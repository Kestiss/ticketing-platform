package com.ticketingplatform.backend.payments

import java.time.Instant
import java.util.UUID

enum class PaymentProviderType { STRIPE }
enum class PaymentProfileStatus { ACTIVE, INACTIVE }

data class PaymentProfile(
    val id: UUID,
    val organizationId: UUID,
    val providerType: PaymentProviderType,
    val providerAccountReference: String,
    val settlementCurrency: String,
    val status: PaymentProfileStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
