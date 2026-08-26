package com.ticketingplatform.backend.organizations

import jakarta.validation.Valid
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationController(
    private val organizationService: OrganizationService,
) {
    @PostMapping
    fun create(@Valid @RequestBody request: CreateOrganizationRequest): ResponseEntity<OrganizationResponse> {
        val organization = organizationService.create(
            CreateOrganizationCommand(
                legalName = request.legalName,
                displayName = request.displayName,
                defaultLocale = request.defaultLocale,
            ),
        )
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(organization.id)
            .toUri()
        return ResponseEntity.created(location).body(organization.toResponse())
    }

    @GetMapping("/{organizationId}")
    fun get(@PathVariable organizationId: UUID): OrganizationResponse =
        organizationService.get(organizationId).toResponse()

    @ExceptionHandler(OrganizationNotFoundException::class)
    fun handleNotFound(exception: OrganizationNotFoundException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Organization not found"),
        )
}

data class CreateOrganizationRequest(
    @field:NotBlank val legalName: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val defaultLocale: String,
)

data class OrganizationResponse(
    val id: UUID,
    val legalName: String,
    val displayName: String,
    val status: OrganizationStatus,
    val defaultLocale: String,
    val createdAt: Instant,
)

private fun Organization.toResponse() = OrganizationResponse(
    id = id,
    legalName = legalName,
    displayName = displayName,
    status = status,
    defaultLocale = defaultLocale,
    createdAt = createdAt,
)
