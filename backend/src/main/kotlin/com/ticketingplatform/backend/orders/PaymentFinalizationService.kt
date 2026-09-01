package com.ticketingplatform.backend.orders

import com.ticketingplatform.backend.inventory.InventoryReservationRepository
import com.ticketingplatform.backend.inventory.InventoryReservationStatus
import com.ticketingplatform.backend.tickets.TicketIssuanceService
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentFinalizationService(
    private val orders: OrderRepository,
    private val attempts: PaymentAttemptRepository,
    private val reservations: InventoryReservationRepository,
    private val tickets: TicketIssuanceService,
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock,
) {
    @Transactional
    fun finalizeStripeCheckout(checkoutReference: String, paymentReference: String?, paidAmountMinor: Long, currency: String) {
        val attempt = attempts.findByCheckoutReference(checkoutReference) ?: throw UnknownPaymentAttemptException(checkoutReference)
        val order = orders.findById(attempt.orderId) ?: throw OrderNotFoundException(attempt.orderId)
        if (order.status == OrderStatus.PAID) return
        require(attempt.status == PaymentAttemptStatus.CHECKOUT_STARTED || attempt.status == PaymentAttemptStatus.SUCCEEDED) { "payment attempt is not awaiting confirmation" }
        require(order.totalAmountMinor == paidAmountMinor) { "paid amount does not match order" }
        require(order.currency.equals(currency, ignoreCase = true)) { "paid currency does not match order" }
        val reservation = reservations.findById(order.reservationId) ?: throw OrderReservationNotFoundException(order.reservationId)
        require(reservation.status == InventoryReservationStatus.ACTIVE) { "reservation is not active" }
        val now = Instant.now(clock)
        val converted = reservations.convert(reservation.id, now) ?: throw IllegalStateException("reservation conversion conflicted")
        check(jdbc.update("""UPDATE ticket_inventory SET reserved_quantity = reserved_quantity - :quantity, sold_quantity = sold_quantity + :quantity, updated_at = :now
            WHERE ticket_type_id = :ticketTypeId AND reserved_quantity >= :quantity""", mapOf("quantity" to converted.requestedQuantity, "ticketTypeId" to converted.ticketTypeId, "now" to now)) == 1) { "inventory conversion invariant violated" }
        attempts.markSucceeded(attempt.id, paymentReference, now)
        check(orders.markPaid(order.id, now)) { "order payment finalization conflicted" }
        tickets.issue(order.id, order.organizationId, order.eventId, converted.ticketTypeId, order.customerEmail, converted.requestedQuantity)
    }
}

class UnknownPaymentAttemptException(reference: String) : RuntimeException("Checkout session $reference was not found")
