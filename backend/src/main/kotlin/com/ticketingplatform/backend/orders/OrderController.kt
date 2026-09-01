package com.ticketingplatform.backend.orders

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
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
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/events/{eventId}/orders")
class OrderController(private val service: OrderService, private val checkout: CheckoutService) {
    @PostMapping
    fun create(@PathVariable organizationId: UUID, @PathVariable eventId: UUID, @Valid @RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> {
        val order = service.createFromReservation(CreateOrderCommand(organizationId, eventId, request.reservationId, request.customerEmail))
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(order.id).toUri()
        return ResponseEntity.created(location).body(order.toResponse())
    }

    @PostMapping("/{orderId}/checkout")
    fun startCheckout(@PathVariable organizationId: UUID, @PathVariable orderId: UUID, @RequestHeader("Idempotency-Key") key: String, @Valid @RequestBody request: StartCheckoutRequest): CheckoutResponse {
        val result = checkout.start(StartCheckoutCommand(organizationId, orderId, request.successUrl, request.cancelUrl, key))
        return CheckoutResponse(result.paymentAttemptId, result.checkoutReference, result.redirectUrl)
    }

    @GetMapping("/{orderId}")
    fun get(@PathVariable organizationId: UUID, @PathVariable orderId: UUID) = service.get(orderId, organizationId).toResponse()

    @ExceptionHandler(OrderReservationNotFoundException::class, OrderNotFoundException::class)
    fun notFound(exception: RuntimeException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Order resource not found"))
    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun invalid(exception: RuntimeException) = ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid order request"))
}

data class CreateOrderRequest(val reservationId: UUID, @field:Email val customerEmail: String)
data class StartCheckoutRequest(@field:NotBlank val successUrl: String, @field:NotBlank val cancelUrl: String)
data class OrderResponse(val id: UUID, val reservationId: UUID, val customerEmail: String, val currency: String, val totalAmountMinor: Long, val status: OrderStatus, val createdAt: Instant)
data class CheckoutResponse(val paymentAttemptId: UUID, val checkoutReference: String, val redirectUrl: String?)
private fun CustomerOrder.toResponse() = OrderResponse(id, reservationId, customerEmail, currency, totalAmountMinor, status, createdAt)
