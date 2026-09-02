package com.ticketingplatform.backend.admissions

import com.ticketingplatform.backend.wallet.SecureToken
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ScannerDeviceRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun create(device: ScannerDevice): ScannerDevice {
        jdbc.update(
            """INSERT INTO scanner_device (id, organization_id, display_name, device_secret_hash, status, created_at)
               VALUES (:id, :organizationId, :displayName, :deviceSecretHash, :status, :createdAt)""",
            MapSqlParameterSource()
                .addValue("id", device.id).addValue("organizationId", device.organizationId)
                .addValue("displayName", device.displayName).addValue("deviceSecretHash", device.deviceSecretHash)
                .addValue("status", device.status.name).addValue("createdAt", device.createdAt),
        )
        return device
    }

    fun assignToEvent(deviceId: UUID, eventId: UUID, assignedAt: Instant) {
        jdbc.update(
            """INSERT INTO scanner_assignment (scanner_device_id, event_id, assigned_at)
               VALUES (:deviceId, :eventId, :assignedAt)
               ON CONFLICT (scanner_device_id, event_id) DO NOTHING""",
            mapOf("deviceId" to deviceId, "eventId" to eventId, "assignedAt" to assignedAt),
        )
    }

    fun findActiveAssignedDevice(deviceId: UUID, rawSecret: String, eventId: UUID): ScannerDevice? = jdbc.query(
        """SELECT device.* FROM scanner_device device
               JOIN scanner_assignment assignment ON assignment.scanner_device_id = device.id
               WHERE device.id = :deviceId AND device.device_secret_hash = :secretHash
                 AND device.status = 'ACTIVE' AND assignment.event_id = :eventId""",
        mapOf("deviceId" to deviceId, "secretHash" to SecureToken.hash(rawSecret), "eventId" to eventId),
    ) { rs, _ -> rs.toDevice() }.singleOrNull()

    fun touch(deviceId: UUID, now: Instant) {
        jdbc.update("UPDATE scanner_device SET last_seen_at = :now WHERE id = :id", mapOf("id" to deviceId, "now" to now))
    }

    private fun ResultSet.toDevice() = ScannerDevice(
        id = getObject("id", UUID::class.java), organizationId = getObject("organization_id", UUID::class.java),
        displayName = getString("display_name"), deviceSecretHash = getString("device_secret_hash"),
        status = ScannerDeviceStatus.valueOf(getString("status")), createdAt = getObject("created_at", Instant::class.java),
        lastSeenAt = getObject("last_seen_at", Instant::class.java),
    )
}

@Repository
class AdmissionRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun admitOnce(entitlementId: UUID, organizationId: UUID, eventId: UUID, scannerDeviceId: UUID, credentialVersion: Int, now: Instant): Boolean = jdbc.update(
        """INSERT INTO admission_record (id, organization_id, event_id, ticket_entitlement_id, scanner_device_id,
                 credential_version, outcome, reason_code, scanned_at)
            VALUES (:id, :organizationId, :eventId, :entitlementId, :scannerDeviceId,
                 :credentialVersion, 'ADMITTED', 'VALID', :scannedAt)
            ON CONFLICT DO NOTHING""",
        mapOf("id" to UUID.randomUUID(), "organizationId" to organizationId, "eventId" to eventId,
            "entitlementId" to entitlementId, "scannerDeviceId" to scannerDeviceId,
            "credentialVersion" to credentialVersion, "scannedAt" to now),
    ) == 1

    fun recordRejection(organizationId: UUID, eventId: UUID, scannerDeviceId: UUID, entitlementId: UUID?, credentialVersion: Int?, reasonCode: String, now: Instant) {
        jdbc.update(
            """INSERT INTO admission_record (id, organization_id, event_id, ticket_entitlement_id, scanner_device_id,
                 credential_version, outcome, reason_code, scanned_at)
            VALUES (:id, :organizationId, :eventId, :entitlementId, :scannerDeviceId,
                 :credentialVersion, 'REJECTED', :reasonCode, :scannedAt)""",
            mapOf("id" to UUID.randomUUID(), "organizationId" to organizationId, "eventId" to eventId,
                "entitlementId" to entitlementId, "scannerDeviceId" to scannerDeviceId,
                "credentialVersion" to credentialVersion, "reasonCode" to reasonCode, "scannedAt" to now),
        )
    }
}
