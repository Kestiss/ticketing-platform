package com.ticketingplatform.backend.admissions

import java.time.Instant
import java.util.UUID

enum class ScannerDeviceStatus { ACTIVE, REVOKED }
enum class AdmissionOutcome { ADMITTED, REJECTED }

data class ScannerDevice(
    val id: UUID,
    val organizationId: UUID,
    val displayName: String,
    val deviceSecretHash: String,
    val status: ScannerDeviceStatus,
    val createdAt: Instant,
    val lastSeenAt: Instant?,
)

data class AdmissionDecision(
    val outcome: AdmissionOutcome,
    val reasonCode: String,
    val ticketEntitlementId: UUID?,
    val scannedAt: Instant,
)
