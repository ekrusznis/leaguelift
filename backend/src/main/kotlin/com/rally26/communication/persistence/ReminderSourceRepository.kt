package com.rally26.communication.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class ReminderSourceRepository(private val jdbcClient: JdbcClient) {
    fun findCampaign(organizationId: UUID, id: UUID): CampaignReminderSource? = jdbcClient.sql(
        """
        select id, organization_id, team_id, name, slug, status
        from campaign where id = :id and organization_id = :organizationId
        """.trimIndent(),
    ).param("id", id).param("organizationId", organizationId).query { rs, _ ->
        CampaignReminderSource(
            rs.getObject("id", UUID::class.java), rs.getObject("organization_id", UUID::class.java),
            rs.getObject("team_id", UUID::class.java), rs.getString("name"), rs.getString("slug"), rs.getString("status"),
        )
    }.optional().orElse(null)

    fun findEvent(organizationId: UUID, id: UUID): EventReminderSource? = jdbcClient.sql(
        """
        select id, organization_id, team_id, tournament_id,
               coalesce(nullif(trim(title), ''), initcap(replace(event_type, '_', ' '))) as display_title,
               status, start_at, timezone, venue_name
        from event where id = :id and organization_id = :organizationId
        """.trimIndent(),
    ).param("id", id).param("organizationId", organizationId).query { rs, _ ->
        EventReminderSource(
            id = rs.getObject("id", UUID::class.java), organizationId = rs.getObject("organization_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java), tournamentId = rs.getObject("tournament_id", UUID::class.java),
            title = rs.getString("display_title"), status = rs.getString("status"),
            startAt = rs.getTimestamp("start_at")?.toInstant(), timezone = rs.getString("timezone"), venueName = rs.getString("venue_name"),
        )
    }.optional().orElse(null)

    fun findFee(organizationId: UUID, id: UUID): FeeReminderSource? = jdbcClient.sql(
        """
        select fa.id, fa.organization_id, fa.household_id, fa.description, fa.due_date, fa.currency,
               greatest(0, fa.original_amount_minor
                    - coalesce((select sum(fp.amount_minor) from fee_payment fp where fp.fee_assignment_id = fa.id and fp.voided_at is null), 0)
                    - coalesce((select sum(adj.amount_minor) from fee_adjustment adj where adj.fee_assignment_id = fa.id and adj.voided_at is null), 0)) as balance_minor,
               fa.status
        from fee_assignment fa where fa.id = :id and fa.organization_id = :organizationId
        """.trimIndent(),
    ).param("id", id).param("organizationId", organizationId).query { rs, _ ->
        FeeReminderSource(
            id = rs.getObject("id", UUID::class.java), organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java), description = rs.getString("description"),
            dueDate = rs.getObject("due_date", LocalDate::class.java), currency = rs.getString("currency"),
            balanceMinor = rs.getLong("balance_minor"), status = rs.getString("status"),
        )
    }.optional().orElse(null)

    fun findDocument(organizationId: UUID, id: UUID): DocumentReminderSource? = jdbcClient.sql(
        """
        select ma.id, ma.organization_id, ma.entity_id as household_id,
               coalesce(nullif(trim(ma.alt_text), ''), 'Document') as title
        from media_assignment ma
        where ma.id = :id and ma.organization_id = :organizationId
          and ma.entity_type = 'HOUSEHOLD' and ma.usage_slot = 'DOCUMENT' and ma.publication_status <> 'RETIRED'
        """.trimIndent(),
    ).param("id", id).param("organizationId", organizationId).query { rs, _ ->
        DocumentReminderSource(
            id = rs.getObject("id", UUID::class.java), organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java), title = rs.getString("title"),
        )
    }.optional().orElse(null)
}

data class CampaignReminderSource(val id: UUID, val organizationId: UUID, val teamId: UUID?, val name: String, val slug: String, val status: String)
data class EventReminderSource(
    val id: UUID, val organizationId: UUID, val teamId: UUID?, val tournamentId: UUID?, val title: String,
    val status: String, val startAt: Instant?, val timezone: String, val venueName: String?,
)
data class FeeReminderSource(
    val id: UUID, val organizationId: UUID, val householdId: UUID, val description: String, val dueDate: LocalDate?,
    val currency: String, val balanceMinor: Long, val status: String,
)
data class DocumentReminderSource(val id: UUID, val organizationId: UUID, val householdId: UUID, val title: String)
