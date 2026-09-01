package com.ticketingplatform.backend.orders

import com.ticketingplatform.backend.events.CreateEventCommand
import com.ticketingplatform.backend.events.CreateTicketTypeCommand
import com.ticketingplatform.backend.events.EventService
import com.ticketingplatform.backend.inventory.CreateReservationCommand
import com.ticketingplatform.backend.inventory.InventoryReservationService
import com.ticketingplatform.backend.inventory.PostgreSqlTestConfiguration
import com.ticketingplatform.backend.organizations.CreateOrganizationCommand
import com.ticketingplatform.backend.organizations.OrganizationService
import com.ticketingplatform.backend.payments.CreatePaymentProfileCommand
import com.ticketingplatform.backend.payments.PaymentProfileService
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

@SpringBootTest
@Import(PostgreSqlTestConfiguration::class)
class PaymentFinalizationIntegrationTest(
    @Autowired private val organizations: OrganizationService,
    @Autowired private val profiles: PaymentProfileService,
    @Autowired private val events: EventService,
    @Autowired private val reservations: InventoryReservationService,
    @Autowired private val orders: OrderService,
    @Autowired private val finalization: PaymentFinalizationService,
    @Autowired private val jdbc: NamedParameterJdbcTemplate,
) {
    @Test
    fun `paid checkout converts inventory and issues tickets exactly once`() {
        val suffix = UUID.randomUUID().toString()
        val organization = organizations.create(CreateOrganizationCommand("Events $suffix", "Events", "en-GB"))
        val profile = profiles.create(CreatePaymentProfileCommand(organization.id, "acct_$suffix", "EUR"))
        val event = events.create(CreateEventCommand(organization.id, null, "Event", "Europe/Vilnius", Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T12:00:00Z")))
        val type = events.addTicketType(CreateTicketTypeCommand(organization.id, event.id, "General", "EUR", 1250, 10))
        events.openSales(event.id, organization.id, profile.id)
        val reservation = reservations.reserve(CreateReservationCommand(organization.id, event.id, type.id, 2, "reservation"))
        val order = orders.createFromReservation(CreateOrderCommand(organization.id, event.id, reservation.id, "buyer@example.test"))
        val attemptId = UUID.randomUUID()
        jdbc.update("""INSERT INTO payment_attempt (id, order_id, provider_type, provider_checkout_reference, status, idempotency_key, created_at, updated_at)
            VALUES (:id, :orderId, 'STRIPE', 'cs_test_complete', 'CHECKOUT_STARTED', 'payment', :now, :now)""", mapOf("id" to attemptId, "orderId" to order.id, "now" to Instant.now()))

        finalization.processStripeCheckoutCompleted("evt_test_complete", profile.providerAccountReference, "cs_test_complete", "pi_test_complete", 2500, "EUR")
        finalization.processStripeCheckoutCompleted("evt_test_complete", profile.providerAccountReference, "cs_test_complete", "pi_test_complete", 2500, "EUR")

        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM customer_order WHERE id = :id", mapOf("id" to order.id), String::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT sold_quantity FROM ticket_inventory WHERE ticket_type_id = :id", mapOf("id" to type.id), Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM ticket_inventory WHERE ticket_type_id = :id", mapOf("id" to type.id), Int::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ticket_entitlement WHERE order_id = :id", mapOf("id" to order.id), Int::class.java))
    }
}
