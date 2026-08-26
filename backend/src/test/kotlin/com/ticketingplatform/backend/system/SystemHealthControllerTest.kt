package com.ticketingplatform.backend.system

import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SystemHealthControllerTest {
    private val controller = SystemHealthController()

    @Test
    fun `reports service health`() {
        val response = controller.health()

        assertEquals("UP", response.status)
        assertNotNull(response.timestamp)
        assertEquals(true, response.timestamp.isBefore(Instant.now().plusSeconds(1)))
    }
}
