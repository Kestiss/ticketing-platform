package com.ticketingplatform.backend.payments

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/payment-profiles")
class PaymentProfileController(private val service: PaymentProfileService) {
    @PostMapping
    fun create(@PathVariable organizationId: UUID, @Valid @RequestBody request: CreatePaymentProfileRequest): ResponseEntity<PaymentProfileResponse> {
        val profile = service.create(CreatePaymentProfileCommand(organizationId, request.providerAccountReference, request.settlementCurrency))
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(profile.id).toUri()
        return ResponseEntity.created(location).body(profile.toResponse())
    }

    @GetMapping("/{paymentProfileId}")
    fun get(@PathVariable organizationId: UUID, @PathVariable paymentProfileId: UUID) = service.getForOrganization(paymentProfileId, organizationId).toResponse()

    @ExceptionHandler(PaymentProfileNotFoundException::class)
    fun notFound(exception: PaymentProfileNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Payment profile not found"),
    )
}

data class CreatePaymentProfileRequest(@field:NotBlank val providerAccountReference: String, @field:Pattern(regexp = "^[A-Za-z]{3}$") val settlementCurrency: String)
data class PaymentProfileResponse(val id: UUID, val providerType: PaymentProviderType, val providerAccountReference: String, val settlementCurrency: String, val status: PaymentProfileStatus, val createdAt: Instant)
private fun PaymentProfile.toResponse() = PaymentProfileResponse(id, providerType, providerAccountReference, settlementCurrency, status, createdAt)
