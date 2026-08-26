package com.ticketingplatform.backend.organizations

import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class InvalidOrganizationException(message: String) : RuntimeException(message)

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun create(command: CreateOrganizationCommand): Organization {
        val legalName = command.legalName.trim()
        val displayName = command.displayName.trim()
        val defaultLocale = command.defaultLocale.trim()

        require(legalName.isNotBlank()) { "legalName must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(defaultLocale.matches(LOCALE_PATTERN)) { "defaultLocale must be a valid BCP 47 language tag" }

        val now = Instant.now(clock)
        return organizationRepository.insert(
            Organization(
                id = UUID.randomUUID(),
                legalName = legalName,
                displayName = displayName,
                status = OrganizationStatus.ACTIVE,
                defaultLocale = Locale.forLanguageTag(defaultLocale).toLanguageTag(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Organization = organizationRepository.findById(id)
        ?: throw OrganizationNotFoundException(id)

    companion object {
        private val LOCALE_PATTERN = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")
    }
}

data class CreateOrganizationCommand(
    val legalName: String,
    val displayName: String,
    val defaultLocale: String,
)

class OrganizationNotFoundException(id: UUID) : RuntimeException("Organization $id was not found")
