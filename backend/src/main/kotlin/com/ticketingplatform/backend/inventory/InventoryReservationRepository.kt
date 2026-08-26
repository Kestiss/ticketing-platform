package com.ticketingplatform.backend.inventory

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class InventoryReservationRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun findByIdempotencyKey(organizationId: UUID, eventId: UUID, ticketTypeId: UUID, idempotencyKey: String): InventoryReservation? = jdbc.query(
        """SELECT * FROM inventory_reservation WHERE organization_id = :organizationId AND event_id = :eventId
           AND ticket_type_id = :ticketTypeId AND idempotency_key = :idempotencyKey""",
        mapOf("organizationId" to organizationId, "eventId" to eventId, "ticketTypeId" to ticketTypeId, "idempotencyKey" to idempotencyKey),
    ) { rs, _ -> rs.toReservation() }.singleOrNull()

    fun insert(reservation: InventoryReservation): InventoryReservation {
        jdbc.update(
            """INSERT INTO inventory_reservation (id, organization_id, event_id, ticket_type_id, requested_quantity,
              status, idempotency_key, expires_at, created_at, updated_at)
              VALUES (:id, :organizationId, :eventId, :ticketTypeId, :requestedQuantity, :status,
              :idempotencyKey, :expiresAt, :createdAt, :updatedAt)""",
            MapSqlParameterSource()
                .addValue("id", reservation.id).addValue("organizationId", reservation.organizationId)
                .addValue("eventId", reservation.eventId).addValue("ticketTypeId", reservation.ticketTypeId)
                .addValue("requestedQuantity", reservation.requestedQuantity).addValue("status", reservation.status.name)
                .addValue("idempotencyKey", reservation.idempotencyKey).addValue("expiresAt", reservation.expiresAt)
                .addValue("createdAt", reservation.createdAt).addValue("updatedAt", reservation.updatedAt),
        )
        return reservation
    }

    fun findByIdForOrganization(id: UUID, organizationId: UUID): InventoryReservation? = jdbc.query(
        "SELECT * FROM inventory_reservation WHERE id = :id AND organization_id = :organizationId",
        mapOf("id" to id, "organizationId" to organizationId),
    ) { rs, _ -> rs.toReservation() }.singleOrNull()

    fun expire(id: UUID, now: Instant): InventoryReservation? = updateAndReturn(id, InventoryReservationStatus.ACTIVE, InventoryReservationStatus.EXPIRED, now)
    fun cancel(id: UUID, now: Instant): InventoryReservation? = updateAndReturn(id, InventoryReservationStatus.ACTIVE, InventoryReservationStatus.CANCELLED, now)

    fun claimExpired(now: Instant, limit: Int): List<InventoryReservation> = jdbc.query(
        """WITH candidates AS (
              SELECT id FROM inventory_reservation
              WHERE status = 'ACTIVE' AND expires_at <= :now
              ORDER BY expires_at
              FOR UPDATE SKIP LOCKED
              LIMIT :limit
            )
            UPDATE inventory_reservation reservation
            SET status = 'EXPIRED', updated_at = :now
            FROM candidates
            WHERE reservation.id = candidates.id
            RETURNING reservation.*""",
        mapOf("now" to now, "limit" to limit),
    ) { rs, _ -> rs.toReservation() }

    private fun updateAndReturn(id: UUID, from: InventoryReservationStatus, to: InventoryReservationStatus, now: Instant): InventoryReservation? {
        val updated = jdbc.query(
            """UPDATE inventory_reservation SET status = :toStatus, updated_at = :now
               WHERE id = :id AND status = :fromStatus
               RETURNING *""",
            mapOf("id" to id, "fromStatus" to from.name, "toStatus" to to.name, "now" to now),
        ) { rs, _ -> rs.toReservation() }
        return updated.singleOrNull()
    }

    private fun ResultSet.toReservation() = InventoryReservation(
        id = getObject("id", UUID::class.java), organizationId = getObject("organization_id", UUID::class.java),
        eventId = getObject("event_id", UUID::class.java), ticketTypeId = getObject("ticket_type_id", UUID::class.java),
        requestedQuantity = getInt("requested_quantity"), status = InventoryReservationStatus.valueOf(getString("status")),
        idempotencyKey = getString("idempotency_key"), expiresAt = getObject("expires_at", Instant::class.java),
        createdAt = getObject("created_at", Instant::class.java), updatedAt = getObject("updated_at", Instant::class.java),
    )
}
