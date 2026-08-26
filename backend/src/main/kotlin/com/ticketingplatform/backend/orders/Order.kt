package com.ticketingplatform.backend.orders

import java.time.Instant
import java.util.UUID

enum class OrderStatus { PENDING_PAYMENT, PAID, PAYMENT_FAILED, CANCELLED, EXPIRED }
enum class PaymentAttemptStatus { CREATED, CHECKOUT_STARTED, SUCCEEDED, FAILED }

data class CustomerOrder(
    val id: UUID,
    val organizationId: UUID,
    val eventId: UUID,
    val reservationId: UUID,
    val customerEmail: String,
    val currency: String,
    val totalAmountMinor: Long,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class OrderItem(
    val id: UUID,
    val orderId: UUID,
    val ticketTypeId: UUID,
    val ticketTypeName: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val createdAt: Instant,
)

data class PaymentAttempt(
    val id: UUID,
    val orderId: UUID,
    val providerType: String,
    val providerPaymentReference: String?,
    val providerCheckoutReference: String?,
    val status: PaymentAttemptStatus,
    val idempotencyKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
