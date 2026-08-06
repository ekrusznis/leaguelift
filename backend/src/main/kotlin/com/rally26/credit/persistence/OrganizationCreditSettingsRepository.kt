package com.rally26.credit.persistence

import com.rally26.credit.domain.CreditExpirationPolicy
import com.rally26.credit.domain.OrganizationCreditSettings
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, default_credit_percent, expiration_policy, expiration_months, p2p_transfer_enabled, updated_at
"""

@Repository
class OrganizationCreditSettingsRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByOrganization(organizationId: UUID): OrganizationCreditSettings? =
        jdbcClient
            .sql("select $COLUMNS from organization_credit_settings where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Idempotent — creates the default row on first real use (grant/settings-page-open), never assumed to pre-exist. */
    fun getOrCreateDefault(organizationId: UUID): OrganizationCreditSettings {
        findByOrganization(organizationId)?.let { return it }
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into organization_credit_settings (id, organization_id, updated_at)
                values (:id, :organizationId, :now)
                on conflict (organization_id) do nothing
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("now", Timestamp.from(now))
            .update()
        return findByOrganization(organizationId)!!
    }

    fun upsert(
        organizationId: UUID,
        defaultCreditPercent: Int,
        expirationPolicy: CreditExpirationPolicy,
        expirationMonths: Int?,
        p2pTransferEnabled: Boolean,
    ): OrganizationCreditSettings {
        getOrCreateDefault(organizationId)
        jdbcClient
            .sql(
                """
                update organization_credit_settings
                set default_credit_percent = :defaultCreditPercent, expiration_policy = :expirationPolicy,
                    expiration_months = :expirationMonths, p2p_transfer_enabled = :p2pTransferEnabled, updated_at = :now
                where organization_id = :organizationId
                """.trimIndent(),
            ).param("defaultCreditPercent", defaultCreditPercent)
            .param("expirationPolicy", expirationPolicy.name)
            .param("expirationMonths", expirationMonths)
            .param("p2pTransferEnabled", p2pTransferEnabled)
            .param("now", Timestamp.from(Instant.now()))
            .param("organizationId", organizationId)
            .update()
        return findByOrganization(organizationId)!!
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): OrganizationCreditSettings =
        OrganizationCreditSettings(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            defaultCreditPercent = rs.getInt("default_credit_percent"),
            expirationPolicy = CreditExpirationPolicy.valueOf(rs.getString("expiration_policy")),
            expirationMonths = rs.getObject("expiration_months", java.lang.Integer::class.java)?.toInt(),
            p2pTransferEnabled = rs.getBoolean("p2p_transfer_enabled"),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
