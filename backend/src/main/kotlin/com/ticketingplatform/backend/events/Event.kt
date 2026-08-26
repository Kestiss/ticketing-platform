package com.ticketingplatform.backend.events

import java.time.Instant
import java.util.UUID

enum class EventStatus { DRAFT, SCHEDULED, ON_SALE, SALES_CLOSED, CANCELLED }
enum class TicketTypeStatus { ACTIVE, INACTIVE }

data class Event(
    val id: UUID,
    val organizationId: UUID,
    val paymentProfileId: UUID?,
    val name: String,
    val timeZone: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: EventStatus,
    val paymentProfileLockedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TicketType(
    val id: UUID,
    val eventId: UUID,
    val name: String,
    val currency: String,
    val unitPriceMinor: Long,
    val capacity: Int,
    val status: TicketTypeStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
