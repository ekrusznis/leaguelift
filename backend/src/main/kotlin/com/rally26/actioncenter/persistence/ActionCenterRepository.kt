package com.rally26.actioncenter.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Repository
class ActionCenterRepository(private val jdbcClient: JdbcClient) {

    fun countPendingCorrections(organizationId: UUID): Long = jdbcClient.sql(
        "select count(*) from profile_correction_request where organization_id = :organizationId and status = 'PENDING'",
    ).param("organizationId", organizationId).query(Long::class.java).single()

    fun countOverdueFees(organizationId: UUID): Long = jdbcClient.sql(
        """
        select count(*) from fee_assignment fa
        where fa.organization_id = :organizationId
          and fa.status in ('OPEN', 'PARTIALLY_PAID')
          and fa.due_date is not null and fa.due_date < current_date
          and not exists (
              select 1 from fee_payment_plan plan
              where plan.fee_assignment_id = fa.id and plan.status = 'ACTIVE'
          )
          and greatest(0, fa.original_amount_minor
                - coalesce((select sum(fp.amount_minor) from fee_payment fp where fp.fee_assignment_id = fa.id and fp.voided_at is null), 0)
                - coalesce((select sum(adj.amount_minor) from fee_adjustment adj where adj.fee_assignment_id = fa.id and adj.voided_at is null), 0)) > 0
        """.trimIndent(),
    ).param("organizationId", organizationId).query(Long::class.java).single()

    fun countFulfillmentExceptions(organizationId: UUID): Long = jdbcClient.sql(
        """
        select count(*) from fulfillment f
        join "order" o on o.id = f.order_id
        where o.organization_id = :organizationId and f.status in ('FAILED', 'NEEDS_ATTENTION')
        """.trimIndent(),
    ).param("organizationId", organizationId).query(Long::class.java).single()

    fun countPendingOfflineFinancialRecords(organizationId: UUID): Long = jdbcClient.sql(
        "select count(*) from offline_financial_record where organization_id = :organizationId and verification_status = 'PENDING_VERIFICATION'",
    ).param("organizationId", organizationId).query(Long::class.java).single()

    fun countOverdueInstallments(organizationId: UUID): Long = jdbcClient.sql(
        """
        select count(*)
        from fee_installment i
        join fee_payment_plan plan on plan.id = i.payment_plan_id and plan.status = 'ACTIVE'
        left join (
            select a.installment_id, sum(a.amount_minor) paid_minor
            from fee_payment_installment_allocation a
            join fee_payment p on p.id = a.fee_payment_id and p.voided_at is null
            group by a.installment_id
        ) paid on paid.installment_id = i.id
        where i.organization_id = :organizationId and i.due_date < current_date
          and coalesce(paid.paid_minor, 0) < i.amount_minor
        """.trimIndent(),
    ).param("organizationId", organizationId).query(Long::class.java).single()

    fun countLatestReconciliationIssues(organizationId: UUID): Long = jdbcClient.sql(
        """
        select coalesce((select issue_count from reconciliation_run
                         where organization_id = :organizationId and status = 'COMPLETED'
                         order by started_at desc limit 1), 0)
        """.trimIndent(),
    ).param("organizationId", organizationId).query(Long::class.java).single()

    fun countReviewableEvents(organizationId: UUID, teamId: UUID? = null, tournamentId: UUID? = null): Long {
        val sql = buildString {
            append("select count(*) from event where organization_id = :organizationId and status in ('DRAFT', 'TENTATIVE')")
            if (teamId != null) append(" and team_id = :teamId")
            if (tournamentId != null) append(" and tournament_id = :tournamentId")
        }
        var statement = jdbcClient.sql(sql).param("organizationId", organizationId)
        if (teamId != null) statement = statement.param("teamId", teamId)
        if (tournamentId != null) statement = statement.param("tournamentId", tournamentId)
        return statement.query(Long::class.java).single()
    }

