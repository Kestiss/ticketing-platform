package com.ticketingplatform.backend.orders

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OrderRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun findByReservationId(reservationId: UUID): CustomerOrder? = jdbc.query(
        "SELECT * FROM customer_order WHERE reservation_id = :reservationId",
        mapOf("reservationId" to reservationId),
    ) { rs, _ -> rs.toOrder() }.singleOrNull()

    fun findByIdForOrganization(orderId: UUID, organizationId: UUID): CustomerOrder? = jdbc.query(
        "SELECT * FROM customer_order WHERE id = :id AND organization_id = :organizationId",
        mapOf("id" to orderId, "organizationId" to organizationId),
    ) { rs, _ -> rs.toOrder() }.singleOrNull()

    fun insert(order: CustomerOrder, item: OrderItem): CustomerOrder {
        jdbc.update(
            """INSERT INTO customer_order (id, organization_id, event_id, reservation_id, customer_email, currency,
                total_amount_minor, status, created_at, updated_at)
                VALUES (:id, :organizationId, :eventId, :reservationId, :customerEmail, :currency,
                :totalAmountMinor, :status, :createdAt, :updatedAt)""",
            order.parameters(),
        )
        jdbc.update(
            """INSERT INTO order_item (id, order_id, ticket_type_id, ticket_type_name, quantity,
                unit_price_minor, line_total_minor, created_at)
                VALUES (:id, :orderId, :ticketTypeId, :ticketTypeName, :quantity,
                :unitPriceMinor, :lineTotalMinor, :createdAt)""",
            item.parameters(),
        )
        return order
    }

    private fun CustomerOrder.parameters() = MapSqlParameterSource()
        .addValue("id", id).addValue("organizationId", organizationId).addValue("eventId", eventId)
        .addValue("reservationId", reservationId).addValue("customerEmail", customerEmail)
        .addValue("currency", currency).addValue("totalAmountMinor", totalAmountMinor)
        .addValue("status", status.name).addValue("createdAt", createdAt).addValue("updatedAt", updatedAt)

    private fun OrderItem.parameters() = MapSqlParameterSource()
        .addValue("id", id).addValue("orderId", orderId).addValue("ticketTypeId", ticketTypeId)
        .addValue("ticketTypeName", ticketTypeName).addValue("quantity", quantity)
        .addValue("unitPriceMinor", unitPriceMinor).addValue("lineTotalMinor", lineTotalMinor)
        .addValue("createdAt", createdAt)

    private fun ResultSet.toOrder() = CustomerOrder(
        id = getObject("id", UUID::class.java), organizationId = getObject("organization_id", UUID::class.java),
        eventId = getObject("event_id", UUID::class.java), reservationId = getObject("reservation_id", UUID::class.java),
        customerEmail = getString("customer_email"), currency = getString("currency"),
        totalAmountMinor = getLong("total_amount_minor"), status = OrderStatus.valueOf(getString("status")),
        createdAt = getObject("created_at", Instant::class.java), updatedAt = getObject("updated_at", Instant::class.java),
    )
}

@Repository
class PaymentAttemptRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun findByOrderAndIdempotencyKey(orderId: UUID, idempotencyKey: String): PaymentAttempt? = jdbc.query(
        "SELECT * FROM payment_attempt WHERE order_id = :orderId AND idempotency_key = :idempotencyKey",
        mapOf("orderId" to orderId, "idempotencyKey" to idempotencyKey),
    ) { rs, _ -> rs.toAttempt() }.singleOrNull()

    fun insert(attempt: PaymentAttempt): PaymentAttempt {
        jdbc.update(
            """INSERT INTO payment_attempt (id, order_id, provider_type, provider_payment_reference,
                provider_checkout_reference, status, idempotency_key, created_at, updated_at)
                VALUES (:id, :orderId, :providerType, :providerPaymentReference,
                :providerCheckoutReference, :status, :idempotencyKey, :createdAt, :updatedAt)""",
            MapSqlParameterSource().addValue("id", attempt.id).addValue("orderId", attempt.orderId)
                .addValue("providerType", attempt.providerType).addValue("providerPaymentReference", attempt.providerPaymentReference)
                .addValue("providerCheckoutReference", attempt.providerCheckoutReference).addValue("status", attempt.status.name)
                .addValue("idempotencyKey", attempt.idempotencyKey).addValue("createdAt", attempt.createdAt).addValue("updatedAt", attempt.updatedAt),
        )
        return attempt
    }

    fun markCheckoutStarted(id: UUID, checkoutReference: String, now: Instant): PaymentAttempt {
        val attempts = jdbc.query(
            """UPDATE payment_attempt SET provider_checkout_reference = :checkoutReference,
                status = 'CHECKOUT_STARTED', updated_at = :now WHERE id = :id RETURNING *""",
            mapOf("id" to id, "checkoutReference" to checkoutReference, "now" to now),
        ) { rs, _ -> rs.toAttempt() }
        return attempts.single()
    }

    private fun ResultSet.toAttempt() = PaymentAttempt(
        id = getObject("id", UUID::class.java), orderId = getObject("order_id", UUID::class.java),
        providerType = getString("provider_type"), providerPaymentReference = getString("provider_payment_reference"),
        providerCheckoutReference = getString("provider_checkout_reference"), status = PaymentAttemptStatus.valueOf(getString("status")),
        idempotencyKey = getString("idempotency_key"), createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )
}
