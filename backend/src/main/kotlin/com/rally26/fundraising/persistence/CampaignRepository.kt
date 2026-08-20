package com.rally26.fundraising.persistence

import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.FundraiserTemplateKey
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, team_id, name, slug, description, campaign_type, goal_amount_minor, currency, start_date, end_date, status, " +
        "event_location_name, event_address, published_at, created_by_user_id," +
        " template_key, submitted_at, approved_at, approved_by_user_id, " +
        "online_contributions_enabled, created_at, updated_at"

@Repository
class CampaignRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Campaign? =
        jdbcClient
            .sql("select $COLUMNS from campaign where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Most recently created active campaign for a team — used by the owner dashboard's team-performance row. */
    fun findActiveByTeam(
        teamId: UUID,
        organizationId: UUID,
    ): Campaign? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from campaign
                where team_id = :teamId and organization_id = :organizationId and status = 'ACTIVE'
                order by created_at desc
                limit 1
                """.trimIndent(),
            ).param("teamId", teamId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findBySlug(slug: String): Campaign? =
        jdbcClient
            .sql("select $COLUMNS from campaign where lower(slug) = lower(:slug)")
            .param("slug", slug)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAll(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Campaign> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from campaign
                where organization_id = :organizationId
                order by created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient
            .sql("select count(*) from campaign where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    /** Non-terminal campaigns only (DRAFT/PENDING_APPROVAL/SCHEDULED/ACTIVE) — used by the FREE/Starter "1 concurrent campaign" plan cap, unlike [countAll]'s all-time count. */
    fun countActive(organizationId: UUID): Long =
        jdbcClient
            .sql(
                """
                select count(*) from campaign
                where organization_id = :organizationId
                  and status in ('DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'ACTIVE')
                """.trimIndent(),
            ).param("organizationId", organizationId)
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
        createdByUserId: UUID?,
        templateKey: FundraiserTemplateKey?,
        eventLocationName: String? = null,
        eventAddress: String? = null,
    ): Campaign {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into campaign (
                    id, organization_id, team_id, name, slug, description, campaign_type,
                    goal_amount_minor, currency, start_date, end_date, event_location_name, event_address, status,
                    created_by_user_id, template_key, created_at, updated_at
                ) values (
                    :id, :organizationId, :teamId, :name, :slug, :description, :campaignType,
                    :goalAmountMinor, :currency, :startDate, :endDate, :eventLocationName, :eventAddress, 'DRAFT',
                    :createdByUserId, :templateKey, :now, :now
                )
                """.trimIndent(),
            ).param("id", id)
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
            .param("eventLocationName", eventLocationName)
            .param("eventAddress", eventAddress)
            .param("createdByUserId", createdByUserId)
            .param("templateKey", templateKey?.name)
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
            eventLocationName = eventLocationName,
            eventAddress = eventAddress,
            status = CampaignStatus.DRAFT,
            publishedAt = null,
            createdByUserId = createdByUserId,
            templateKey = templateKey,
            submittedAt = null,
            approvedAt = null,
            approvedByUserId = null,
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
        eventLocationName: String? = null,
        eventAddress: String? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update campaign
                set name              = coalesce(:name, name),
                    description       = coalesce(:description, description),
                    goal_amount_minor = coalesce(:goalAmountMinor, goal_amount_minor),
                    start_date        = coalesce(:startDate, start_date),
                    end_date          = coalesce(:endDate, end_date),
                    event_location_name = coalesce(:eventLocationName, event_location_name),
                    event_address       = coalesce(:eventAddress, event_address),
                    updated_at        = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("name", name)
            .param("description", description)
            .param("goalAmountMinor", goalAmountMinor)
            .param("startDate", startDate?.let { Date.valueOf(it) })
            .param("endDate", endDate?.let { Date.valueOf(it) })
            .param("eventLocationName", eventLocationName)
            .param("eventAddress", eventAddress)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun markPendingApproval(
        id: UUID,
        organizationId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update campaign
                set status = 'PENDING_APPROVAL',
                    submitted_at = :now,
                    approved_at = null,
                    approved_by_user_id = null,
                    updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun markActive(
        id: UUID,
        organizationId: UUID,
        approvedByUserId: UUID?,
    ): Int {
        val now = Instant.now()
        val approvedAt = approvedByUserId?.let { Timestamp.from(now) }
        return jdbcClient
            .sql(
                """
                update campaign
                set status = 'ACTIVE',
                    published_at = coalesce(published_at, :now),
                    approved_at = :approvedAt,
                    approved_by_user_id = :approvedByUserId,
                    updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("approvedAt", approvedAt)
            .param("approvedByUserId", approvedByUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun markScheduled(
        id: UUID,
        organizationId: UUID,
        approvedByUserId: UUID?,
    ): Int {
        val now = Instant.now()
        val approvedAt = approvedByUserId?.let { Timestamp.from(now) }
        return jdbcClient
            .sql(
                """
                update campaign
                set status = 'SCHEDULED',
                    published_at = coalesce(published_at, :now),
                    approved_at = :approvedAt,
                    approved_by_user_id = :approvedByUserId,
                    updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("approvedAt", approvedAt)
            .param("approvedByUserId", approvedByUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun findScheduledDue(today: LocalDate): List<Campaign> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from campaign
                where status = 'SCHEDULED'
                  and (start_date is null or start_date <= :today)
                order by start_date nulls first, created_at
                """.trimIndent(),
            ).param("today", Date.valueOf(today))
            .query(::mapRow)
            .list()

    fun findActiveReadyToEnd(today: LocalDate): List<Campaign> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from campaign
                where status = 'ACTIVE'
                  and end_date is not null
                  and end_date < :today
                order by end_date, created_at
                """.trimIndent(),
            ).param("today", Date.valueOf(today))
            .query(::mapRow)
            .list()

    fun activateScheduled(
        id: UUID,
        organizationId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update campaign
                set status = 'ACTIVE',
                    published_at = coalesce(published_at, :now),
                    updated_at = :now
                where id = :id and organization_id = :organizationId and status = 'SCHEDULED'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun markEnded(
        id: UUID,
        organizationId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update campaign
                set status = 'ENDED', updated_at = :now
                where id = :id and organization_id = :organizationId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun returnToDraft(
        id: UUID,
        organizationId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update campaign
                set status = 'DRAFT',
                    submitted_at = null,
                    approved_at = null,
                    approved_by_user_id = null,
                    updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    /** Generic terminal-state transition retained for COMPLETED/ARCHIVED. */
    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: CampaignStatus,
        publishedAt: Instant? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update campaign
                set status = :status,
                    published_at = coalesce(:publishedAt, published_at),
                    updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("status", status.name)
            .param("publishedAt", publishedAt?.let { Timestamp.from(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Campaign =
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
            eventLocationName = rs.getString("event_location_name"),
            eventAddress = rs.getString("event_address"),
            status = CampaignStatus.valueOf(rs.getString("status")),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            templateKey = rs.getString("template_key")?.let { FundraiserTemplateKey.valueOf(it) },
            submittedAt = rs.getTimestamp("submitted_at")?.toInstant(),
            approvedAt = rs.getTimestamp("approved_at")?.toInstant(),
            approvedByUserId = rs.getObject("approved_by_user_id", UUID::class.java),
            onlineContributionsEnabled = rs.getBoolean("online_contributions_enabled"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
