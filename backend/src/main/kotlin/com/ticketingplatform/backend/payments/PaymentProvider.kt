package com.ticketingplatform.backend.payments

import java.util.UUID

interface PaymentProvider {
    fun createCheckout(request: CreateCheckoutRequest): CheckoutSession
}

data class CreateCheckoutRequest(
    val paymentAttemptId: UUID,
    val orderId: UUID,
    val providerAccountReference: String,
    val amountMinor: Long,
    val currency: String,
    val customerEmail: String,
    val successUrl: String,
    val cancelUrl: String,
    val idempotencyKey: String,
)

data class CheckoutSession(
    val providerCheckoutReference: String,
    val redirectUrl: String,
)
