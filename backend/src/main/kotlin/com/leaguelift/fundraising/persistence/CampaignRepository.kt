package com.leaguelift.fundraising.persistence

import com.leaguelift.fundraising.domain.Campaign
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.CampaignType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val COLUMNS = "id, organization_id, team_id, name, slug, description, campaign_type, goal_amount_minor, currency, start_date, end_date, status, published_at, created_at, updated_at"

@Repository
class CampaignRepository(private val jdbcClient: JdbcClient) {

    fun findById(id: UUID, organizationId: UUID): Campaign? =
        jdbcClient.sql("select $COLUMNS from campaign where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Most recently created active campaign for a team — used by the owner dashboard's team-performance row (one team, one headline fundraiser at a time is enough for that card). */
    fun findActiveByTeam(teamId: UUID, organizationId: UUID): Campaign? =
        jdbcClient.sql(
            """
            select $COLUMNS from campaign
            where team_id = :teamId and organization_id = :organizationId and status = 'ACTIVE'
            order by created_at desc
            limit 1
            """.trimIndent(),
        )
            .param("teamId", teamId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findBySlug(slug: String): Campaign? =
        jdbcClient.sql("select $COLUMNS from campaign where lower(slug) = lower(:slug)")
            .param("slug", slug)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAll(organizationId: UUID, offset: Int, limit: Int): List<Campaign> =
        jdbcClient.sql(
            """
            select $COLUMNS from campaign
            where organization_id = :organizationId
            order by created_at desc
            offset :offset limit :limit
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from campaign where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        teamId: UUID?,
        name: String,
        slug: String,
        description: String?,
        campaignType: CampaignType,
        goalAmountMinor: Long,
        currency: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): Campaign {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into campaign (id, organization_id, team_id, name, slug, description, campaign_type, goal_amount_minor, currency, start_date, end_date, status, created_at, updated_at)
            values (:id, :organizationId, :teamId, :name, :slug, :description, :campaignType, :goalAmountMinor, :currency, :startDate, :endDate, 'DRAFT', :now, :now)
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("name", name)
            .param("slug", slug)
            .param("description", description)
            .param("campaignType", campaignType.name)
            .param("goalAmountMinor", goalAmountMinor)
            .param("currency", currency)
            .param("startDate", startDate?.let { Date.valueOf(it) })
            .param("endDate", endDate?.let { Date.valueOf(it) })
            .param("now", Timestamp.from(now))
            .update()
        return Campaign(
            id = id,
            organizationId = organizationId,
            teamId = teamId,
            name = name,
            slug = slug,
            description = description,
            campaignType = campaignType,
            goalAmountMinor = goalAmountMinor,
            currency = currency,
            startDate = startDate,
            endDate = endDate,
            status = CampaignStatus.DRAFT,
            publishedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        id: UUID,
        organizationId: UUID,
        name: String?,
        description: String?,
        goalAmountMinor: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update campaign
            set name              = coalesce(:name, name),
                description       = coalesce(:description, description),
                goal_amount_minor = coalesce(:goalAmountMinor, goal_amount_minor),
                start_date        = coalesce(:startDate, start_date),
                end_date          = coalesce(:endDate, end_date),
                updated_at        = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("name", name)
            .param("description", description)
            .param("goalAmountMinor", goalAmountMinor)
            .param("startDate", startDate?.let { Date.valueOf(it) })
            .param("endDate", endDate?.let { Date.valueOf(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun updateStatus(id: UUID, organizationId: UUID, status: CampaignStatus, publishedAt: Instant?): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update campaign
            set status = :status, published_at = coalesce(:publishedAt, published_at), updated_at = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("status", status.name)
            .param("publishedAt", publishedAt?.let { Timestamp.from(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Campaign =
        Campaign(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java),
            name = rs.getString("name"),
            slug = rs.getString("slug"),
            description = rs.getString("description"),
            campaignType = CampaignType.valueOf(rs.getString("campaign_type")),
            goalAmountMinor = rs.getLong("goal_amount_minor"),
            currency = rs.getString("currency"),
            startDate = rs.getDate("start_date")?.toLocalDate(),
            endDate = rs.getDate("end_date")?.toLocalDate(),
            status = CampaignStatus.valueOf(rs.getString("status")),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
