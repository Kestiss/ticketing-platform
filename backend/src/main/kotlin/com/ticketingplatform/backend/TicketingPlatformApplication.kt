package com.ticketingplatform.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TicketingPlatformApplication

fun main(args: Array<String>) {
    runApplication<TicketingPlatformApplication>(*args)
}
