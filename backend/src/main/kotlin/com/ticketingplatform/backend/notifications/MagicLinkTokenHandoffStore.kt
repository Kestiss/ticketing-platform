package com.ticketingplatform.backend.notifications

import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

interface MagicLinkTokenHandoffStore {
    fun put(notificationId: UUID, rawToken: String, expiresAt: Instant)
    fun take(notificationId: UUID): String?
}

@Component
class InMemoryMagicLinkTokenHandoffStore(
    private val clock: Clock,
) : MagicLinkTokenHandoffStore {
    private val entries = ConcurrentHashMap<UUID, StoredToken>()

    override fun put(notificationId: UUID, rawToken: String, expiresAt: Instant) {
        entries[notificationId] = StoredToken(rawToken = rawToken, expiresAt = expiresAt)
    }

    override fun take(notificationId: UUID): String? {
        val now = Instant.now(clock)
        val entry = entries.remove(notificationId) ?: return null
        if (entry.expiresAt.isBefore(now)) return null
        return entry.rawToken
    }
}

private data class StoredToken(
    val rawToken: String,
    val expiresAt: Instant,
)
