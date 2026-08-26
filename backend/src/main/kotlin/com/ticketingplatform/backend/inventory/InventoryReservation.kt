package com.ticketingplatform.backend.inventory

import java.time.Instant
import java.util.UUID

enum class InventoryReservationStatus { ACTIVE, EXPIRED, CONVERTED, CANCELLED }

data class InventoryReservation(
    val id: UUID,
    val organizationId: UUID,
    val eventId: UUID,
    val ticketTypeId: UUID,
    val requestedQuantity: Int,
    val status: InventoryReservationStatus,
    val idempotencyKey: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
