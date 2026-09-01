package com.ticketingplatform.backend.orders

import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OrderRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun lockReservation(reservationId: UUID) = lock("order:$reservationId")
    fun lockOrder(orderId: UUID) = lock("paid-order:$orderId")
    private fun lock(scope: String) { jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))", mapOf("scope" to scope)) }
    fun findByReservationId(reservationId: UUID): CustomerOrder? = jdbc.query("SELECT * FROM customer_order WHERE reservation_id = :reservationId", mapOf("reservationId" to reservationId)) { rs, _ -> rs.toOrder() }.singleOrNull()
    fun findByIdForOrganization(orderId: UUID, organizationId: UUID): CustomerOrder? = jdbc.query("SELECT * FROM customer_order WHERE id = :id AND organization_id = :organizationId", mapOf("id" to orderId, "organizationId" to organizationId)) { rs, _ -> rs.toOrder() }.singleOrNull()
    fun findById(orderId: UUID): CustomerOrder? = jdbc.query("SELECT * FROM customer_order WHERE id = :id", mapOf("id" to orderId)) { rs, _ -> rs.toOrder() }.singleOrNull()
    fun findSingleItem(orderId: UUID): OrderItem = jdbc.query("SELECT * FROM order_item WHERE order_id = :orderId", mapOf("orderId" to orderId)) { rs, _ -> rs.toItem() }.single()
    fun insert(order: CustomerOrder, item: OrderItem): CustomerOrder { jdbc.update("""INSERT INTO customer_order (id, organization_id, event_id, reservation_id, payment_profile_id, customer_email, currency, total_amount_minor, status, created_at, updated_at) VALUES (:id, :organizationId, :eventId, :reservationId, :paymentProfileId, :customerEmail, :currency, :totalAmountMinor, :status, :createdAt, :updatedAt)""", order.parameters()); jdbc.update("""INSERT INTO order_item (id, order_id, ticket_type_id, ticket_type_name, quantity, unit_price_minor, line_total_minor, created_at) VALUES (:id, :orderId, :ticketTypeId, :ticketTypeName, :quantity, :unitPriceMinor, :lineTotalMinor, :createdAt)""", item.parameters()); return order }
    fun markPaid(orderId: UUID, now: Instant): Boolean = jdbc.update("UPDATE customer_order SET status = 'PAID', updated_at = :now WHERE id = :id AND status = 'PENDING_PAYMENT'", mapOf("id" to orderId, "now" to now)) == 1
    private fun CustomerOrder.parameters() = MapSqlParameterSource().addValue("id", id).addValue("organizationId", organizationId).addValue("eventId", eventId).addValue("reservationId", reservationId).addValue("paymentProfileId", paymentProfileId).addValue("customerEmail", customerEmail).addValue("currency", currency).addValue("totalAmountMinor", totalAmountMinor).addValue("status", status.name).addValue("createdAt", createdAt).addValue("updatedAt", updatedAt)
    private fun OrderItem.parameters() = MapSqlParameterSource().addValue("id", id).addValue("orderId", orderId).addValue("ticketTypeId", ticketTypeId).addValue("ticketTypeName", ticketTypeName).addValue("quantity", quantity).addValue("unitPriceMinor", unitPriceMinor).addValue("lineTotalMinor", lineTotalMinor).addValue("createdAt", createdAt)
    private fun java.sql.ResultSet.toOrder() = CustomerOrder(getObject("id", UUID::class.java), getObject("organization_id", UUID::class.java), getObject("event_id", UUID::class.java), getObject("reservation_id", UUID::class.java), getObject("payment_profile_id", UUID::class.java), getString("customer_email"), getString("currency"), getLong("total_amount_minor"), OrderStatus.valueOf(getString("status")), getObject("created_at", Instant::class.java), getObject("updated_at", Instant::class.java))
    private fun java.sql.ResultSet.toItem() = OrderItem(getObject("id", UUID::class.java), getObject("order_id", UUID::class.java), getObject("ticket_type_id", UUID::class.java), getString("ticket_type_name"), getInt("quantity"), getLong("unit_price_minor"), getLong("line_total_minor"), getObject("created_at", Instant::class.java))
}

@Repository
class PaymentAttemptRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun findByOrderAndIdempotencyKey(orderId: UUID, idempotencyKey: String): PaymentAttempt? = jdbc.query("SELECT * FROM payment_attempt WHERE order_id = :orderId AND idempotency_key = :idempotencyKey", mapOf("orderId" to orderId, "idempotencyKey" to idempotencyKey)) { rs, _ -> rs.toAttempt() }.singleOrNull()
    fun findByCheckoutReference(reference: String): PaymentAttempt? = jdbc.query("SELECT * FROM payment_attempt WHERE provider_type = 'STRIPE' AND provider_checkout_reference = :reference", mapOf("reference" to reference)) { rs, _ -> rs.toAttempt() }.singleOrNull()
    fun insert(attempt: PaymentAttempt): PaymentAttempt { jdbc.update("""INSERT INTO payment_attempt (id, order_id, provider_type, provider_payment_reference, provider_checkout_reference, status, idempotency_key, created_at, updated_at) VALUES (:id, :orderId, :providerType, :providerPaymentReference, :providerCheckoutReference, :status, :idempotencyKey, :createdAt, :updatedAt)""", MapSqlParameterSource().addValue("id", attempt.id).addValue("orderId", attempt.orderId).addValue("providerType", attempt.providerType).addValue("providerPaymentReference", attempt.providerPaymentReference).addValue("providerCheckoutReference", attempt.providerCheckoutReference).addValue("status", attempt.status.name).addValue("idempotencyKey", attempt.idempotencyKey).addValue("createdAt", attempt.createdAt).addValue("updatedAt", attempt.updatedAt)); return attempt }
    fun markCheckoutStarted(id: UUID, checkoutReference: String, now: Instant): PaymentAttempt = jdbc.query("UPDATE payment_attempt SET provider_checkout_reference = :reference, status = 'CHECKOUT_STARTED', updated_at = :now WHERE id = :id RETURNING *", mapOf("id" to id, "reference" to checkoutReference, "now" to now)) { rs, _ -> rs.toAttempt() }.single()
    fun markSucceeded(id: UUID, paymentReference: String?, now: Instant): Boolean = jdbc.update("UPDATE payment_attempt SET provider_payment_reference = :paymentReference, status = 'SUCCEEDED', updated_at = :now WHERE id = :id AND status <> 'SUCCEEDED'", mapOf("id" to id, "paymentReference" to paymentReference, "now" to now)) == 1
    private fun java.sql.ResultSet.toAttempt() = PaymentAttempt(getObject("id", UUID::class.java), getObject("order_id", UUID::class.java), getString("provider_type"), getString("provider_payment_reference"), getString("provider_checkout_reference"), PaymentAttemptStatus.valueOf(getString("status")), getString("idempotency_key"), getObject("created_at", Instant::class.java), getObject("updated_at", Instant::class.java))
}
