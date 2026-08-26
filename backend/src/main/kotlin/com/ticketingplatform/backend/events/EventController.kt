package com.ticketingplatform.backend.events

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
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
@RequestMapping("/api/v1")
class EventController(private val service: EventService) {
    @PostMapping("/organizations/{organizationId}/events")
    fun createEvent(@PathVariable organizationId: UUID, @Valid @RequestBody request: CreateEventRequest): ResponseEntity<EventResponse> {
        val event = service.create(CreateEventCommand(organizationId, request.paymentProfileId, request.name, request.timeZone, request.startsAt, request.endsAt))
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(event.id).toUri()
        return ResponseEntity.created(location).body(event.toResponse())
    }

    @GetMapping("/organizations/{organizationId}/events/{eventId}")
    fun getEvent(@PathVariable organizationId: UUID, @PathVariable eventId: UUID) = service.get(eventId, organizationId).toResponse()

    @PostMapping("/organizations/{organizationId}/events/{eventId}/ticket-types")
    fun addTicketType(@PathVariable organizationId: UUID, @PathVariable eventId: UUID, @Valid @RequestBody request: CreateTicketTypeRequest): ResponseEntity<TicketTypeResponse> {
        val ticketType = service.addTicketType(CreateTicketTypeCommand(organizationId, eventId, request.name, request.currency, request.unitPriceMinor, request.capacity))
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(ticketType.id).toUri()
        return ResponseEntity.created(location).body(ticketType.toResponse())
    }

    @PostMapping("/organizations/{organizationId}/events/{eventId}/sales/open")
    fun openSales(@PathVariable organizationId: UUID, @PathVariable eventId: UUID, @Valid @RequestBody request: OpenSalesRequest) =
        service.openSales(eventId, organizationId, request.paymentProfileId).toResponse()

    @ExceptionHandler(EventNotFoundException::class)
    fun notFound(exception: EventNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Event not found"),
    )

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class, EventPaymentProfileOwnershipException::class)
    fun invalid(exception: RuntimeException) = ResponseEntity.badRequest().body(
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid request"),
    )
}

data class CreateEventRequest(@field:NotBlank val name: String, @field:NotBlank val timeZone: String, @field:NotNull val startsAt: Instant, @field:NotNull val endsAt: Instant, val paymentProfileId: UUID?)
data class CreateTicketTypeRequest(@field:NotBlank val name: String, @field:Pattern(regexp = "^[A-Za-z]{3}$") val currency: String, @field:Min(0) val unitPriceMinor: Long, @field:Min(1) val capacity: Int)
data class OpenSalesRequest(@field:NotNull val paymentProfileId: UUID)
data class EventResponse(val id: UUID, val organizationId: UUID, val paymentProfileId: UUID?, val name: String, val timeZone: String, val startsAt: Instant, val endsAt: Instant, val status: EventStatus, val paymentProfileLockedAt: Instant?)
data class TicketTypeResponse(val id: UUID, val eventId: UUID, val name: String, val currency: String, val unitPriceMinor: Long, val capacity: Int, val status: TicketTypeStatus)
private fun Event.toResponse() = EventResponse(id, organizationId, paymentProfileId, name, timeZone, startsAt, endsAt, status, paymentProfileLockedAt)
private fun TicketType.toResponse() = TicketTypeResponse(id, eventId, name, currency, unitPriceMinor, capacity, status)
