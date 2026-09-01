package com.ticketingplatform.backend.payments.stripe

import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.param.checkout.SessionCreateParams
import com.ticketingplatform.backend.payments.CheckoutSession
import com.ticketingplatform.backend.payments.CreateCheckoutRequest
import com.ticketingplatform.backend.payments.PaymentProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StripePaymentProvider(@Value("${ticketing.stripe.secret-key}") private val secretKey: String) : PaymentProvider {
    override fun createCheckout(request: CreateCheckoutRequest): CheckoutSession {
        check(secretKey.isNotBlank()) { "Stripe secret key must be configured" }
        val params = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.PAYMENT).setCustomerEmail(request.customerEmail)
            .setSuccessUrl(request.successUrl).setCancelUrl(request.cancelUrl).setClientReferenceId(request.orderId.toString())
            .putMetadata("order_id", request.orderId.toString()).putMetadata("payment_attempt_id", request.paymentAttemptId.toString())
            .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L).setPriceData(
                SessionCreateParams.LineItem.PriceData.builder().setCurrency(request.currency.lowercase()).setUnitAmount(request.amountMinor)
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName("Event ticket order").build()).build()).build()).build()
        val options = RequestOptions.builder().setIdempotencyKey(request.idempotencyKey).setStripeAccount(request.providerAccountReference).build()
        val session = StripeClient(secretKey).v1().checkout().sessions().create(params, options)
        return CheckoutSession(session.id, requireNotNull(session.url) { "Stripe Checkout did not return a URL" })
    }
}
