package com.ticketingplatform.backend.payments

import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentProfileService(
    private val paymentProfileRepository: PaymentProfileRepository,
    private val clock: Clock,
) {
    @Transactional
    fun create(command: CreatePaymentProfileCommand): PaymentProfile {
        val accountReference = command.providerAccountReference.trim()
        val currency = command.settlementCurrency.trim().uppercase(Locale.ROOT)
        require(accountReference.isNotBlank()) { "providerAccountReference must not be blank" }
        require(CURRENCY_PATTERN.matches(currency)) { "settlementCurrency must be a three-letter ISO currency code" }
        val now = Instant.now(clock)
        return paymentProfileRepository.insert(
            PaymentProfile(
                id = UUID.randomUUID(),
                organizationId = command.organizationId,
                providerType = PaymentProviderType.STRIPE,
                providerAccountReference = accountReference,
                settlementCurrency = currency,
                status = PaymentProfileStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getForOrganization(profileId: UUID, organizationId: UUID): PaymentProfile =
        paymentProfileRepository.findByIdForOrganization(profileId, organizationId)
            ?: throw PaymentProfileNotFoundException(profileId)

    companion object {
        private val CURRENCY_PATTERN = Regex("^[A-Z]{3}$")
    }
}

data class CreatePaymentProfileCommand(
    val organizationId: UUID,
    val providerAccountReference: String,
    val settlementCurrency: String,
)

class PaymentProfileNotFoundException(id: UUID) : RuntimeException("Payment profile $id was not found")
