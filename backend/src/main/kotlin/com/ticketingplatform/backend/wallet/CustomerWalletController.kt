package com.ticketingplatform.backend.wallet

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Duration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/customer/wallet")
class CustomerWalletController(
    private val wallet: CustomerWalletService,
) {
    @PostMapping("/magic-links")
    fun requestMagicLink(@Valid @RequestBody request: MagicLinkRequest): ResponseEntity<Void> {
        wallet.requestMagicLink(request.email)
        // Always return the same response; never disclose whether an email owns tickets.
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/magic-links/redeem")
    fun redeem(@Valid @RequestBody request: RedeemMagicLinkRequest): ResponseEntity<Void> {
        val session = wallet.redeemMagicLink(request.token)
        val cookie = ResponseCookie.from("ticketing_wallet", session.rawSessionToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/api/v1/customer/wallet")
            .maxAge(Duration.between(java.time.Instant.now(), session.expiresAt))
            .build()
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build()
    }

    @GetMapping("/tickets")
    fun tickets(@CookieValue("ticketing_wallet") sessionToken: String): List<WalletTicketView> =
        wallet.listTickets(sessionToken)

    @ExceptionHandler(InvalidMagicLinkException::class, InvalidWalletSessionException::class)
    fun unauthorized(exception: RuntimeException): ResponseEntity<ProblemDetail> = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.message ?: "Unauthorized"),
    )
}

data class MagicLinkRequest(@field:Email val email: String)
data class RedeemMagicLinkRequest(@field:NotBlank val token: String)
