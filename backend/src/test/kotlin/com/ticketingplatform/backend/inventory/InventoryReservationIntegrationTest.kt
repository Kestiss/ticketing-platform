package com.ticketingplatform.backend.inventory

import com.ticketingplatform.backend.events.CreateEventCommand
import com.ticketingplatform.backend.events.CreateTicketTypeCommand
import com.ticketingplatform.backend.events.EventService
import com.ticketingplatform.backend.organizations.CreateOrganizationCommand
import com.ticketingplatform.backend.organizations.OrganizationService
import com.ticketingplatform.backend.payments.CreatePaymentProfileCommand
import com.ticketingplatform.backend.payments.PaymentProfileService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

@SpringBootTest
class InventoryReservationIntegrationTest(
    @Autowired private val organizations: OrganizationService,
    @Autowired private val paymentProfiles: PaymentProfileService,
    @Autowired private val events: EventService,
    @Autowired private val reservations: InventoryReservationService,
    @Autowired private val jdbc: NamedParameterJdbcTemplate,
) {
    @Test
    fun `concurrent reservations never exceed ticket capacity`() {
        val fixture = onSaleFixture(capacity = 5)
        val executor = Executors.newFixedThreadPool(8)
        val results = executor.invokeAll((1..8).map { attempt -> Callable {
            runCatching { reservations.reserve(CreateReservationCommand(fixture.organizationId, fixture.eventId, fixture.ticketTypeId, 1, "attempt-$attempt")) }.isSuccess
        } })
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(5, results.count { it.get() })
        assertEquals(5, inventoryReserved(fixture.ticketTypeId))
    }

    @Test
    fun `idempotent reservation creates capacity hold only once`() {
        val fixture = onSaleFixture(capacity = 5)
        val command = CreateReservationCommand(fixture.organizationId, fixture.eventId, fixture.ticketTypeId, 2, "same-request")
        val first = reservations.reserve(command)
        val repeated = reservations.reserve(command)

        assertEquals(first.id, repeated.id)
        assertEquals(2, inventoryReserved(fixture.ticketTypeId))
    }

    @Test
    fun `cancellation releases capacity exactly once`() {
        val fixture = onSaleFixture(capacity = 5)
        val reservation = reservations.reserve(CreateReservationCommand(fixture.organizationId, fixture.eventId, fixture.ticketTypeId, 3, "cancel-me"))

        reservations.cancel(reservation.id, fixture.organizationId)
        reservations.cancel(reservation.id, fixture.organizationId)

        assertEquals(0, inventoryReserved(fixture.ticketTypeId))
    }

    @Test
    fun `expiry releases capacity exactly once`() {
        val fixture = onSaleFixture(capacity = 5)
        val reservation = reservations.reserve(CreateReservationCommand(fixture.organizationId, fixture.eventId, fixture.ticketTypeId, 2, "expire-me"))
        jdbc.update("UPDATE inventory_reservation SET expires_at = :past WHERE id = :id", mapOf("past" to Instant.now().minusSeconds(1), "id" to reservation.id))

        assertEquals(1, reservations.expireDueReservations())
        assertEquals(0, reservations.expireDueReservations())
        assertEquals(0, inventoryReserved(fixture.ticketTypeId))
    }

    @Test
    fun `different organization cannot read reservation`() {
        val fixture = onSaleFixture(capacity = 5)
        val reservation = reservations.reserve(CreateReservationCommand(fixture.organizationId, fixture.eventId, fixture.ticketTypeId, 1, "private"))
        val other = organizations.create(CreateOrganizationCommand("Other Legal Name ${UUID.randomUUID()}", "Other", "en-GB"))

        assertFailsWith<ReservationNotFoundException> { reservations.get(reservation.id, other.id) }
    }

    private fun onSaleFixture(capacity: Int): Fixture {
        val suffix = UUID.randomUUID().toString()
        val organization = organizations.create(CreateOrganizationCommand("Events $suffix", "Events $suffix", "en-GB"))
        val profile = paymentProfiles.create(CreatePaymentProfileCommand(organization.id, "acct_$suffix", "EUR"))
        val event = events.create(CreateEventCommand(organization.id, null, "Event $suffix", "Europe/Vilnius", Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T12:00:00Z")))
        val ticketType = events.addTicketType(CreateTicketTypeCommand(organization.id, event.id, "General", "EUR", 1000, capacity))
        events.openSales(event.id, organization.id, profile.id)
        return Fixture(organization.id, event.id, ticketType.id)
    }

    private fun inventoryReserved(ticketTypeId: UUID): Int = jdbc.queryForObject(
        "SELECT reserved_quantity FROM ticket_inventory WHERE ticket_type_id = :id",
        mapOf("id" to ticketTypeId), Int::class.java,
    )!!

    private data class Fixture(val organizationId: UUID, val eventId: UUID, val ticketTypeId: UUID)
}
