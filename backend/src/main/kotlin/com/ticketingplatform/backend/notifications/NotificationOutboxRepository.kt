package com.ticketingplatform.backend.notifications

import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Durable intent to notify. Delivery providers are deliberately separate from the ticketing transaction.
 * Payloads must never contain raw passwords, card data, or unencrypted long-lived credentials.
 */
@Repository
class NotificationOutboxRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun enqueueMagicLink(recipientEmail: String, expiresAt: Instant) {
        jdbc.update(
            """INSERT INTO notification_outbox (id, notification_type, recipient_email, payload, created_at)
               VALUES (:id, 'CUSTOMER_TICKET_WALLET_MAGIC_LINK', :recipientEmail,
                       CAST(:payload AS jsonb), :createdAt)""",
            MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("recipientEmail", recipientEmail)
                .addValue("payload", "{\"expiresAt\":\"$expiresAt\"}")
                .addValue("createdAt", Instant.now()),
        )
    }
}
