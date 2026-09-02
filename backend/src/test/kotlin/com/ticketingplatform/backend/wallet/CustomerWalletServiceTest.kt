package com.ticketingplatform.backend.wallet

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

class SecureTokenTest {
    @Test
    fun `tokens are random and hashes are deterministic`() {
        val first = SecureToken.generate()
        val second = SecureToken.generate()

        assertNotEquals(first, second)
        assertEquals(SecureToken.hash(first), SecureToken.hash(first))
        assertNotEquals(SecureToken.hash(first), SecureToken.hash(second))
    }
}
