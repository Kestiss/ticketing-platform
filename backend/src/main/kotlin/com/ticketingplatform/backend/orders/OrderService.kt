package com.ticketingplatform.backend.orders

import com.ticketingplatform.backend.inventory.InventoryReservationRepository
import com.ticketingplatform.backend.inventory.InventoryReservationStatus
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val reservationRepository: InventoryReservationRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock,
) {
    @Transactional
    fun createFromReservation(command: CreateOrderCommand): CustomerOrder {
        orderRepository.lockReservation(command.reservationId)
        orderRepository.findByReservationId(command.reservationId)?.let { return it }
        val reservation = reservationRepository.findByIdForOrganization(command.reservationId, command.organizationId)
            ?: throw OrderReservationNotFoundException(command.reservationId)
        require(reservation.eventId == command.eventId) { "reservation does not belong to the requested event" }
        require(reservation.status == InventoryReservationStatus.ACTIVE) { "reservation must be active" }
        require(reservation.expiresAt.isAfter(Instant.now(clock))) { "reservation has expired" }
        val eventProfile = jdbc.query("SELECT payment_profile_id FROM event WHERE id = :eventId AND organization_id = :organizationId AND status = 'ON_SALE'",
            mapOf("eventId" to command.eventId, "organizationId" to command.organizationId)) { rs, _ -> rs.getObject("payment_profile_id", UUID::class.java) }.singleOrNull()
            ?: throw OrderReservationNotFoundException(command.reservationId)
        val ticketType = jdbc.query("SELECT name, currency, unit_price_minor FROM ticket_type WHERE id = :ticketTypeId AND event_id = :eventId",
            mapOf("ticketTypeId" to reservation.ticketTypeId, "eventId" to command.eventId)) { rs, _ -> TicketTypePrice(rs.getString("name"), rs.getString("currency"), rs.getLong("unit_price_minor")) }.singleOrNull()
            ?: throw OrderReservationNotFoundException(command.reservationId)
        val email = command.customerEmail.trim().lowercase(Locale.ROOT)
        require(EMAIL_PATTERN.matches(email)) { "customerEmail must be valid" }
        val total = Math.multiplyExact(ticketType.unitPriceMinor, reservation.requestedQuantity.toLong())
        val now = Instant.now(clock)
        val order = CustomerOrder(UUID.randomUUID(), command.organizationId, command.eventId, reservation.id, eventProfile, email, ticketType.currency, total, OrderStatus.PENDING_PAYMENT, now, now)
        return orderRepository.insert(order, OrderItem(UUID.randomUUID(), order.id, reservation.ticketTypeId, ticketType.name, reservation.requestedQuantity, ticketType.unitPriceMinor, total, now))
    }

    @Transactional(readOnly = true)
    fun get(orderId: UUID, organizationId: UUID): CustomerOrder = orderRepository.findByIdForOrganization(orderId, organizationId) ?: throw OrderNotFoundException(orderId)
    companion object { private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") }
}

data class CreateOrderCommand(val organizationId: UUID, val eventId: UUID, val reservationId: UUID, val customerEmail: String)
class OrderReservationNotFoundException(id: UUID) : RuntimeException("Active reservation $id was not found")
class OrderNotFoundException(id: UUID) : RuntimeException("Order $id was not found")
