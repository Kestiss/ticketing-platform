package com.ticketingplatform.backend.admissions

import com.ticketingplatform.backend.events.CreateEventCommand
import com.ticketingplatform.backend.events.CreateTicketTypeCommand
import com.ticketingplatform.backend.events.EventService
import com.ticketingplatform.backend.inventory.CreateReservationCommand
import com.ticketingplatform.backend.inventory.InventoryReservationService
import com.ticketingplatform.backend.inventory.PostgreSqlTestConfiguration
import com.ticketingplatform.backend.orders.CreateOrderCommand
import com.ticketingplatform.backend.orders.OrderService
import com.ticketingplatform.backend.orders.PaymentFinalizationService
import com.ticketingplatform.backend.organizations.CreateOrganizationCommand
import com.ticketingplatform.backend.organizations.OrganizationService
import com.ticketingplatform.backend.payments.CreatePaymentProfileCommand
import com.ticketingplatform.backend.payments.PaymentProfileService
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

@SpringBootTest(properties = ["ticketing.wallet.presentation-signing-key=test-signing-key"])
@Import(PostgreSqlTestConfiguration::class)
class AdmissionServiceIntegrationTest(
    @Autowired private val organizations: OrganizationService,
    @Autowired private val paymentProfiles: PaymentProfileService,
    @Autowired private val events: EventService,
    @Autowired private val reservations: InventoryReservationService,
    @Autowired private val orders: OrderService,
    @Autowired private val finalization: PaymentFinalizationService,
    @Autowired private val admissions: AdmissionService,
    @Autowired private val jdbc: NamedParameterJdbcTemplate,
    @Value("\${ticketing.wallet.presentation-signing-key}") private val signingKey: String,
) {
    @Test
    fun `valid ticket is admitted once and duplicate is rejected`() {
        val fixture = admissionFixture()

        val first = admissions.validate(
            ValidateAdmissionCommand(
                fixture.organizationId,
                fixture.scannerDeviceId,
                fixture.scannerSecret,
                fixture.eventId,
                fixture.presentationClaim,
            ),
        )
        val duplicate = admissions.validate(
            ValidateAdmissionCommand(
                fixture.organizationId,
                fixture.scannerDeviceId,
                fixture.scannerSecret,
                fixture.eventId,
                fixture.presentationClaim,
            ),
        )

        assertEquals(AdmissionOutcome.ADMITTED, first.outcome)
        assertEquals("VALID", first.reasonCode)
        assertEquals(fixture.entitlementId, first.ticketEntitlementId)
        assertEquals(AdmissionOutcome.REJECTED, duplicate.outcome)
        assertEquals("ALREADY_ADMITTED", duplicate.reasonCode)
        assertEquals(fixture.entitlementId, duplicate.ticketEntitlementId)
    }

    @Test
    fun `invalid and tampered claims are rejected`() {
        val fixture = admissionFixture()

        val invalid = admissions.validate(
            ValidateAdmissionCommand(
                fixture.organizationId,
                fixture.scannerDeviceId,
                fixture.scannerSecret,
                fixture.eventId,
                "not-a-claim",
            ),
        )
        val tampered = admissions.validate(
            ValidateAdmissionCommand(
                fixture.organizationId,
                fixture.scannerDeviceId,
                fixture.scannerSecret,
                fixture.eventId,
                fixture.presentationClaim.dropLast(1) + "x",
            ),
        )

        assertEquals(AdmissionOutcome.REJECTED, invalid.outcome)
        assertEquals("INVALID_CREDENTIAL", invalid.reasonCode)
        assertEquals(null, invalid.ticketEntitlementId)
        assertEquals(AdmissionOutcome.REJECTED, tampered.outcome)
        assertEquals("INVALID_CREDENTIAL", tampered.reasonCode)
        assertEquals(null, tampered.ticketEntitlementId)
    }

    @Test
    fun `revoked credential is rejected`() {
        val fixture = admissionFixture()
        jdbc.update(
            "UPDATE ticket_credential SET status = 'REVOKED', revoked_at = :now WHERE id = :id",
            mapOf("id" to fixture.credentialId, "now" to Instant.now()),
        )

        val decision = admissions.validate(
            ValidateAdmissionCommand(
                fixture.organizationId,
                fixture.scannerDeviceId,
                fixture.scannerSecret,
                fixture.eventId,
                fixture.presentationClaim,
            ),
        )

        assertEquals(AdmissionOutcome.REJECTED, decision.outcome)
        assertEquals("REVOKED_OR_INVALID", decision.reasonCode)
        assertEquals(fixture.entitlementId, decision.ticketEntitlementId)
    }

    @Test
    fun `scanner assigned to different event is denied`() {
        val fixture = admissionFixture()
        val otherEvent = events.create(
            CreateEventCommand(
                fixture.organizationId,
                null,
                "Other Event ${UUID.randomUUID()}",
                "Europe/Vilnius",
                Instant.parse("2030-06-01T10:00:00Z"),
                Instant.parse("2030-06-01T12:00:00Z"),
            ),
        )

        assertFailsWith<InvalidScannerDeviceException> {
            admissions.validate(
                ValidateAdmissionCommand(
                    fixture.organizationId,
                    fixture.scannerDeviceId,
                    fixture.scannerSecret,
                    otherEvent.id,
                    fixture.presentationClaim,
                ),
            )
        }
    }

    @Test
    fun `scanner from different organization is denied`() {
        val fixture = admissionFixture()
        val foreignFixture = scannerFixture()

        assertFailsWith<InvalidScannerDeviceException> {
            admissions.validate(
                ValidateAdmissionCommand(
                    fixture.organizationId,
                    foreignFixture.scannerDeviceId,
                    foreignFixture.scannerSecret,
                    fixture.eventId,
                    fixture.presentationClaim,
                ),
            )
        }
    }

    private fun admissionFixture(): AdmissionFixture {
        val suffix = UUID.randomUUID().toString()
        val organization = organizations.create(CreateOrganizationCommand("Events $suffix", "Events", "en-GB"))
        val profile = paymentProfiles.create(CreatePaymentProfileCommand(organization.id, "acct_$suffix", "EUR"))
        val event = events.create(
            CreateEventCommand(
                organization.id,
                null,
                "Event $suffix",
                "Europe/Vilnius",
                Instant.parse("2030-01-01T10:00:00Z"),
                Instant.parse("2030-01-01T12:00:00Z"),
            ),
        )
        val ticketType = events.addTicketType(CreateTicketTypeCommand(organization.id, event.id, "General", "EUR", 1250, 10))
        events.openSales(event.id, organization.id, profile.id)
        val reservation = reservations.reserve(CreateReservationCommand(organization.id, event.id, ticketType.id, 1, "reservation-$suffix"))
        val order = orders.createFromReservation(CreateOrderCommand(organization.id, event.id, reservation.id, "buyer-$suffix@example.test"))
        val checkoutReference = "cs_test_$suffix"
        jdbc.update(
            """INSERT INTO payment_attempt (id, order_id, provider_type, provider_checkout_reference, status, idempotency_key, created_at, updated_at)
                VALUES (:id, :orderId, 'STRIPE', :checkoutReference, 'CHECKOUT_STARTED', :idempotencyKey, :now, :now)""",
            mapOf(
                "id" to UUID.randomUUID(),
                "orderId" to order.id,
                "checkoutReference" to checkoutReference,
                "idempotencyKey" to "payment-$suffix",
                "now" to Instant.now(),
            ),
        )
        finalization.processStripeCheckoutCompleted("evt_test_$suffix", profile.providerAccountReference, checkoutReference, "pi_test_$suffix", 1250, "EUR")
        val credential = jdbc.query(
            """SELECT credential.id, credential.version, entitlement.id AS entitlement_id
               FROM ticket_credential credential
               JOIN ticket_entitlement entitlement ON entitlement.id = credential.ticket_entitlement_id
               WHERE entitlement.order_id = :orderId""",
            mapOf("orderId" to order.id),
        ) { rs, _ -> CredentialFixture(rs.getObject("id", UUID::class.java), rs.getInt("version"), rs.getObject("entitlement_id", UUID::class.java)) }.single()

        val scanner = admissions.createScanner(CreateScannerDeviceCommand(organization.id, event.id, "Gate scanner $suffix"))
        return AdmissionFixture(
            organization.id,
            event.id,
            scanner.scannerDeviceId,
            scanner.rawScannerSecret,
            credential.id,
            credential.entitlementId,
            signedClaim(credential.id, credential.version, event.id),
        )
    }

    private fun scannerFixture(): ScannerFixture {
        val suffix = UUID.randomUUID().toString()
        val organization = organizations.create(CreateOrganizationCommand("Org $suffix", "Org", "en-GB"))
        val event = events.create(
            CreateEventCommand(
                organization.id,
                null,
                "Foreign Event $suffix",
                "Europe/Vilnius",
                Instant.parse("2030-02-01T10:00:00Z"),
                Instant.parse("2030-02-01T12:00:00Z"),
            ),
        )
        val scanner = admissions.createScanner(CreateScannerDeviceCommand(organization.id, event.id, "Foreign scanner $suffix"))
        return ScannerFixture(scanner.scannerDeviceId, scanner.rawScannerSecret)
    }

    private fun signedClaim(credentialId: UUID, version: Int, eventId: UUID): String {
        val payload = "$credentialId.$version.$eventId"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
        return "$payload.$signature"
    }

    private data class CredentialFixture(val id: UUID, val version: Int, val entitlementId: UUID)

    private data class AdmissionFixture(
        val organizationId: UUID,
        val eventId: UUID,
        val scannerDeviceId: UUID,
        val scannerSecret: String,
        val credentialId: UUID,
        val entitlementId: UUID,
        val presentationClaim: String,
    )

    private data class ScannerFixture(
        val scannerDeviceId: UUID,
        val scannerSecret: String,
    )
}
