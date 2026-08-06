package com.rally26.credit.persistence

import com.rally26.credit.domain.CampaignHouseholdAttributionLink
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, household_id, campaign_id, code, public_display_name, created_at"

@Repository
class CampaignHouseholdAttributionLinkRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findForHouseholdAndCampaign(
        organizationId: UUID,
        householdId: UUID,
        campaignId: UUID,
    ): CampaignHouseholdAttributionLink? =
        jdbcClient
            .sql(
                "select $COLUMNS from campaign_household_attribution_link where organization_id = :organizationId and household_id = :householdId and campaign_id = :campaignId",
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("campaignId", campaignId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByCode(
        organizationId: UUID,
        campaignId: UUID,
        code: String,
    ): CampaignHouseholdAttributionLink? =
        jdbcClient
            .sql(
                "select $COLUMNS from campaign_household_attribution_link where organization_id = :organizationId and campaign_id = :campaignId and code = :code",
            ).param("organizationId", organizationId)
            .param("campaignId", campaignId)
            .param("code", code)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        organizationId: UUID,
        householdId: UUID,
        campaignId: UUID,
        code: String,
    ): CampaignHouseholdAttributionLink {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into campaign_household_attribution_link
                    (id, organization_id, household_id, campaign_id, code, created_at)
                values
                    (:id, :organizationId, :householdId, :campaignId, :code, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("campaignId", campaignId)
            .param("code", code)
            .param("now", Timestamp.from(now))
            .update()
        return CampaignHouseholdAttributionLink(id, organizationId, householdId, campaignId, code, null, now)
    }

    fun updatePublicDisplayName(
        organizationId: UUID,
        id: UUID,
        publicDisplayName: String?,
    ): Int =
        jdbcClient
            .sql(
                "update campaign_household_attribution_link set public_display_name = :publicDisplayName where id = :id and organization_id = :organizationId",
            ).param("publicDisplayName", publicDisplayName)
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): CampaignHouseholdAttributionLink =
        CampaignHouseholdAttributionLink(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            campaignId = rs.getObject("campaign_id", UUID::class.java),
            code = rs.getString("code"),
            publicDisplayName = rs.getString("public_display_name"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
