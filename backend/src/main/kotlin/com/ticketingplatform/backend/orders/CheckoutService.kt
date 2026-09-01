package com.ticketingplatform.backend.orders

import com.ticketingplatform.backend.payments.CreateCheckoutRequest
import com.ticketingplatform.backend.payments.PaymentProfileRepository
import com.ticketingplatform.backend.payments.PaymentProfileStatus
import com.ticketingplatform.backend.payments.PaymentProvider
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CheckoutService(
    private val orders: OrderRepository,
    private val attempts: PaymentAttemptRepository,
    private val profiles: PaymentProfileRepository,
    private val paymentProvider: PaymentProvider,
    private val clock: Clock,
) {
    @Transactional
    fun start(command: StartCheckoutCommand): CheckoutResult {
        val order = orders.findByIdForOrganization(command.orderId, command.organizationId) ?: throw OrderNotFoundException(command.orderId)
        require(order.status == OrderStatus.PENDING_PAYMENT) { "order is not payable" }
        val key = command.idempotencyKey.trim()
        require(key.isNotBlank()) { "idempotencyKey must not be blank" }
        attempts.findByOrderAndIdempotencyKey(order.id, key)?.let { existing ->
            require(existing.providerCheckoutReference != null && existing.checkoutRedirectUrl != null) { "existing checkout initiation did not complete" }
            return CheckoutResult(existing.id, existing.providerCheckoutReference, existing.checkoutRedirectUrl)
        }
        val profile = profiles.findById(order.paymentProfileId) ?: throw IllegalStateException("order payment profile was not found")
        require(profile.organizationId == order.organizationId && profile.status == PaymentProfileStatus.ACTIVE) { "order payment profile is not active" }
        val now = Instant.now(clock)
        val attempt = attempts.insert(PaymentAttempt(UUID.randomUUID(), order.id, "STRIPE", null, null, null, PaymentAttemptStatus.CREATED, key, now, now))
        val checkout = paymentProvider.createCheckout(CreateCheckoutRequest(attempt.id, order.id, profile.providerAccountReference, order.totalAmountMinor, order.currency, order.customerEmail, command.successUrl, command.cancelUrl, "checkout:${attempt.id}"))
        val updated = attempts.markCheckoutStarted(attempt.id, checkout.providerCheckoutReference, checkout.redirectUrl, Instant.now(clock))
        return CheckoutResult(updated.id, checkout.providerCheckoutReference, updated.checkoutRedirectUrl)
    }
}

data class StartCheckoutCommand(val organizationId: UUID, val orderId: UUID, val successUrl: String, val cancelUrl: String, val idempotencyKey: String)
data class CheckoutResult(val paymentAttemptId: UUID, val checkoutReference: String, val redirectUrl: String)
