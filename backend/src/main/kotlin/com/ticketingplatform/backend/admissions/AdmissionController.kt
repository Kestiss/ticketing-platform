package com.ticketingplatform.backend.admissions

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/events/{eventId}")
class AdmissionController(private val service: AdmissionService) {
    @PostMapping("/scanners")
    fun createScanner(@PathVariable organizationId: UUID, @PathVariable eventId: UUID, @Valid @RequestBody request: CreateScannerRequest): ScannerBootstrapResponse {
        val result = service.createScanner(CreateScannerDeviceCommand(organizationId, eventId, request.displayName))
        return ScannerBootstrapResponse(result.scannerDeviceId, result.rawScannerSecret)
    }

    @PostMapping("/admissions/validate")
    fun validate(@PathVariable eventId: UUID, @RequestHeader("X-Scanner-Device") scannerDevice: UUID,
                 @RequestHeader("X-Scanner-Secret") scannerSecret: String, @Valid @RequestBody request: ValidateAdmissionRequest): AdmissionResponse {
        val decision = service.validate(ValidateAdmissionCommand(scannerDevice, scannerSecret, eventId, request.presentationClaim))
        return AdmissionResponse(decision.outcome, decision.reasonCode, decision.ticketEntitlementId, decision.scannedAt)
    }

    @ExceptionHandler(InvalidScannerDeviceException::class)
    fun scannerUnauthorized(exception: InvalidScannerDeviceException): ResponseEntity<ProblemDetail> = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Scanner unauthorized"))
}

data class CreateScannerRequest(@field:NotBlank val displayName: String)
data class ScannerBootstrapResponse(val scannerDeviceId: UUID, val scannerSecret: String)
data class ValidateAdmissionRequest(@field:NotBlank val presentationClaim: String)
data class AdmissionResponse(val outcome: AdmissionOutcome, val reasonCode: String, val ticketEntitlementId: UUID?, val scannedAt: Instant)
