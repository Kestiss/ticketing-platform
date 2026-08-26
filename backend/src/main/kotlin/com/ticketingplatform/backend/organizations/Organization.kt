package com.ticketingplatform.backend.organizations

import java.time.Instant
import java.util.UUID

enum class OrganizationStatus {
    ACTIVE,
    SUSPENDED,
}

data class Organization(
    val id: UUID,
    val legalName: String,
    val displayName: String,
    val status: OrganizationStatus,
    val defaultLocale: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
