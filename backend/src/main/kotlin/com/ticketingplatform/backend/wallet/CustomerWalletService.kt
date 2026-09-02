package com.ticketingplatform.backend.wallet

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerWalletService(
    private val repository: WalletAccessRepository,
    private val clock: Clock,
    @Value("${ticketing.wallet.presentation-signing-key}") private val presentationSigningKey: String,
) {
    @Transactional
    fun requestMagicLink(email: String): MagicLinkRequestResult {
        val normalizedEmail = normalizeEmail(email)
        val rawToken = SecureToken.generate()
        val now = Instant.now(clock)
        repository.insertMagicLink(
            CustomerMagicLink(UUID.randomUUID(), normalizedEmail, SecureToken.hash(rawToken), MagicLinkPurpose.TICKET_WALLET,
                now, now.plus(MAGIC_LINK_TTL), null),
        )
        // A transactional outbox notification will deliver this URL in the production notification module.
        return MagicLinkRequestResult(rawToken, now.plus(MAGIC_LINK_TTL))
    }

    @Transactional
    fun redeemMagicLink(rawToken: String): WalletSessionResult {
        val now = Instant.now(clock)
        val link = repository.consumeMagicLink(SecureToken.hash(rawToken), now)
            ?: throw InvalidMagicLinkException()
        val rawSessionToken = SecureToken.generate()
        val session = CustomerWalletSession(UUID.randomUUID(), link.email, SecureToken.hash(rawSessionToken), now, now.plus(SESSION_TTL), null)
        repository.insertSession(session)
        return WalletSessionResult(rawSessionToken, session.expiresAt)
    }

    @Transactional(readOnly = true)
    fun listTickets(rawSessionToken: String): List<WalletTicketView> {
        val session = repository.findActiveSession(SecureToken.hash(rawSessionToken), Instant.now(clock))
            ?: throw InvalidWalletSessionException()
        return repository.findTicketsByEmail(session.email).map { ticket ->
            WalletTicketView(
                ticket.entitlementId, ticket.eventId, ticket.eventName, ticket.startsAt, ticket.endsAt, ticket.timeZone,
                ticket.entitlementStatus, ticket.credentialVersion, presentationClaim(ticket),
            )
        }
    }

    private fun presentationClaim(ticket: WalletTicket): String {
        check(presentationSigningKey.isNotBlank()) { "Wallet presentation signing key must be configured" }
        val payload = "${ticket.credentialId}.${ticket.credentialVersion}.${ticket.eventId}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(presentationSigningKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
        return "$payload.$signature"
    }

    private fun normalizeEmail(email: String): String {
        val value = email.trim().lowercase()
        require(EMAIL_PATTERN.matches(value)) { "email must be valid" }
        return value
    }

    companion object {
        private val MAGIC_LINK_TTL: Duration = Duration.ofMinutes(15)
        private val SESSION_TTL: Duration = Duration.ofHours(24)
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

data class MagicLinkRequestResult(val rawToken: String, val expiresAt: Instant)
data class WalletSessionResult(val rawSessionToken: String, val expiresAt: Instant)
data class WalletTicketView(
    val entitlementId: UUID,
    val eventId: UUID,
    val eventName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val timeZone: String,
    val status: String,
    val credentialVersion: Int,
    val presentationClaim: String,
)
class InvalidMagicLinkException : RuntimeException("Magic link is invalid, expired, or already used")
class InvalidWalletSessionException : RuntimeException("Wallet session is invalid or expired")
