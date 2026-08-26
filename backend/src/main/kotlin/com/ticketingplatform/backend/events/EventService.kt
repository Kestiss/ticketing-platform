package com.ticketingplatform.backend.events

import com.ticketingplatform.backend.payments.PaymentProfileRepository
import com.ticketingplatform.backend.payments.PaymentProfileStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val ticketTypeRepository: TicketTypeRepository,
    private val paymentProfileRepository: PaymentProfileRepository,
    private val clock: Clock,
) {
    @Transactional
    fun create(command: CreateEventCommand): Event {
        val name = command.name.trim()
        require(name.isNotBlank()) { "name must not be blank" }
        require(command.endsAt.isAfter(command.startsAt)) { "endsAt must be after startsAt" }
        require(runCatching { ZoneId.of(command.timeZone) }.isSuccess) { "timeZone must be a valid IANA timezone" }
        command.paymentProfileId?.let { profileId ->
            paymentProfileRepository.findByIdForOrganization(profileId, command.organizationId)
                ?: throw EventPaymentProfileOwnershipException()
        }
        val now = Instant.now(clock)
        return eventRepository.insert(Event(UUID.randomUUID(), command.organizationId, command.paymentProfileId, name,
            command.timeZone, command.startsAt, command.endsAt, EventStatus.DRAFT, null, now, now))
    }

    @Transactional
    fun addTicketType(command: CreateTicketTypeCommand): TicketType {
        eventRepository.findByIdForOrganization(command.eventId, command.organizationId)
            ?: throw EventNotFoundException(command.eventId)
        val name = command.name.trim()
        val currency = command.currency.trim().uppercase(Locale.ROOT)
        require(name.isNotBlank()) { "name must not be blank" }
        require(CURRENCY_PATTERN.matches(currency)) { "currency must be a three-letter ISO code" }
        require(command.unitPriceMinor >= 0) { "unitPriceMinor must not be negative" }
        require(command.capacity > 0) { "capacity must be positive" }
        val now = Instant.now(clock)
        return ticketTypeRepository.insert(TicketType(UUID.randomUUID(), command.eventId, name, currency,
            command.unitPriceMinor, command.capacity, TicketTypeStatus.ACTIVE, now, now))
    }

    @Transactional
    fun openSales(eventId: UUID, organizationId: UUID, paymentProfileId: UUID): Event {
        val event = eventRepository.findByIdForOrganization(eventId, organizationId) ?: throw EventNotFoundException(eventId)
        require(event.status == EventStatus.DRAFT || event.status == EventStatus.SCHEDULED) { "event is not eligible to open sales" }
        require(event.paymentProfileLockedAt == null) { "event payment profile is already locked" }
        val profile = paymentProfileRepository.findByIdForOrganization(paymentProfileId, organizationId)
            ?: throw EventPaymentProfileOwnershipException()
        require(profile.status == PaymentProfileStatus.ACTIVE) { "payment profile must be active" }
        require(ticketTypeRepository.hasActiveForEvent(eventId)) { "event requires at least one active ticket type" }
        val now = Instant.now(clock)
        check(eventRepository.updateForOpeningSales(eventId, paymentProfileId, now)) { "event sales opening conflicted; retry with current state" }
        // Future audit/outbox event: EventSalesOpened(eventId, organizationId, paymentProfileId, now).
        return eventRepository.findByIdForOrganization(eventId, organizationId)!!
    }

    @Transactional(readOnly = true)
    fun get(eventId: UUID, organizationId: UUID): Event =
        eventRepository.findByIdForOrganization(eventId, organizationId) ?: throw EventNotFoundException(eventId)

    companion object { private val CURRENCY_PATTERN = Regex("^[A-Z]{3}$") }
}

data class CreateEventCommand(val organizationId: UUID, val paymentProfileId: UUID?, val name: String, val timeZone: String, val startsAt: Instant, val endsAt: Instant)
data class CreateTicketTypeCommand(val organizationId: UUID, val eventId: UUID, val name: String, val currency: String, val unitPriceMinor: Long, val capacity: Int)
class EventNotFoundException(id: UUID) : RuntimeException("Event $id was not found")
class EventPaymentProfileOwnershipException : RuntimeException("Payment profile must belong to the event organization")
