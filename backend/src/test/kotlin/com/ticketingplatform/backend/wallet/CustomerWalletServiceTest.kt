package com.ticketingplatform.backend.wallet

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class CustomerWalletServiceTest {
    private val now = Instant.parse("2030-01-01T10:00:00Z")
    private val repository = FakeWalletAccessRepository()
    private val service = CustomerWalletService(repository, Clock.fixed(now, ZoneOffset.UTC), "test-signing-key")

    @Test
    fun `magic link is single use and creates a wallet session`() {
        val link = service.requestMagicLink("Customer@Example.test")

        val session = service.redeemMagicLink(link.rawToken)

        assertEquals(1, repository.sessions.size)
        assertEquals("customer@example.test", repository.sessions.single().email)
        assertEquals(24 * 60 * 60, session.expiresAt.epochSecond - now.epochSecond)
        assertFailsWith<InvalidMagicLinkException> { service.redeemMagicLink(link.rawToken) }
    }

    @Test
    fun `ticket presentation claim does not expose credential token hash`() {
        val link = service.requestMagicLink("customer@example.test")
        val session = service.redeemMagicLink(link.rawToken)
        repository.tickets += WalletTicket(
            UUID.randomUUID(), UUID.randomUUID(), "Concert", now, now.plusSeconds(3600), "Europe/Vilnius",
            "ACTIVE", UUID.randomUUID(), 1, "ACTIVE",
        )

        val ticket = service.listTickets(session.rawSessionToken).single()

        assert(ticket.presentationClaim.contains(ticket.credentialVersion.toString()))
        assert(!ticket.presentationClaim.contains("credential_token_hash"))
    }

    private class FakeWalletAccessRepository : WalletAccessRepositoryStub() {
        val links = mutableListOf<CustomerMagicLink>()
        val sessions = mutableListOf<CustomerWalletSession>()
        val tickets = mutableListOf<WalletTicket>()

        override fun insertMagicLink(link: CustomerMagicLink) { links += link }
        override fun consumeMagicLink(tokenHash: String, now: Instant): CustomerMagicLink? {
            val index = links.indexOfFirst { it.tokenHash == tokenHash && it.consumedAt == null && it.expiresAt > now }
            if (index < 0) return null
            val consumed = links[index].copy(consumedAt = now)
            links[index] = consumed
            return consumed
        }
        override fun insertSession(session: CustomerWalletSession) { sessions += session }
        override fun findActiveSession(tokenHash: String, now: Instant): CustomerWalletSession? = sessions.singleOrNull { it.tokenHash == tokenHash && it.expiresAt > now && it.revokedAt == null }
        override fun findTicketsByEmail(email: String): List<WalletTicket> = tickets
    }
}
