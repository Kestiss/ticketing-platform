package com.ticketingplatform.backend.resale

import java.time.Instant
import java.util.UUID

enum class ResaleListingStatus { LISTED, PURCHASE_PENDING, SOLD, CANCELLED }
enum class ResaleTransactionStatus { PAYMENT_PENDING, PAYMENT_FAILED, PAYMENT_CONFIRMED }
enum class SellerPayoutState { PENDING, PROCESSING, PAID, FAILED }
enum class ResaleAuditEventType { LISTED, CANCELLED, PURCHASE_PENDING, PAYMENT_FAILED, SOLD }

data class EventResalePolicy(
    val eventId: UUID,
    val organizationId: UUID,
    val enabled: Boolean,
    val minimumPriceMinor: Long?,
    val maximumPriceMinor: Long?,
    val listingCutoffMinutes: Int,
    val resaleFeeMinor: Long?,
    val resaleFeeBasisPoints: Int?,
    val checkedInIneligible: Boolean,
    val refundedIneligible: Boolean,
    val revokedIneligible: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResaleListing(
    val id: UUID,
    val organizationId: UUID,
    val eventId: UUID,
    val sourceEntitlementId: UUID,
    val sourceCredentialId: UUID?,
    val sellerEmail: String,
    val buyerEmail: String?,
    val currency: String,
    val listedPriceMinor: Long,
    val status: ResaleListingStatus,
    val paymentPendingReference: String?,
    val cancelledAt: Instant?,
    val soldAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResaleTransaction(
    val id: UUID,
    val listingId: UUID,
    val providerType: String,
    val providerPaymentReference: String?,
    val idempotencyKey: String,
    val status: ResaleTransactionStatus,
    val failureReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class EntitlementSnapshot(
    val id: UUID,
    val organizationId: UUID,
    val eventId: UUID,
    val orderId: UUID,
    val ticketTypeId: UUID,
    val ownerEmail: String,
    val status: String,
    val transferredToEntitlementId: UUID?,
    val eventStartsAt: Instant,
)

data class TicketCredentialSnapshot(
    val id: UUID,
    val entitlementId: UUID,
    val version: Int,
    val status: String,
)

data class ConfigureResalePolicyCommand(
    val organizationId: UUID,
    val eventId: UUID,
    val enabled: Boolean,
    val minimumPriceMinor: Long?,
    val maximumPriceMinor: Long?,
    val listingCutoffMinutes: Int,
    val resaleFeeMinor: Long?,
    val resaleFeeBasisPoints: Int?,
    val checkedInIneligible: Boolean,
    val refundedIneligible: Boolean,
    val revokedIneligible: Boolean,
)

data class CreateResaleListingCommand(
    val organizationId: UUID,
    val eventId: UUID,
    val sourceEntitlementId: UUID,
    val sellerEmail: String,
    val priceMinor: Long,
    val currency: String,
)

data class CancelResaleListingCommand(val listingId: UUID, val sellerEmail: String)
data class BeginResalePurchaseCommand(val listingId: UUID, val buyerEmail: String, val idempotencyKey: String)
data class FailResalePurchaseCommand(val listingId: UUID, val buyerEmail: String, val idempotencyKey: String, val reason: String?)
data class CompleteResalePurchaseCommand(val listingId: UUID, val buyerEmail: String, val idempotencyKey: String, val providerPaymentReference: String)

class ResaleListingNotFoundException(id: UUID) : RuntimeException("Resale listing $id was not found")
class ResalePolicyNotFoundException(eventId: UUID) : RuntimeException("Resale policy for event $eventId was not configured")
