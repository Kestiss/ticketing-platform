package com.ticketingplatform.backend.system

import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SystemHealthResponse(
    val status: String,
    val timestamp: Instant,
)

@RestController
@RequestMapping("/api/v1/system")
class SystemHealthController {
    @GetMapping("/health")
    fun health(): SystemHealthResponse = SystemHealthResponse(
        status = "UP",
        timestamp = Instant.now(),
    )
}
