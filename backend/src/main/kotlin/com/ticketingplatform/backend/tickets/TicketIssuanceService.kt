package com.ticketingplatform.backend.tickets

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class TicketIssuanceService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock,
) {
    fun issue(orderId: UUID, organizationId: UUID, eventId: UUID, ticketTypeId: UUID, ownerEmail: String, quantity: Int) {
        val existing = jdbc.queryForObject("SELECT count(*) FROM ticket_entitlement WHERE order_id = :orderId", mapOf("orderId" to orderId), Int::class.java) ?: 0
        if (existing > 0) return
        val now = Instant.now(clock)
        repeat(quantity) {
            val entitlementId = UUID.randomUUID()
            val rawCredential = ByteArray(32).also(SecureRandom()::nextBytes)
            val tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawCredential))
            jdbc.update("""INSERT INTO ticket_entitlement (id, organization_id, event_id, order_id, ticket_type_id, owner_email, status, created_at)
                VALUES (:id, :organizationId, :eventId, :orderId, :ticketTypeId, :ownerEmail, 'ACTIVE', :createdAt)""",
                mapOf("id" to entitlementId, "organizationId" to organizationId, "eventId" to eventId, "orderId" to orderId, "ticketTypeId" to ticketTypeId, "ownerEmail" to ownerEmail, "createdAt" to now))
            jdbc.update("""INSERT INTO ticket_credential (id, ticket_entitlement_id, version, credential_token_hash, status, created_at)
                VALUES (:id, :entitlementId, 1, :tokenHash, 'ACTIVE', :createdAt)""",
                MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("entitlementId", entitlementId).addValue("tokenHash", tokenHash).addValue("createdAt", now))
        }
    }
}
