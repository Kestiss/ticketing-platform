package com.ticketingplatform.backend.wallet

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class WalletAccessRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun insertMagicLink(link: CustomerMagicLink) {
        jdbc.update(
            """INSERT INTO customer_magic_link (id, email, token_hash, purpose, requested_at, expires_at)
               VALUES (:id, :email, :tokenHash, :purpose, :requestedAt, :expiresAt)""",
            MapSqlParameterSource()
                .addValue("id", link.id).addValue("email", link.email).addValue("tokenHash", link.tokenHash)
                .addValue("purpose", link.purpose.name).addValue("requestedAt", link.requestedAt).addValue("expiresAt", link.expiresAt)),
    )

    fun consumeMagicLink(tokenHash: String, now: Instant): CustomerMagicLink? = jdbc.query(
        """UPDATE customer_magic_link SET consumed_at = :now
           WHERE token_hash = :tokenHash AND consumed_at IS NULL AND expires_at > :now
           RETURNING *""",
        mapOf("tokenHash" to tokenHash, "now" to now),
    ) { rs, _ -> rs.toMagicLink() }.singleOrNull()

    fun insertSession(session: CustomerWalletSession) {
        jdbc.update(
            """INSERT INTO customer_wallet_session (id, email, session_token_hash, created_at, expires_at)
               VALUES (:id, :email, :tokenHash, :createdAt, :expiresAt)""",
            MapSqlParameterSource()
                .addValue("id", session.id).addValue("email", session.email).addValue("tokenHash", session.tokenHash)
                .addValue("createdAt", session.createdAt).addValue("expiresAt", session.expiresAt)),
    )

    fun findActiveSession(tokenHash: String, now: Instant): CustomerWalletSession? = jdbc.query(
        """SELECT * FROM customer_wallet_session
           WHERE session_token_hash = :tokenHash AND revoked_at IS NULL AND expires_at > :now""",
        mapOf("tokenHash" to tokenHash, "now" to now),
    ) { rs, _ -> rs.toSession() }.singleOrNull()

    fun findTicketsByEmail(email: String): List<WalletTicket> = jdbc.query(
        """SELECT entitlement.id AS entitlement_id, entitlement.event_id, event.name AS event_name,
                  event.starts_at, event.ends_at, event.time_zone, entitlement.status AS entitlement_status,
                  credential.id AS credential_id, credential.version, credential.status AS credential_status
           FROM ticket_entitlement entitlement
           JOIN event ON event.id = entitlement.event_id
           JOIN ticket_credential credential ON credential.ticket_entitlement_id = entitlement.id
           WHERE lower(entitlement.owner_email) = lower(:email) AND credential.status = 'ACTIVE'
           ORDER BY event.starts_at, entitlement.created_at""",
        mapOf("email" to email),
    ) { rs, _ -> rs.toWalletTicket() }

    private fun ResultSet.toMagicLink() = CustomerMagicLink(
        id = getObject("id", UUID::class.java), email = getString("email"), tokenHash = getString("token_hash"),
        purpose = MagicLinkPurpose.valueOf(getString("purpose")), requestedAt = getObject("requested_at", Instant::class.java),
        expiresAt = getObject("expires_at", Instant::class.java), consumedAt = getObject("consumed_at", Instant::class.java))

    private fun ResultSet.toSession() = CustomerWalletSession(
        id = getObject("id", UUID::class.java), email = getString("email"), tokenHash = getString("session_token_hash"),
        createdAt = getObject("created_at", Instant::class.java), expiresAt = getObject("expires_at", Instant::class.java),
        revokedAt = getObject("revoked_at", Instant::class.java))

    private fun ResultSet.toWalletTicket() = WalletTicket(
        entitlementId = getObject("entitlement_id", UUID::class.java), eventId = getObject("event_id", UUID::class.java),
        eventName = getString("event_name"), startsAt = getObject("starts_at", Instant::class.java),
        endsAt = getObject("ends_at", Instant::class.java), timeZone = getString("time_zone"),
        entitlementStatus = getString("entitlement_status"), credentialId = getObject("credential_id", UUID::class.java),
        credentialVersion = getInt("version"), credentialStatus = getString("credential_status"))
}

data class WalletTicket(
    val entitlementId: UUID,
    val eventId: UUID,
    val eventName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val timeZone: String,
    val entitlementStatus: String,
    val credentialId: UUID,
    val credentialVersion: Int,
    val credentialStatus: String,
)
