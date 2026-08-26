package com.ticketingplatform.backend.events

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class EventRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun insert(event: Event): Event {
        jdbc.update(
            """INSERT INTO event (id, organization_id, payment_profile_id, name, time_zone, starts_at, ends_at,
                status, payment_profile_locked_at, created_at, updated_at)
                VALUES (:id, :organizationId, :paymentProfileId, :name, :timeZone, :startsAt, :endsAt,
                :status, :paymentProfileLockedAt, :createdAt, :updatedAt)""",
            event.parameters(),
        )
        return event
    }

    fun findByIdForOrganization(id: UUID, organizationId: UUID): Event? = jdbc.query(
        "SELECT * FROM event WHERE id = :id AND organization_id = :organizationId",
        mapOf("id" to id, "organizationId" to organizationId),
    ) { rs, _ -> rs.toEvent() }.singleOrNull()

    fun findById(id: UUID): Event? = jdbc.query(
        "SELECT * FROM event WHERE id = :id", mapOf("id" to id),
    ) { rs, _ -> rs.toEvent() }.singleOrNull()

    fun updateForOpeningSales(id: UUID, paymentProfileId: UUID, now: Instant): Boolean = jdbc.update(
        """UPDATE event SET status = 'ON_SALE', payment_profile_id = :paymentProfileId,
            payment_profile_locked_at = :now, updated_at = :now
            WHERE id = :id AND status IN ('DRAFT', 'SCHEDULED') AND payment_profile_locked_at IS NULL""",
        mapOf("id" to id, "paymentProfileId" to paymentProfileId, "now" to now),
    ) == 1

    private fun Event.parameters() = MapSqlParameterSource()
        .addValue("id", id).addValue("organizationId", organizationId)
        .addValue("paymentProfileId", paymentProfileId).addValue("name", name)
        .addValue("timeZone", timeZone).addValue("startsAt", startsAt).addValue("endsAt", endsAt)
        .addValue("status", status.name).addValue("paymentProfileLockedAt", paymentProfileLockedAt)
        .addValue("createdAt", createdAt).addValue("updatedAt", updatedAt)

    private fun ResultSet.toEvent() = Event(
        id = getObject("id", UUID::class.java), organizationId = getObject("organization_id", UUID::class.java),
        paymentProfileId = getObject("payment_profile_id", UUID::class.java), name = getString("name"),
        timeZone = getString("time_zone"), startsAt = getObject("starts_at", Instant::class.java),
        endsAt = getObject("ends_at", Instant::class.java), status = EventStatus.valueOf(getString("status")),
        paymentProfileLockedAt = getObject("payment_profile_locked_at", Instant::class.java),
        createdAt = getObject("created_at", Instant::class.java), updatedAt = getObject("updated_at", Instant::class.java),
    )
}

@Repository
class TicketTypeRepository(private val jdbc: NamedParameterJdbcTemplate) {
    fun insert(ticketType: TicketType): TicketType {
        jdbc.update(
            """INSERT INTO ticket_type (id, event_id, name, currency, unit_price_minor, capacity, status, created_at, updated_at)
                VALUES (:id, :eventId, :name, :currency, :unitPriceMinor, :capacity, :status, :createdAt, :updatedAt)""",
            MapSqlParameterSource().addValue("id", ticketType.id).addValue("eventId", ticketType.eventId)
                .addValue("name", ticketType.name).addValue("currency", ticketType.currency)
                .addValue("unitPriceMinor", ticketType.unitPriceMinor).addValue("capacity", ticketType.capacity)
                .addValue("status", ticketType.status.name).addValue("createdAt", ticketType.createdAt)
                .addValue("updatedAt", ticketType.updatedAt),
        )
        jdbc.update(
            """INSERT INTO ticket_inventory (ticket_type_id, sold_quantity, reserved_quantity, updated_at)
                VALUES (:ticketTypeId, 0, 0, :updatedAt)""",
            mapOf("ticketTypeId" to ticketType.id, "updatedAt" to ticketType.createdAt),
        )
        return ticketType
    }

    fun hasActiveForEvent(eventId: UUID): Boolean = jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM ticket_type WHERE event_id = :eventId AND status = 'ACTIVE')",
        mapOf("eventId" to eventId), Boolean::class.java,
    ) ?: false
}
