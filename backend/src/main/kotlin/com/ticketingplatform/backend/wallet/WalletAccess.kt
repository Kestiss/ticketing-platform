package com.ticketingplatform.backend.wallet

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

enum class MagicLinkPurpose { TICKET_WALLET }

data class CustomerMagicLink(
    val id: UUID,
    val email: String,
    val tokenHash: String,
    val purpose: MagicLinkPurpose,
    val requestedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
)

data class CustomerWalletSession(
    val id: UUID,
    val email: String,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

object SecureToken {
    private val random = SecureRandom()

    fun generate(): String = ByteArray(32).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    fun hash(value: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
    )
}
