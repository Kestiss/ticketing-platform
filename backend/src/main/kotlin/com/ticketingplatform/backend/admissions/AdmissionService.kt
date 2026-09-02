package com.ticketingplatform.backend.admissions

import com.ticketingplatform.backend.wallet.SecureToken
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdmissionService(
    private val scanners: ScannerDeviceRepository,
    private val admissions: AdmissionRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock,
    @Value("${ticketing.wallet.presentation-signing-key}") private val presentationSigningKey: String,
) {
    @Transactional
    fun createScanner(command: CreateScannerDeviceCommand): ScannerBootstrapResult {
        val displayName = command.displayName.trim()
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        requireEventBelongsToOrganization(command.eventId, command.organizationId)
        val rawSecret = SecureToken.generate()
        val now = Instant.now(clock)
        val device = ScannerDevice(UUID.randomUUID(), command.organizationId, displayName, SecureToken.hash(rawSecret), ScannerDeviceStatus.ACTIVE, now, null)
        scanners.create(device)
        scanners.assignToEvent(device.id, command.eventId, now)
        return ScannerBootstrapResult(device.id, rawSecret)
    }

    @Transactional
    fun validate(command: ValidateAdmissionCommand): AdmissionDecision {
        val now = Instant.now(clock)
        requireEventBelongsToOrganization(command.eventId, command.organizationId)
        val scanner = scanners.findActiveAssignedDevice(command.scannerDeviceId, command.scannerSecret, command.eventId)
            ?.takeIf { it.organizationId == command.organizationId }
            ?: throw InvalidScannerDeviceException()
        scanners.touch(scanner.id, now)
        val parsed = parseAndVerifyClaim(command.presentationClaim)
        if (parsed == null || parsed.eventId != command.eventId) {
            admissions.recordRejection(scanner.organizationId, command.eventId, scanner.id, null, null, "INVALID_CREDENTIAL", now)
            return AdmissionDecision(AdmissionOutcome.REJECTED, "INVALID_CREDENTIAL", null, now)
        }
        val entitlement = jdbc.query(
            """SELECT entitlement.id, entitlement.status AS entitlement_status, credential.status AS credential_status, credential.version
               FROM ticket_entitlement entitlement JOIN ticket_credential credential ON credential.ticket_entitlement_id = entitlement.id
               WHERE credential.id = :credentialId AND credential.version = :version AND entitlement.event_id = :eventId""",
            mapOf("credentialId" to parsed.credentialId, "version" to parsed.version, "eventId" to command.eventId),
        ) { rs, _ -> CredentialState(rs.getObject("id", UUID::class.java), rs.getString("entitlement_status"), rs.getString("credential_status"), rs.getInt("version")) }.singleOrNull()
        if (entitlement == null || entitlement.entitlementStatus != "ACTIVE" || entitlement.credentialStatus != "ACTIVE") {
            admissions.recordRejection(scanner.organizationId, command.eventId, scanner.id, entitlement?.entitlementId, parsed.version, "REVOKED_OR_INVALID", now)
            return AdmissionDecision(AdmissionOutcome.REJECTED, "REVOKED_OR_INVALID", entitlement?.entitlementId, now)
        }
        val admitted = admissions.admitOnce(entitlement.entitlementId, scanner.organizationId, command.eventId, scanner.id, entitlement.version, now)
        if (!admitted) {
            admissions.recordRejection(scanner.organizationId, command.eventId, scanner.id, entitlement.entitlementId, entitlement.version, "ALREADY_ADMITTED", now)
            return AdmissionDecision(AdmissionOutcome.REJECTED, "ALREADY_ADMITTED", entitlement.entitlementId, now)
        }
        return AdmissionDecision(AdmissionOutcome.ADMITTED, "VALID", entitlement.entitlementId, now)
    }

    private fun requireEventBelongsToOrganization(eventId: UUID, organizationId: UUID) {
        val exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM event WHERE id = :eventId AND organization_id = :organizationId)",
            mapOf("eventId" to eventId, "organizationId" to organizationId), Boolean::class.java) ?: false
        require(exists) { "event does not belong to organization" }
    }

    private fun parseAndVerifyClaim(value: String): ParsedClaim? {
        val parts = value.split('.')
        if (parts.size != 4 || presentationSigningKey.isBlank()) return null
        val payload = parts.take(3).joinToString(".")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(presentationSigningKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
        if (!java.security.MessageDigest.isEqual(expected.toByteArray(StandardCharsets.UTF_8), parts[3].toByteArray(StandardCharsets.UTF_8))) return null
        return runCatching { ParsedClaim(UUID.fromString(parts[0]), parts[1].toInt(), UUID.fromString(parts[2])) }.getOrNull()
    }

    private data class ParsedClaim(val credentialId: UUID, val version: Int, val eventId: UUID)
    private data class CredentialState(val entitlementId: UUID, val entitlementStatus: String, val credentialStatus: String, val version: Int)
}

data class CreateScannerDeviceCommand(val organizationId: UUID, val eventId: UUID, val displayName: String)
data class ScannerBootstrapResult(val scannerDeviceId: UUID, val rawScannerSecret: String)
data class ValidateAdmissionCommand(val organizationId: UUID, val scannerDeviceId: UUID, val scannerSecret: String, val eventId: UUID, val presentationClaim: String)
class InvalidScannerDeviceException : RuntimeException("Scanner device is invalid, revoked, or not assigned to this event")
