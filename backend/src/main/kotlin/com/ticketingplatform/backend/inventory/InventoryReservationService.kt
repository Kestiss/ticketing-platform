package com.ticketingplatform.backend.inventory

import com.ticketingplatform.backend.events.EventStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryReservationService(
    private val reservationRepository: InventoryReservationRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock,
) {
    @Transactional
    fun reserve(command: CreateReservationCommand): InventoryReservation {
        require(command.quantity in 1..10) { "quantity must be between 1 and 10" }
        val key = command.idempotencyKey.trim()
        require(key.isNotBlank()) { "idempotencyKey must not be blank" }

        reservationRepository.findByIdempotencyKey(command.organizationId, command.eventId, command.ticketTypeId, key)?.let { return it }
        requireTicketTypeIsAvailable(command)
        val reserved = jdbc.update(
            """UPDATE ticket_inventory inventory SET reserved_quantity = reserved_quantity + :quantity, updated_at = :now
               FROM ticket_type type
               WHERE inventory.ticket_type_id = type.id AND type.id = :ticketTypeId AND type.event_id = :eventId
                 AND type.status = 'ACTIVE' AND inventory.sold_quantity + inventory.reserved_quantity + :quantity <= type.capacity""",
            mapOf("quantity" to command.quantity, "now" to Instant.now(clock), "ticketTypeId" to command.ticketTypeId, "eventId" to command.eventId),
        )
        if (reserved != 1) throw InventoryUnavailableException()

        val now = Instant.now(clock)
        return reservationRepository.insert(
            InventoryReservation(UUID.randomUUID(), command.organizationId, command.eventId, command.ticketTypeId,
                command.quantity, InventoryReservationStatus.ACTIVE, key, now.plus(RESERVATION_TTL), now, now),
        )
    }

    @Transactional
    fun cancel(reservationId: UUID, organizationId: UUID): InventoryReservation {
        val reservation = reservationRepository.findByIdForOrganization(reservationId, organizationId)
            ?: throw ReservationNotFoundException(reservationId)
        val now = Instant.now(clock)
        val cancelled = reservationRepository.cancel(reservationId, now) ?: return reservation
        release(cancelled)
        return cancelled
    }

    @Transactional
    fun expire(reservationId: UUID, now: Instant = Instant.now(clock)): InventoryReservation? {
        val reservation = reservationRepository.findByIdForOrganization(reservationId, organizationId = UUID(0, 0))
        return reservation
    }

    @Transactional(readOnly = true)
    fun get(reservationId: UUID, organizationId: UUID): InventoryReservation =
        reservationRepository.findByIdForOrganization(reservationId, organizationId) ?: throw ReservationNotFoundException(reservationId)

    private fun requireTicketTypeIsAvailable(command: CreateReservationCommand) {
        val eventStatus = jdbc.queryForObject(
            "SELECT status FROM event WHERE id = :eventId AND organization_id = :organizationId",
            mapOf("eventId" to command.eventId, "organizationId" to command.organizationId), String::class.java,
        ) ?: throw ReservationNotFoundException(command.eventId)
        require(eventStatus == EventStatus.ON_SALE.name) { "event is not on sale" }
    }

    private fun release(reservation: InventoryReservation) {
        jdbc.update(
            """UPDATE ticket_inventory SET reserved_quantity = reserved_quantity - :quantity, updated_at = :now
               WHERE ticket_type_id = :ticketTypeId AND reserved_quantity >= :quantity""",
            mapOf("quantity" to reservation.requestedQuantity, "ticketTypeId" to reservation.ticketTypeId, "now" to Instant.now(clock)),
        )
        // Future outbox event: InventoryReservationReleased(reservation.id, reservation.ticketTypeId).
    }

    companion object { private val RESERVATION_TTL: Duration = Duration.ofMinutes(15) }
}

data class CreateReservationCommand(val organizationId: UUID, val eventId: UUID, val ticketTypeId: UUID, val quantity: Int, val idempotencyKey: String)
class InventoryUnavailableException : RuntimeException("Requested ticket quantity is not available")
class ReservationNotFoundException(id: UUID) : RuntimeException("Reservation $id was not found")
