package com.ticketingplatform.backend.payments.stripe

import com.stripe.model.Event
import com.stripe.net.Webhook
import com.ticketingplatform.backend.orders.PaymentFinalizationService
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
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
    fun receive(@RequestBody payload: String, @RequestHeader("Stripe-Signature") signature: String): ResponseEntity<Void> {
        val event = Webhook.constructEvent(payload, signature, webhookSecret)
        if (event.type != "checkout.session.completed") return ResponseEntity.ok().build()
        val session = event.dataObjectDeserializer.`object`.orElseThrow { IllegalArgumentException("Stripe event did not contain a Checkout Session") } as com.stripe.model.checkout.Session
        if (session.paymentStatus != "paid") return ResponseEntity.ok().build()
        finalization.finalizeStripeCheckout(session.id, session.paymentIntent as? String, session.amountTotal ?: 0L, session.currency ?: "")
        return ResponseEntity.status(HttpStatus.OK).build()
    }
}
