package com.ticketingplatform.backend.payments

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PaymentProfileRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun insert(profile: PaymentProfile): PaymentProfile {
        jdbc.update(
            """
            INSERT INTO payment_profile (id, organization_id, provider_type, provider_account_reference,
              settlement_currency, status, created_at, updated_at)
            VALUES (:id, :organizationId, :providerType, :providerAccountReference,
              :settlementCurrency, :status, :createdAt, :updatedAt)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", profile.id)
                .addValue("organizationId", profile.organizationId)
                .addValue("providerType", profile.providerType.name)
                .addValue("providerAccountReference", profile.providerAccountReference)
                .addValue("settlementCurrency", profile.settlementCurrency)
                .addValue("status", profile.status.name)
                .addValue("createdAt", profile.createdAt)
                .addValue("updatedAt", profile.updatedAt),
        )
        return profile
    }

    fun findByIdForOrganization(id: UUID, organizationId: UUID): PaymentProfile? = jdbc.query(
        "SELECT * FROM payment_profile WHERE id = :id AND organization_id = :organizationId",
        mapOf("id" to id, "organizationId" to organizationId),
    ) { rs, _ -> rs.toPaymentProfile() }.singleOrNull()

    private fun ResultSet.toPaymentProfile() = PaymentProfile(
        id = getObject("id", UUID::class.java),
        organizationId = getObject("organization_id", UUID::class.java),
        providerType = PaymentProviderType.valueOf(getString("provider_type")),
        providerAccountReference = getString("provider_account_reference"),
        settlementCurrency = getString("settlement_currency"),
        status = PaymentProfileStatus.valueOf(getString("status")),
        createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )
}