    fun listGuardianFeeActions(userId: UUID, limit: Int): List<GuardianFeeAction> = jdbcClient.sql(
        """
        select fee.id, fee.organization_id, fee.household_id, fee.description, fee.currency,
               fee.due_date, fee.balance_minor, fee.created_at
        from (
            select distinct on (fa.id)
                   fa.id, fa.organization_id, fa.household_id, fa.description, fa.currency, fa.due_date,
                   greatest(0, fa.original_amount_minor
                        - coalesce((select sum(fp.amount_minor) from fee_payment fp where fp.fee_assignment_id = fa.id and fp.voided_at is null), 0)
                        - coalesce((select sum(adj.amount_minor) from fee_adjustment adj where adj.fee_assignment_id = fa.id and adj.voided_at is null), 0)) as balance_minor,
                   fa.created_at
            from guardian_relationship gr
            join fee_assignment fa on fa.household_id = gr.household_id and fa.organization_id = gr.organization_id
            where gr.user_id = :userId and gr.status = 'ACTIVE'
              and fa.status in ('OPEN', 'PARTIALLY_PAID')
              and fa.due_date is not null and fa.due_date <= current_date + 7
            order by fa.id, fa.due_date asc, fa.created_at asc
        ) fee
        where fee.balance_minor > 0
        order by fee.due_date asc, fee.created_at asc
        limit :limit
        """.trimIndent(),
    )
        .param("userId", userId).param("limit", limit)
        .query { rs, _ ->
            GuardianFeeAction(
                id = rs.getObject("id", UUID::class.java),
                organizationId = rs.getObject("organization_id", UUID::class.java),
                householdId = rs.getObject("household_id", UUID::class.java),
                description = rs.getString("description"),
                currency = rs.getString("currency"),
                dueDate = rs.getObject("due_date", LocalDate::class.java),
                balanceMinor = rs.getLong("balance_minor"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }.list()

    fun listGuardianDocumentActions(userId: UUID, limit: Int): List<GuardianDocumentAction> = jdbcClient.sql(
        """
        select ma.id, ma.organization_id, ma.entity_id as household_id,
               coalesce(nullif(trim(ma.alt_text), ''), 'Document') as title,
               ma.created_at
        from guardian_relationship gr
        join media_assignment ma
          on ma.entity_type = 'HOUSEHOLD' and ma.entity_id = gr.household_id
         and ma.organization_id = gr.organization_id and ma.usage_slot = 'DOCUMENT'
         and ma.publication_status <> 'RETIRED'
        left join document_acknowledgment da
          on da.media_assignment_id = ma.id and da.household_adult_id = gr.household_adult_id
        where gr.user_id = :userId and gr.status = 'ACTIVE' and da.id is null
        order by ma.created_at asc
        limit :limit
        """.trimIndent(),
    )
        .param("userId", userId).param("limit", limit)
        .query { rs, _ ->
            GuardianDocumentAction(
                assignmentId = rs.getObject("id", UUID::class.java),
                organizationId = rs.getObject("organization_id", UUID::class.java),
                householdId = rs.getObject("household_id", UUID::class.java),
                title = rs.getString("title"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }.list()

    fun listGuardianRsvpActions(userId: UUID, limit: Int): List<RsvpAction> = jdbcClient.sql(
        """
        select distinct e.id as event_id, e.organization_id, gr.household_id, p.id as participant_id,
               trim(p.first_name || ' ' || p.last_name) as participant_name,
               coalesce(nullif(trim(e.title), ''), initcap(replace(e.event_type, '_', ' '))) as event_title,
               e.start_at, e.created_at
        from guardian_relationship gr
        join participant p on p.household_id = gr.household_id and p.organization_id = gr.organization_id and p.status = 'ACTIVE'
        join participant_team pt on pt.participant_id = p.id and pt.organization_id = p.organization_id and pt.status = 'ACTIVE'
        join event e on e.organization_id = p.organization_id and e.team_id = pt.team_id
        left join event_rsvp er on er.event_id = e.id and er.participant_id = p.id
        where gr.user_id = :userId and gr.status = 'ACTIVE'
          and e.status in ('TENTATIVE', 'SCHEDULED', 'DELAYED', 'POSTPONED')
          and e.start_at between now() and now() + interval '14 days'
          and er.id is null
        order by e.start_at asc
        limit :limit
        """.trimIndent(),
    )
        .param("userId", userId).param("limit", limit)
        .query(::mapRsvp).list()

    fun listAthleteRsvpActions(userId: UUID, limit: Int): List<RsvpAction> = jdbcClient.sql(
        """
        select e.id as event_id, e.organization_id, p.household_id, p.id as participant_id,
               trim(p.first_name || ' ' || p.last_name) as participant_name,
               coalesce(nullif(trim(e.title), ''), initcap(replace(e.event_type, '_', ' '))) as event_title,
               e.start_at, e.created_at
        from role_assignment ra
        join participant p on p.id = ra.resource_id and p.organization_id = ra.organization_id and p.status = 'ACTIVE'
        join participant_team pt on pt.participant_id = p.id and pt.organization_id = p.organization_id and pt.status = 'ACTIVE'
        join event e on e.organization_id = p.organization_id and e.team_id = pt.team_id
        left join event_rsvp er on er.event_id = e.id and er.participant_id = p.id
        where ra.user_id = :userId and ra.context_type = 'PARTICIPANT' and ra.status = 'ACTIVE'
          and e.status in ('TENTATIVE', 'SCHEDULED', 'DELAYED', 'POSTPONED')
          and e.start_at between now() and now() + interval '14 days'
          and er.id is null
        order by e.start_at asc
        limit :limit
        """.trimIndent(),
    )
        .param("userId", userId).param("limit", limit)
        .query(::mapRsvp).list()

    fun listSupportCaseActions(userId: UUID, limit: Int): List<SupportCaseAction> = jdbcClient.sql(
        """
        select id, subject, created_at
        from support_case
        where requester_user_id = :userId and status = 'WAITING_ON_CUSTOMER'
        order by updated_at desc
        limit :limit
        """.trimIndent(),
    )
        .param("userId", userId).param("limit", limit)
        .query { rs, _ -> SupportCaseAction(rs.getObject("id", UUID::class.java), rs.getString("subject"), rs.getTimestamp("created_at").toInstant()) }
        .list()

    fun countOpenPrioritySupportCases(): Long = jdbcClient.sql(
        "select count(*) from support_case where status in ('OPEN', 'IN_PROGRESS') and priority in ('HIGH', 'URGENT')",
    ).query(Long::class.java).single()

    fun countFailedDeliveries(): Long = jdbcClient.sql(
        """
        select (select count(*) from outbox_event where status in ('FAILED', 'DEAD_LETTER'))
             + (select count(*) from announcement_recipient where email_status = 'FAILED' or sms_status = 'FAILED')
        """.trimIndent(),
    ).query(Long::class.java).single()

    private fun mapRsvp(rs: java.sql.ResultSet, _rowNum: Int) = RsvpAction(
        eventId = rs.getObject("event_id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        householdId = rs.getObject("household_id", UUID::class.java),
        participantId = rs.getObject("participant_id", UUID::class.java),
        participantName = rs.getString("participant_name"),
        eventTitle = rs.getString("event_title"),
        startAt = rs.getTimestamp("start_at").toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}

data class GuardianFeeAction(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val description: String,
    val currency: String,
    val dueDate: LocalDate,
    val balanceMinor: Long,
    val createdAt: Instant,
) {
    val dueAt: Instant get() = dueDate.atStartOfDay().toInstant(ZoneOffset.UTC)
}

data class GuardianDocumentAction(
    val assignmentId: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val title: String,
    val createdAt: Instant,
)

data class RsvpAction(
    val eventId: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val participantId: UUID,
    val participantName: String,
    val eventTitle: String,
    val startAt: Instant,
    val createdAt: Instant,
)

data class SupportCaseAction(val id: UUID, val subject: String, val createdAt: Instant)
