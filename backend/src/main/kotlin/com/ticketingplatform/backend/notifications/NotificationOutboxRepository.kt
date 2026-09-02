package com.ticketingplatform.backend.notifications

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
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
        private val objectMapper: ObjectMapper,
    ) {
        fun enqueueMagicLink(recipientEmail: String, magicLinkId: UUID, magicLinkTokenHash: String, expiresAt: Instant): UUID {
            val id = UUID.randomUUID()
            val now = Instant.now()
            val payload = objectMapper.writeValueAsString(
                mapOf(
                    "magicLinkId" to magicLinkId,
                    "expiresAt" to expiresAt,
                ),
            )
            jdbc.update(
                """INSERT INTO notification_outbox (id, notification_type, recipient_email, subject_reference, magic_link_token_hash, payload, created_at, updated_at, next_attempt_at)
                   VALUES (:id, 'CUSTOMER_TICKET_WALLET_MAGIC_LINK', :recipientEmail,
                           :subjectReference, :magicLinkTokenHash, CAST(:payload AS jsonb), :createdAt, :updatedAt, :nextAttemptAt)""",
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("recipientEmail", recipientEmail)
                    .addValue("subjectReference", magicLinkId.toString())
                    .addValue("magicLinkTokenHash", magicLinkTokenHash)
                    .addValue("payload", payload)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now)
                    .addValue("nextAttemptAt", now),
            )
            return id
        }

        fun enqueuePurchaseConfirmation(
            recipientEmail: String,
            orderId: UUID,
            eventId: UUID,
            ticketTypeId: UUID,
            quantity: Int,
            totalAmountMinor: Long,
            currency: String,
        ): UUID {
            val id = UUID.randomUUID()
            val now = Instant.now()
            val payload = objectMapper.writeValueAsString(
                mapOf(
                    "orderId" to orderId,
                    "eventId" to eventId,
                    "ticketTypeId" to ticketTypeId,
                    "quantity" to quantity,
                    "totalAmountMinor" to totalAmountMinor,
                    "currency" to currency,
                ),
            )
            jdbc.update(
                """INSERT INTO notification_outbox (id, notification_type, recipient_email, subject_reference, payload, created_at, updated_at, next_attempt_at)
                   VALUES (:id, 'PURCHASE_CONFIRMATION', :recipientEmail,
                           :subjectReference, CAST(:payload AS jsonb), :createdAt, :updatedAt, :nextAttemptAt)
                   ON CONFLICT DO NOTHING""",
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("recipientEmail", recipientEmail)
                    .addValue("subjectReference", orderId.toString())
                    .addValue("payload", payload)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now)
                    .addValue("nextAttemptAt", now),
            )
            return id
        }

        fun claimPending(now: Instant, limit: Int): List<ClaimedNotification> {
            val claimToken = UUID.randomUUID().toString()
            val claimTokenHash = hash(claimToken)
            return jdbc.query(
                """WITH candidates AS (
                       SELECT id
                       FROM notification_outbox
                       WHERE status = 'PENDING' AND next_attempt_at <= :now
                       ORDER BY next_attempt_at, created_at
                       FOR UPDATE SKIP LOCKED
                       LIMIT :limit
                   )
                   UPDATE notification_outbox notification
                   SET status = 'IN_PROGRESS',
                       attempts = attempts + 1,
                       claimed_at = :now,
                       claim_token_hash = :claimTokenHash,
                       last_attempt_at = :now,
                       updated_at = :now
                   FROM candidates
                   WHERE notification.id = candidates.id
                   RETURNING notification.*""",
                mapOf("now" to now, "limit" to limit, "claimTokenHash" to claimTokenHash),
            ) { rs, _ ->
                ClaimedNotification(
                    record = NotificationOutboxRecord(
                        id = rs.getObject("id", UUID::class.java),
                        type = NotificationType.valueOf(rs.getString("notification_type")),
                        recipientEmail = rs.getString("recipient_email"),
                        subjectReference = rs.getString("subject_reference"),
                        magicLinkTokenHash = rs.getString("magic_link_token_hash"),
                        payload = objectMapper.readTree(rs.getString("payload")),
                        attempts = rs.getInt("attempts"),
                        maxAttempts = rs.getInt("max_attempts"),
                    ),
                    claimToken = claimToken,
                )
            }
        }

        fun markDelivered(id: UUID, claimToken: String, deliveredAt: Instant): Boolean = jdbc.update(
            """UPDATE notification_outbox
               SET status = 'DELIVERED',
                   published_at = :deliveredAt,
                   delivered_at = :deliveredAt,
                   claimed_at = NULL,
                   claim_token_hash = NULL,
                   updated_at = :deliveredAt
               WHERE id = :id AND status = 'IN_PROGRESS' AND claim_token_hash = :claimTokenHash""",
            mapOf("id" to id, "claimTokenHash" to hash(claimToken), "deliveredAt" to deliveredAt),
        ) == 1

        fun markForRetry(id: UUID, claimToken: String, now: Instant, nextAttemptAt: Instant, error: String, terminalFailure: Boolean): Boolean {
            val updateSql = if (terminalFailure) {
                """UPDATE notification_outbox
                   SET status = 'FAILED',
                       failed_at = :now,
                       last_error = :error,
                       claimed_at = NULL,
                       claim_token_hash = NULL,
                       updated_at = :now
                   WHERE id = :id AND status = 'IN_PROGRESS' AND claim_token_hash = :claimTokenHash"""
            } else {
                """UPDATE notification_outbox
                   SET status = 'PENDING',
                       next_attempt_at = :nextAttemptAt,
                       last_error = :error,
                       claimed_at = NULL,
                       claim_token_hash = NULL,
                       updated_at = :now
                   WHERE id = :id AND status = 'IN_PROGRESS' AND claim_token_hash = :claimTokenHash"""
            }
            return jdbc.update(
                updateSql,
                mapOf("id" to id, "claimTokenHash" to hash(claimToken), "nextAttemptAt" to nextAttemptAt, "error" to error.take(400), "now" to now),
            ) == 1
        }

        private fun hash(value: String): String = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
        )
    }

    data class ClaimedNotification(
        val record: NotificationOutboxRecord,
        val claimToken: String,
    )

    data class NotificationOutboxRecord(
        val id: UUID,
        val type: NotificationType,
        val recipientEmail: String,
        val subjectReference: String?,
        val magicLinkTokenHash: String?,
        val payload: JsonNode,
        val attempts: Int,
        val maxAttempts: Int,
    )

    enum class NotificationType {
        CUSTOMER_TICKET_WALLET_MAGIC_LINK,
        PURCHASE_CONFIRMATION,
    }
}
