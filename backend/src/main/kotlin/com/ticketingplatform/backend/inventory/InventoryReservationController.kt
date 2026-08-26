package com.ticketingplatform.backend.inventory

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/events/{eventId}/reservations")
class InventoryReservationController(private val service: InventoryReservationService) {
    @PostMapping
    fun reserve(
        @PathVariable organizationId: UUID,
        @PathVariable eventId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateReservationRequest,
    ): ResponseEntity<InventoryReservationResponse> {
        val reservation = service.reserve(CreateReservationCommand(organizationId, eventId, request.ticketTypeId, request.quantity, idempotencyKey))
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(reservation.id).toUri()
        return ResponseEntity.created(location).body(reservation.toResponse())
    }

    @GetMapping("/{reservationId}")
    fun get(@PathVariable organizationId: UUID, @PathVariable reservationId: UUID) = service.get(reservationId, organizationId).toResponse()

    @DeleteMapping("/{reservationId}")
    fun cancel(@PathVariable organizationId: UUID, @PathVariable reservationId: UUID) = service.cancel(reservationId, organizationId).toResponse()

    @ExceptionHandler(ReservationNotFoundException::class)
    fun notFound(exception: ReservationNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Reservation not found"),
    )

    @ExceptionHandler(IllegalArgumentException::class, InventoryUnavailableException::class)
    fun invalid(exception: RuntimeException) = ResponseEntity.badRequest().body(
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Reservation request failed"),
    )
}

data class CreateReservationRequest(val ticketTypeId: UUID, @field:Min(1) @field:Max(10) val quantity: Int)
data class InventoryReservationResponse(val id: UUID, val ticketTypeId: UUID, val quantity: Int, val status: InventoryReservationStatus, val expiresAt: Instant)
private fun InventoryReservation.toResponse() = InventoryReservationResponse(id, ticketTypeId, requestedQuantity, status, expiresAt)
