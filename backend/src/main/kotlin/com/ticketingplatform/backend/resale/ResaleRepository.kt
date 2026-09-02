package com.ticketingplatform.backend.resale

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ResaleRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun lockEntitlement(entitlementId: UUID) = lock("resale-entitlement:$entitlementId")
    fun lockListing(listingId: UUID) = lock("resale-listing:$listingId")

    private fun lock(scope: String) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))", mapOf("scope" to scope))
    }

    fun findPolicy(eventId: UUID): EventResalePolicy? = jdbc.query(
        "SELECT * FROM event_resale_policy WHERE event_id = :eventId",
        mapOf("eventId" to eventId),
    ) { rs, _ -> rs.toPolicy() }.singleOrNull()

    fun upsertPolicy(policy: EventResalePolicy): EventResalePolicy {
        jdbc.update(
            """INSERT INTO event_resale_policy (event_id, organization_id, enabled, minimum_price_minor, maximum_price_minor,
                listing_cutoff_minutes, resale_fee_minor, resale_fee_basis_points, checked_in_ineligible, refunded_ineligible,
                revoked_ineligible, created_at, updated_at)
                VALUES (:eventId, :organizationId, :enabled, :minimumPriceMinor, :maximumPriceMinor, :listingCutoffMinutes,
                :resaleFeeMinor, :resaleFeeBasisPoints, :checkedInIneligible, :refundedIneligible, :revokedIneligible, :createdAt, :updatedAt)
                ON CONFLICT (event_id) DO UPDATE SET organization_id = EXCLUDED.organization_id, enabled = EXCLUDED.enabled,
                minimum_price_minor = EXCLUDED.minimum_price_minor, maximum_price_minor = EXCLUDED.maximum_price_minor,
                listing_cutoff_minutes = EXCLUDED.listing_cutoff_minutes, resale_fee_minor = EXCLUDED.resale_fee_minor,
                resale_fee_basis_points = EXCLUDED.resale_fee_basis_points, checked_in_ineligible = EXCLUDED.checked_in_ineligible,
                refunded_ineligible = EXCLUDED.refunded_ineligible, revoked_ineligible = EXCLUDED.revoked_ineligible,
                updated_at = EXCLUDED.updated_at""",
            MapSqlParameterSource()
                .addValue("eventId", policy.eventId)
                .addValue("organizationId", policy.organizationId)
                .addValue("enabled", policy.enabled)
                .addValue("minimumPriceMinor", policy.minimumPriceMinor)
                .addValue("maximumPriceMinor", policy.maximumPriceMinor)
                .addValue("listingCutoffMinutes", policy.listingCutoffMinutes)
                .addValue("resaleFeeMinor", policy.resaleFeeMinor)
                .addValue("resaleFeeBasisPoints", policy.resaleFeeBasisPoints)
                .addValue("checkedInIneligible", policy.checkedInIneligible)
                .addValue("refundedIneligible", policy.refundedIneligible)
                .addValue("revokedIneligible", policy.revokedIneligible)
                .addValue("createdAt", policy.createdAt)
                .addValue("updatedAt", policy.updatedAt),
        )
        return findPolicy(policy.eventId)!!
    }

    fun findEntitlement(entitlementId: UUID): EntitlementSnapshot? = jdbc.query(
        """SELECT entitlement.id, entitlement.organization_id, entitlement.event_id, entitlement.order_id, entitlement.ticket_type_id,
               entitlement.owner_email, entitlement.status, entitlement.transferred_to_entitlement_id, event.starts_at
           FROM ticket_entitlement entitlement
           JOIN event ON event.id = entitlement.event_id
           WHERE entitlement.id = :id""",
        mapOf("id" to entitlementId),
    ) { rs, _ -> rs.toEntitlement() }.singleOrNull()

    fun findListingBySourceEntitlement(sourceEntitlementId: UUID): ResaleListing? = jdbc.query(
        "SELECT * FROM resale_listing WHERE source_entitlement_id = :sourceEntitlementId AND status IN ('LISTED', 'PURCHASE_PENDING')",
        mapOf("sourceEntitlementId" to sourceEntitlementId),
    ) { rs, _ -> rs.toListing() }.singleOrNull()

    fun insertListing(listing: ResaleListing): ResaleListing {
        jdbc.update(
            """INSERT INTO resale_listing (id, organization_id, event_id, source_entitlement_id, source_credential_id, seller_email,
               buyer_email, currency, listed_price_minor, status, payment_pending_reference, cancelled_at, sold_at, created_at, updated_at)
               VALUES (:id, :organizationId, :eventId, :sourceEntitlementId, :sourceCredentialId, :sellerEmail, :buyerEmail,
               :currency, :listedPriceMinor, :status, :paymentPendingReference, :cancelledAt, :soldAt, :createdAt, :updatedAt)""",
            listing.parameters(),
        )
        return listing
    }

    fun findListing(listingId: UUID): ResaleListing? = jdbc.query(
        "SELECT * FROM resale_listing WHERE id = :id",
        mapOf("id" to listingId),
    ) { rs, _ -> rs.toListing() }.singleOrNull()

    fun markListingCancelled(listingId: UUID, now: Instant): ResaleListing? = jdbc.query(
        """UPDATE resale_listing SET status = 'CANCELLED', cancelled_at = :now, updated_at = :now
            WHERE id = :id AND status = 'LISTED' RETURNING *""",
        mapOf("id" to listingId, "now" to now),
    ) { rs, _ -> rs.toListing() }.singleOrNull()

    fun markListingPurchasePending(listingId: UUID, buyerEmail: String, reference: String, now: Instant): ResaleListing? = jdbc.query(
        """UPDATE resale_listing SET status = 'PURCHASE_PENDING', buyer_email = :buyerEmail,
            payment_pending_reference = :reference, updated_at = :now
            WHERE id = :id AND status = 'LISTED' RETURNING *""",
        mapOf("id" to listingId, "buyerEmail" to buyerEmail, "reference" to reference, "now" to now),
    ) { rs, _ -> rs.toListing() }.singleOrNull()

    fun resetListingToListed(listingId: UUID, now: Instant): ResaleListing? = jdbc.query(
        """UPDATE resale_listing SET status = 'LISTED', buyer_email = NULL, payment_pending_reference = NULL, updated_at = :now
            WHERE id = :id AND status = 'PURCHASE_PENDING' RETURNING *""",
        mapOf("id" to listingId, "now" to now),
    ) { rs, _ -> rs.toListing() }.singleOrNull()

    fun markListingSold(listingId: UUID, buyerEmail: String, now: Instant): ResaleListing? = jdbc.query(
        """UPDATE resale_listing SET status = 'SOLD', buyer_email = :buyerEmail, sold_at = :now, updated_at = :now
            WHERE id = :id AND status = 'PURCHASE_PENDING' RETURNING *""",
        mapOf("id" to listingId, "buyerEmail" to buyerEmail, "now" to now),
    ) { rs, _ -> rs.toListing() }.singleOrNull()

    fun activeCredentialCount(entitlementId: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM ticket_credential WHERE ticket_entitlement_id = :entitlementId AND status = 'ACTIVE'",
        mapOf("entitlementId" to entitlementId),
        Int::class.java,
    ) ?: 0

    fun findActiveCredential(entitlementId: UUID): TicketCredentialSnapshot? = jdbc.query(
        "SELECT * FROM ticket_credential WHERE ticket_entitlement_id = :entitlementId AND status = 'ACTIVE'",
        mapOf("entitlementId" to entitlementId),
    ) { rs, _ -> rs.toCredential() }.singleOrNull()

    fun revokeCredential(credentialId: UUID, now: Instant): Boolean = jdbc.update(
        "UPDATE ticket_credential SET status = 'REVOKED', revoked_at = :now WHERE id = :id AND status = 'ACTIVE'",
        mapOf("id" to credentialId, "now" to now),
    ) == 1

    fun createTransferredEntitlement(source: EntitlementSnapshot, ownerEmail: String, now: Instant): UUID {
        val newId = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO ticket_entitlement (id, organization_id, event_id, order_id, ticket_type_id, owner_email, status, created_at)
                VALUES (:id, :organizationId, :eventId, :orderId, :ticketTypeId, :ownerEmail, 'ACTIVE', :createdAt)""",
            mapOf(
                "id" to newId,
                "organizationId" to source.organizationId,
                "eventId" to source.eventId,
                "orderId" to source.orderId,
                "ticketTypeId" to source.ticketTypeId,
                "ownerEmail" to ownerEmail,
                "createdAt" to now,
            ),
        )
        return newId
    }

    fun createCredential(entitlementId: UUID, tokenHash: String, now: Instant) {
        jdbc.update(
            """INSERT INTO ticket_credential (id, ticket_entitlement_id, version, credential_token_hash, status, created_at)
                VALUES (:id, :entitlementId, 1, :tokenHash, 'ACTIVE', :createdAt)""",
            mapOf("id" to UUID.randomUUID(), "entitlementId" to entitlementId, "tokenHash" to tokenHash, "createdAt" to now),
        )
    }

    fun markEntitlementTransferred(sourceEntitlementId: UUID, targetEntitlementId: UUID, now: Instant): Boolean = jdbc.update(
        """UPDATE ticket_entitlement SET status = 'REVOKED', transferred_to_entitlement_id = :target, transferred_at = :now
            WHERE id = :sourceId AND status = 'ACTIVE'""",
        mapOf("sourceId" to sourceEntitlementId, "target" to targetEntitlementId, "now" to now),
    ) == 1

    fun findTransactionByListingAndKey(listingId: UUID, idempotencyKey: String): ResaleTransaction? = jdbc.query(
        "SELECT * FROM resale_transaction WHERE listing_id = :listingId AND idempotency_key = :idempotencyKey",
        mapOf("listingId" to listingId, "idempotencyKey" to idempotencyKey),
    ) { rs, _ -> rs.toTransaction() }.singleOrNull()

    fun insertTransaction(transaction: ResaleTransaction): ResaleTransaction {
        jdbc.update(
            """INSERT INTO resale_transaction (id, listing_id, provider_type, provider_payment_reference, idempotency_key,
                status, failure_reason, created_at, updated_at)
                VALUES (:id, :listingId, :providerType, :providerPaymentReference, :idempotencyKey, :status, :failureReason, :createdAt, :updatedAt)""",
            transaction.parameters(),
        )
        return transaction
    }

    fun markTransactionFailed(id: UUID, reason: String?, now: Instant): ResaleTransaction? = jdbc.query(
        """UPDATE resale_transaction SET status = 'PAYMENT_FAILED', failure_reason = :reason, updated_at = :now
            WHERE id = :id AND status = 'PAYMENT_PENDING' RETURNING *""",
        mapOf("id" to id, "reason" to reason, "now" to now),
    ) { rs, _ -> rs.toTransaction() }.singleOrNull()

    fun markTransactionConfirmed(id: UUID, providerPaymentReference: String, now: Instant): ResaleTransaction? = jdbc.query(
        """UPDATE resale_transaction SET status = 'PAYMENT_CONFIRMED', provider_payment_reference = :providerPaymentReference,
            updated_at = :now WHERE id = :id AND status <> 'PAYMENT_CONFIRMED' RETURNING *""",
        mapOf("id" to id, "providerPaymentReference" to providerPaymentReference, "now" to now),
    ) { rs, _ -> rs.toTransaction() }.singleOrNull()

    fun insertPendingPayout(
        listingId: UUID,
        transactionId: UUID,
        sellerEmail: String,
        currency: String,
        grossAmountMinor: Long,
        feeAmountMinor: Long,
        netAmountMinor: Long,
        now: Instant,
    ): UUID? {
        val payoutId = UUID.randomUUID()
        val created = jdbc.update(
            """INSERT INTO seller_payout (id, listing_id, resale_transaction_id, seller_email, currency, gross_amount_minor,
                fee_amount_minor, net_amount_minor, state, created_at, updated_at)
                VALUES (:id, :listingId, :transactionId, :sellerEmail, :currency, :grossAmountMinor, :feeAmountMinor,
                :netAmountMinor, 'PENDING', :now, :now)
                ON CONFLICT (listing_id) DO NOTHING""",
            mapOf(
                "id" to payoutId,
                "listingId" to listingId,
                "transactionId" to transactionId,
                "sellerEmail" to sellerEmail,
                "currency" to currency,
                "grossAmountMinor" to grossAmountMinor,
                "feeAmountMinor" to feeAmountMinor,
                "netAmountMinor" to netAmountMinor,
                "now" to now,
            ),
        )
        return if (created == 1) payoutId else null
    }

    fun insertAuditEvent(
        listingId: UUID,
        eventId: UUID,
        eventType: ResaleAuditEventType,
        actorEmail: String?,
        payload: String,
        now: Instant,
    ) {
        jdbc.update(
            """INSERT INTO resale_audit_event (id, listing_id, event_id, event_type, actor_email, payload, created_at)
                VALUES (:id, :listingId, :eventId, :eventType, :actorEmail, CAST(:payload AS jsonb), :createdAt)""",
            mapOf(
                "id" to UUID.randomUUID(),
                "listingId" to listingId,
                "eventId" to eventId,
                "eventType" to eventType.name,
                "actorEmail" to actorEmail,
                "payload" to payload,
                "createdAt" to now,
            ),
        )
    }

    private fun ResaleListing.parameters() = MapSqlParameterSource()
        .addValue("id", id)
        .addValue("organizationId", organizationId)
        .addValue("eventId", eventId)
        .addValue("sourceEntitlementId", sourceEntitlementId)
        .addValue("sourceCredentialId", sourceCredentialId)
        .addValue("sellerEmail", sellerEmail)
        .addValue("buyerEmail", buyerEmail)
        .addValue("currency", currency)
        .addValue("listedPriceMinor", listedPriceMinor)
        .addValue("status", status.name)
        .addValue("paymentPendingReference", paymentPendingReference)
        .addValue("cancelledAt", cancelledAt)
        .addValue("soldAt", soldAt)
        .addValue("createdAt", createdAt)
        .addValue("updatedAt", updatedAt)

    private fun ResaleTransaction.parameters() = MapSqlParameterSource()
        .addValue("id", id)
        .addValue("listingId", listingId)
        .addValue("providerType", providerType)
        .addValue("providerPaymentReference", providerPaymentReference)
        .addValue("idempotencyKey", idempotencyKey)
        .addValue("status", status.name)
        .addValue("failureReason", failureReason)
        .addValue("createdAt", createdAt)
        .addValue("updatedAt", updatedAt)

    private fun ResultSet.toPolicy() = EventResalePolicy(
        eventId = getObject("event_id", UUID::class.java),
        organizationId = getObject("organization_id", UUID::class.java),
        enabled = getBoolean("enabled"),
        minimumPriceMinor = getObject("minimum_price_minor") as Long?,
        maximumPriceMinor = getObject("maximum_price_minor") as Long?,
        listingCutoffMinutes = getInt("listing_cutoff_minutes"),
        resaleFeeMinor = getObject("resale_fee_minor") as Long?,
        resaleFeeBasisPoints = getObject("resale_fee_basis_points") as Int?,
        checkedInIneligible = getBoolean("checked_in_ineligible"),
        refundedIneligible = getBoolean("refunded_ineligible"),
        revokedIneligible = getBoolean("revoked_ineligible"),
        createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )

    private fun ResultSet.toEntitlement() = EntitlementSnapshot(
        id = getObject("id", UUID::class.java),
        organizationId = getObject("organization_id", UUID::class.java),
        eventId = getObject("event_id", UUID::class.java),
        orderId = getObject("order_id", UUID::class.java),
        ticketTypeId = getObject("ticket_type_id", UUID::class.java),
        ownerEmail = getString("owner_email"),
        status = getString("status"),
        transferredToEntitlementId = getObject("transferred_to_entitlement_id", UUID::class.java),
        eventStartsAt = getObject("starts_at", Instant::class.java),
    )

    private fun ResultSet.toListing() = ResaleListing(
        id = getObject("id", UUID::class.java),
        organizationId = getObject("organization_id", UUID::class.java),
        eventId = getObject("event_id", UUID::class.java),
        sourceEntitlementId = getObject("source_entitlement_id", UUID::class.java),
        sourceCredentialId = getObject("source_credential_id", UUID::class.java),
        sellerEmail = getString("seller_email"),
        buyerEmail = getString("buyer_email"),
        currency = getString("currency"),
        listedPriceMinor = getLong("listed_price_minor"),
        status = ResaleListingStatus.valueOf(getString("status")),
        paymentPendingReference = getString("payment_pending_reference"),
        cancelledAt = getObject("cancelled_at", Instant::class.java),
        soldAt = getObject("sold_at", Instant::class.java),
        createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )

    private fun ResultSet.toCredential() = TicketCredentialSnapshot(
        id = getObject("id", UUID::class.java),
        entitlementId = getObject("ticket_entitlement_id", UUID::class.java),
        version = getInt("version"),
        status = getString("status"),
    )

    private fun ResultSet.toTransaction() = ResaleTransaction(
        id = getObject("id", UUID::class.java),
        listingId = getObject("listing_id", UUID::class.java),
        providerType = getString("provider_type"),
        providerPaymentReference = getString("provider_payment_reference"),
        idempotencyKey = getString("idempotency_key"),
        status = ResaleTransactionStatus.valueOf(getString("status")),
        failureReason = getString("failure_reason"),
        createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )
}
