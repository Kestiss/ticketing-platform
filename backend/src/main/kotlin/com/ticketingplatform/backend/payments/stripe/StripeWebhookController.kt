package com.ticketingplatform.backend.payments.stripe

import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.ticketingplatform.backend.orders.PaymentFinalizationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("stripe")
@RequestMapping("/api/v1/payments/stripe")
class StripeWebhookController(
    @Value("${ticketing.stripe.webhook-secret}") private val webhookSecret: String,
    private val finalization: PaymentFinalizationService,
) {
    @PostMapping("/webhook")
    fun receive(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
        @RequestHeader("Stripe-Account", required = false) stripeAccount: String?,
    ): ResponseEntity<Void> {
        check(webhookSecret.isNotBlank()) { "Stripe webhook secret must be configured" }
        val event = Webhook.constructEvent(payload, signature, webhookSecret)
        if (event.type != "checkout.session.completed") return ResponseEntity.ok().build()
        val session = event.dataObjectDeserializer.`object`.orElseThrow {
            IllegalArgumentException("Stripe event did not contain a Checkout Session")
        } as Session
        if (session.paymentStatus != "paid") return ResponseEntity.ok().build()
        finalization.processStripeCheckoutCompleted(
            providerEventId = event.id,
            stripeAccountReference = stripeAccount,
            checkoutReference = session.id,
            paymentReference = session.paymentIntent as? String,
            paidAmountMinor = session.amountTotal ?: 0L,
            currency = session.currency ?: "",
        )
        return ResponseEntity.ok().build()
    }
}
