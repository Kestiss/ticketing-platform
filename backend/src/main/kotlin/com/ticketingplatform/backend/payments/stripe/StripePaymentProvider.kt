package com.ticketingplatform.backend.payments.stripe

import com.ticketingplatform.backend.payments.CheckoutSession
import com.ticketingplatform.backend.payments.CreateCheckoutRequest
import com.ticketingplatform.backend.payments.PaymentProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("stripe")
class StripePaymentProvider(
    @Value("${ticketing.stripe.secret-key}") private val secretKey: String,
) : PaymentProvider {
    override fun createCheckout(request: CreateCheckoutRequest): CheckoutSession {
        check(secretKey.isNotBlank()) { "Stripe secret key must be configured" }
        // Stripe SDK integration is added in the next commit. This guard ensures no silent mock payment path exists.
        throw UnsupportedOperationException("Stripe Checkout SDK integration is not configured yet")
    }
}
