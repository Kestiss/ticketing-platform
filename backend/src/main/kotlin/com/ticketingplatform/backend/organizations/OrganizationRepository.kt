package com.ticketingplatform.backend.organizations

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OrganizationRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun insert(organization: Organization): Organization {
        val parameters = MapSqlParameterSource()
            .addValue("id", organization.id)
            .addValue("legalName", organization.legalName)
            .addValue("displayName", organization.displayName)
            .addValue("status", organization.status.name)
            .addValue("defaultLocale", organization.defaultLocale)
            .addValue("createdAt", organization.createdAt)
            .addValue("updatedAt", organization.updatedAt)

        jdbcTemplate.update(
            """
            INSERT INTO organization (
                id, legal_name, display_name, status, default_locale, created_at, updated_at
            ) VALUES (
                :id, :legalName, :displayName, :status, :defaultLocale, :createdAt, :updatedAt
            )
            """.trimIndent(),
            parameters,
        )
        return organization
    }

    fun findById(id: UUID): Organization? = jdbcTemplate.query(
        """
        SELECT id, legal_name, display_name, status, default_locale, created_at, updated_at
        FROM organization
        WHERE id = :id
        """.trimIndent(),
        mapOf("id" to id),
    ) { resultSet, _ -> resultSet.toOrganization() }.singleOrNull()

    private fun ResultSet.toOrganization() = Organization(
        id = getObject("id", UUID::class.java),
        legalName = getString("legal_name"),
        displayName = getString("display_name"),
        status = OrganizationStatus.valueOf(getString("status")),
        defaultLocale = getString("default_locale"),
        createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )
}
