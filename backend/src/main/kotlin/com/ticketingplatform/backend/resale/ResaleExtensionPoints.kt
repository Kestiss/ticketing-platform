package com.ticketingplatform.backend.resale

import java.util.UUID
import org.springframework.stereotype.Component

interface ResaleAuthorization {
    fun assertOrganizerCanConfigurePolicy(organizationId: UUID, eventId: UUID)
    fun assertSellerCanManageEntitlement(sellerEmail: String, entitlementId: UUID)
    fun assertBuyerCanPurchase(buyerEmail: String, listingId: UUID)
}

interface ResalePaymentVerification {
    fun assertVerifiedBuyerPayment(listingId: UUID, buyerEmail: String, providerPaymentReference: String)
}

interface SellerPayoutAdapter {
    fun onPendingPayoutCreated(listingId: UUID, payoutId: UUID)
}

@Component
class AllowAllResaleAuthorization : ResaleAuthorization {
    override fun assertOrganizerCanConfigurePolicy(organizationId: UUID, eventId: UUID) {
        // Temporary bootstrap only. Replace with Keycloak-backed organizer authorization.
    }

    override fun assertSellerCanManageEntitlement(sellerEmail: String, entitlementId: UUID) {
        // Temporary bootstrap only. Replace with authenticated customer wallet ownership checks.
    }

    override fun assertBuyerCanPurchase(buyerEmail: String, listingId: UUID) {
        // Temporary bootstrap only. Replace with authenticated customer wallet checks.
    }
}

@Component
class StubResalePaymentVerification : ResalePaymentVerification {
    override fun assertVerifiedBuyerPayment(listingId: UUID, buyerEmail: String, providerPaymentReference: String) {
        require(providerPaymentReference.isNotBlank()) { "providerPaymentReference must not be blank" }
    }
}

@Component
class NoopSellerPayoutAdapter : SellerPayoutAdapter {
    override fun onPendingPayoutCreated(listingId: UUID, payoutId: UUID) {
        // External Stripe Connect seller payout calls are intentionally left behind this adapter boundary.
    }
}
