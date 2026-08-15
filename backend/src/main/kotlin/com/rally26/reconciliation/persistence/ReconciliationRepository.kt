package com.rally26.reconciliation.persistence

import com.rally26.reconciliation.domain.NewReconciliationIssue
import com.rally26.reconciliation.domain.ReconciliationIssue
import com.rally26.reconciliation.domain.ReconciliationRun
import com.rally26.reconciliation.domain.ReconciliationRunStatus
import com.rally26.reconciliation.domain.ReconciliationSeverity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val RUN_COLUMNS =
    "id, organization_id, status, issue_count, high_count, medium_count, low_count, started_by_user_id, started_at, completed_at"
private const val ISSUE_COLUMNS =
    "id, reconciliation_run_id, organization_id, issue_type, severity, resource_type, resource_id, title, detail, action_path, created_at"

@Repository
class ReconciliationRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insertRun(
        organizationId: UUID,
        startedByUserId: UUID,
    ): ReconciliationRun {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into reconciliation_run
                    (id, organization_id, status, started_by_user_id, started_at)
                values (:id, :organizationId, 'RUNNING', :startedByUserId, :startedAt)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("startedByUserId", startedByUserId)
            .param("startedAt", Timestamp.from(now))
            .update()
        return ReconciliationRun(id, organizationId, ReconciliationRunStatus.RUNNING, 0, 0, 0, 0, startedByUserId, now, null)
    }

    fun completeRun(
        runId: UUID,
        organizationId: UUID,
        high: Int,
        medium: Int,
        low: Int,
    ): ReconciliationRun {
        val completedAt = Instant.now()
        jdbcClient
            .sql(
                """
                update reconciliation_run
                set status = 'COMPLETED', issue_count = :issueCount, high_count = :highCount,
                    medium_count = :mediumCount, low_count = :lowCount, completed_at = :completedAt
                where id = :runId and organization_id = :organizationId
                """.trimIndent(),
            ).param("issueCount", high + medium + low)
            .param("highCount", high)
            .param("mediumCount", medium)
            .param("lowCount", low)
            .param("completedAt", Timestamp.from(completedAt))
            .param("runId", runId)
            .param("organizationId", organizationId)
            .update()
        return findRun(organizationId, runId) ?: error("Reconciliation run disappeared after completion.")
    }

    fun findRun(
        organizationId: UUID,
        runId: UUID,
    ): ReconciliationRun? =
        jdbcClient
            .sql(
                "select $RUN_COLUMNS from reconciliation_run where organization_id = :organizationId and id = :runId",
            ).param("organizationId", organizationId)
            .param("runId", runId)
            .query(::mapRun)
            .optional()
            .orElse(null)

    fun latestRun(organizationId: UUID): ReconciliationRun? =
        jdbcClient
            .sql(
                "select $RUN_COLUMNS from reconciliation_run where organization_id = :organizationId order by started_at desc limit 1",
            ).param("organizationId", organizationId)
            .query(::mapRun)
            .optional()
            .orElse(null)

    fun listRuns(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<ReconciliationRun> =
        jdbcClient
            .sql(
                "select $RUN_COLUMNS from reconciliation_run where organization_id = :organizationId order by started_at desc offset :offset limit :limit",
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRun)
            .list()

    fun countRuns(organizationId: UUID): Long =
        jdbcClient
            .sql(
                "select count(*) from reconciliation_run where organization_id = :organizationId",
            ).param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun searchRuns(
        organizationId: UUID,
        status: ReconciliationRunStatus?,
        ascending: Boolean,
        offset: Int,
        limit: Int,
    ): List<ReconciliationRun> {
        val sql =
            buildString {
                append("select $RUN_COLUMNS from reconciliation_run where organization_id = :organizationId")
                if (status != null) append(" and status = :status")
                append(" order by started_at ${if (ascending) "asc" else "desc"} offset :offset limit :limit")
            }
        var statement =
            jdbcClient
                .sql(sql)
                .param("organizationId", organizationId)
                .param("offset", offset)
                .param("limit", limit)
        if (status != null) statement = statement.param("status", status.name)
        return statement.query(::mapRun).list()
    }

    fun countRunsFiltered(
        organizationId: UUID,
        status: ReconciliationRunStatus?,
    ): Long {
        val sql =
            buildString {
                append("select count(*) from reconciliation_run where organization_id = :organizationId")
                if (status != null) append(" and status = :status")
            }
        var statement = jdbcClient.sql(sql).param("organizationId", organizationId)
        if (status != null) statement = statement.param("status", status.name)
        return statement.query(Long::class.java).single()
    }

    fun insertIssue(
        runId: UUID,
        organizationId: UUID,
        issue: NewReconciliationIssue,
    ): ReconciliationIssue {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into reconciliation_issue
                    (id, reconciliation_run_id, organization_id, issue_type, severity, resource_type,
                     resource_id, title, detail, action_path, created_at)
                values (:id, :runId, :organizationId, :issueType, :severity, :resourceType,
                        :resourceId, :title, :detail, :actionPath, :createdAt)
                """.trimIndent(),
            ).param("id", id)
            .param("runId", runId)
            .param("organizationId", organizationId)
            .param("issueType", issue.issueType)
            .param("severity", issue.severity.name)
            .param("resourceType", issue.resourceType)
            .param("resourceId", issue.resourceId)
            .param("title", issue.title)
            .param("detail", issue.detail)
            .param("actionPath", issue.actionPath)
            .param("createdAt", Timestamp.from(now))
            .update()
        return ReconciliationIssue(
            id,
            runId,
            organizationId,
            issue.issueType,
            issue.severity,
            issue.resourceType,
            issue.resourceId,
            issue.title,
            issue.detail,
            issue.actionPath,
            now,
        )
    }

    fun listIssues(
        organizationId: UUID,
        runId: UUID,
    ): List<ReconciliationIssue> =
        jdbcClient
            .sql(
                "select $ISSUE_COLUMNS from reconciliation_issue where organization_id = :organizationId and reconciliation_run_id = :runId order by case severity when 'HIGH' then 0 when 'MEDIUM' then 1 else 2 end, created_at",
            ).param("organizationId", organizationId)
            .param("runId", runId)
            .query(::mapIssue)
            .list()

    fun listIssuesPaged(
        organizationId: UUID,
        runId: UUID,
        query: String?,
        severity: ReconciliationSeverity?,
        resourceType: String?,
        ascending: Boolean,
        offset: Int,
        limit: Int,
    ): List<ReconciliationIssue> {
        val sql =
            buildString {
                append(
                    "select $ISSUE_COLUMNS from reconciliation_issue" +
                        " where organization_id = :organizationId and reconciliation_run_id = :runId",
                )
                if (query != null) {
                    append(
                        " and (lower(title) like :query or lower(detail) like :query" +
                            " or lower(issue_type) like :query or lower(coalesce(cast(resource_id as text), '')) like :query)",
                    )
                }
                if (severity != null) append(" and severity = :severity")
                if (resourceType != null) append(" and resource_type = :resourceType")
                append(" order by created_at ${if (ascending) "asc" else "desc"} offset :offset limit :limit")
            }
        var statement =
            jdbcClient
                .sql(sql)
                .param("organizationId", organizationId)
                .param("runId", runId)
                .param("offset", offset)
                .param("limit", limit)
        if (query != null) statement = statement.param("query", "%${query.lowercase()}%")
        if (severity != null) statement = statement.param("severity", severity.name)
        if (resourceType != null) statement = statement.param("resourceType", resourceType)
        return statement.query(::mapIssue).list()
    }

    fun countIssues(
        organizationId: UUID,
        runId: UUID,
        query: String?,
        severity: ReconciliationSeverity?,
        resourceType: String?,
    ): Long {
        val sql =
            buildString {
                append(
                    "select count(*) from reconciliation_issue" +
                        " where organization_id = :organizationId and reconciliation_run_id = :runId",
                )
                if (query != null) {
                    append(
                        " and (lower(title) like :query or lower(detail) like :query" +
                            " or lower(issue_type) like :query or lower(coalesce(cast(resource_id as text), '')) like :query)",
                    )
                }
                if (severity != null) append(" and severity = :severity")
                if (resourceType != null) append(" and resource_type = :resourceType")
            }
        var statement =
            jdbcClient
                .sql(sql)
                .param("organizationId", organizationId)
                .param("runId", runId)
        if (query != null) statement = statement.param("query", "%${query.lowercase()}%")
        if (severity != null) statement = statement.param("severity", severity.name)
        if (resourceType != null) statement = statement.param("resourceType", resourceType)
        return statement.query(Long::class.java).single()
    }

    fun countLatestIssues(organizationId: UUID): Long =
        jdbcClient
            .sql(
                """
                select coalesce((select issue_count from reconciliation_run
                                 where organization_id = :organizationId and status = 'COMPLETED'
                                 order by started_at desc limit 1), 0)
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun detectIssues(organizationId: UUID): List<NewReconciliationIssue> =
        buildList {
            addAll(pendingOfflineRecords(organizationId))
            addAll(missingProviderReferences(organizationId))
            addAll(missingLedgerEntries(organizationId))
            addAll(missingFulfillments(organizationId))
            addAll(fulfillmentExceptions(organizationId))
            addAll(overdueInstallments(organizationId))
            addAll(paymentPlanBalanceMismatches(organizationId))
        }

    private fun pendingOfflineRecords(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select id, record_type, display_label
                from offline_financial_record
                where organization_id = :organizationId and verification_status = 'PENDING_VERIFICATION'
                order by received_at
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                val id = rs.getObject("id", UUID::class.java)
                NewReconciliationIssue(
                    "OFFLINE_PENDING_VERIFICATION",
                    ReconciliationSeverity.HIGH,
                    "OFFLINE_FINANCIAL_RECORD",
                    id,
                    "Offline transaction needs verification",
                    rs.getString("display_label"),
                    "/app/organizations/$organizationId/financial-operations",
                )
            }.list()

    private fun missingProviderReferences(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select id, resource_type from (
                    select id, 'CONTRIBUTION'::text resource_type from contribution
                     where organization_id = :organizationId and status = 'CONFIRMED' and payment_source = 'STRIPE' and stripe_payment_intent_id is null
                    union all
                    select id, 'SPONSORSHIP'::text from sponsorship
                     where organization_id = :organizationId and status = 'CONFIRMED' and payment_source = 'STRIPE' and stripe_payment_intent_id is null
                    union all
                    select id, 'ORDER'::text from "order"
                     where organization_id = :organizationId and status = 'CONFIRMED' and payment_source = 'STRIPE' and stripe_payment_intent_id is null
                ) missing
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                val type = rs.getString("resource_type")
                val id = rs.getObject("id", UUID::class.java)
                NewReconciliationIssue(
                    "MISSING_PROVIDER_REFERENCE",
                    ReconciliationSeverity.HIGH,
                    type,
                    id,
                    "Confirmed $type is missing a Stripe payment reference",
                    "The record is confirmed as Stripe-processed but has no payment-intent identifier.",
                    "/app/organizations/$organizationId/financial-operations",
                )
            }.list()

    private fun missingLedgerEntries(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select source_id, source_type from (
                    select c.id source_id, 'CONTRIBUTION'::text source_type
                    from contribution c
                    where c.organization_id = :organizationId and c.status = 'CONFIRMED'
                      and not exists (select 1 from ledger_entry l where l.organization_id = c.organization_id and l.source_type = 'CONTRIBUTION' and l.source_id = c.id)
                    union all
                    select s.id, 'SPONSORSHIP'::text
                    from sponsorship s
                    where s.organization_id = :organizationId and s.status = 'CONFIRMED'
                      and not exists (select 1 from ledger_entry l where l.organization_id = s.organization_id and l.source_type = 'SPONSORSHIP' and l.source_id = s.id)
                    union all
                    select o.id, 'ORDER'::text
                    from "order" o
                    where o.organization_id = :organizationId and o.status = 'CONFIRMED'
                      and not exists (select 1 from ledger_entry l where l.organization_id = o.organization_id and l.source_type = 'ORDER' and l.source_id = o.id)
                ) missing
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                val type = rs.getString("source_type")
                val id = rs.getObject("source_id", UUID::class.java)
                NewReconciliationIssue(
                    "MISSING_LEDGER_ENTRIES",
                    ReconciliationSeverity.HIGH,
                    type,
                    id,
                    "$type is missing ledger entries",
                    "The confirmed source record has no append-only ledger rows.",
                    "/app/organizations/$organizationId/financial-operations",
                )
            }.list()

    private fun missingFulfillments(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select o.id
                from "order" o
                left join fulfillment f on f.order_id = o.id
                where o.organization_id = :organizationId and o.status = 'CONFIRMED' and f.id is null
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                val id = rs.getObject("id", UUID::class.java)
                NewReconciliationIssue(
                    "MISSING_FULFILLMENT",
                    ReconciliationSeverity.HIGH,
                    "ORDER",
                    id,
                    "Confirmed order has no fulfillment record",
                    "Create or repair the fulfillment record before processing the order.",
                    "/app/organizations/$organizationId/stores",
                )
            }.list()

    private fun fulfillmentExceptions(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select f.id, f.order_id, f.status, coalesce(f.attention_reason, f.last_error, 'Fulfillment needs review') detail
                from fulfillment f join "order" o on o.id = f.order_id
                where o.organization_id = :organizationId and f.status in ('FAILED', 'NEEDS_ATTENTION')
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                NewReconciliationIssue(
                    "FULFILLMENT_EXCEPTION",
                    ReconciliationSeverity.HIGH,
                    "FULFILLMENT",
                    rs.getObject("id", UUID::class.java),
                    "Fulfillment ${rs.getString("status")} requires attention",
                    rs.getString("detail"),
                    "/app/organizations/$organizationId/stores",
                )
            }.list()

    private fun overdueInstallments(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select i.id, plan.household_id, i.amount_minor,
                       coalesce(sum(case when p.voided_at is null then a.amount_minor else 0 end), 0) paid_minor,
                       i.due_date
                from fee_installment i
                join fee_payment_plan plan on plan.id = i.payment_plan_id and plan.status = 'ACTIVE'
                left join fee_payment_installment_allocation a on a.installment_id = i.id
                left join fee_payment p on p.id = a.fee_payment_id
                where i.organization_id = :organizationId and i.due_date < current_date
                group by i.id, plan.household_id
                having coalesce(sum(case when p.voided_at is null then a.amount_minor else 0 end), 0) < i.amount_minor
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                val remaining = rs.getLong("amount_minor") - rs.getLong("paid_minor")
                val householdId = rs.getObject("household_id", UUID::class.java)
                NewReconciliationIssue(
                    "OVERDUE_INSTALLMENT",
                    ReconciliationSeverity.MEDIUM,
                    "FEE_INSTALLMENT",
                    rs.getObject("id", UUID::class.java),
                    "Payment-plan installment is overdue",
                    "$remaining minor units remained due on ${rs.getDate("due_date").toLocalDate()}.",
                    "/app/organizations/$organizationId/households/$householdId/fees",
                )
            }.list()

    private fun paymentPlanBalanceMismatches(organizationId: UUID): List<NewReconciliationIssue> =
        jdbcClient
            .sql(
                """
                select plan.id, plan.household_id, plan.total_minor,
                       greatest(0, fa.original_amount_minor
                         - coalesce((select sum(p.amount_minor) from fee_payment p where p.fee_assignment_id = fa.id and p.voided_at is null), 0)
                         - coalesce((select sum(a.amount_minor) from fee_adjustment a where a.fee_assignment_id = fa.id and a.voided_at is null), 0)) current_balance,
                       greatest(0, plan.total_minor
                         - coalesce((select sum(a.amount_minor) from fee_payment_installment_allocation a join fee_payment p on p.id = a.fee_payment_id and p.voided_at is null where a.payment_plan_id = plan.id), 0)) plan_remaining
                from fee_payment_plan plan
                join fee_assignment fa on fa.id = plan.fee_assignment_id
                where plan.organization_id = :organizationId and plan.status = 'ACTIVE'
                  and greatest(0, fa.original_amount_minor
                         - coalesce((select sum(p.amount_minor) from fee_payment p where p.fee_assignment_id = fa.id and p.voided_at is null), 0)
                         - coalesce((select sum(a.amount_minor) from fee_adjustment a where a.fee_assignment_id = fa.id and a.voided_at is null), 0))
                      <> greatest(0, plan.total_minor
                         - coalesce((select sum(a.amount_minor) from fee_payment_installment_allocation a join fee_payment p on p.id = a.fee_payment_id and p.voided_at is null where a.payment_plan_id = plan.id), 0))
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                val householdId = rs.getObject("household_id", UUID::class.java)
                NewReconciliationIssue(
                    "PAYMENT_PLAN_BALANCE_MISMATCH",
                    ReconciliationSeverity.HIGH,
                    "FEE_PAYMENT_PLAN",
                    rs.getObject("id", UUID::class.java),
                    "Payment plan does not match the fee balance",
                    "The fee balance is ${rs.getLong(
                        "current_balance",
                    )} minor units while the plan shows ${rs.getLong("plan_remaining")} remaining.",
                    "/app/organizations/$organizationId/households/$householdId/fees",
                )
            }.list()

    private fun mapRun(
        rs: ResultSet,
        rowNum: Int,
    ) = ReconciliationRun(
        rs.getObject("id", UUID::class.java),
        rs.getObject("organization_id", UUID::class.java),
        ReconciliationRunStatus.valueOf(rs.getString("status")),
        rs.getInt("issue_count"),
        rs.getInt("high_count"),
        rs.getInt("medium_count"),
        rs.getInt("low_count"),
        rs.getObject("started_by_user_id", UUID::class.java),
        rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("completed_at")?.toInstant(),
    )

    private fun mapIssue(
        rs: ResultSet,
        rowNum: Int,
    ) = ReconciliationIssue(
        rs.getObject("id", UUID::class.java),
        rs.getObject("reconciliation_run_id", UUID::class.java),
        rs.getObject("organization_id", UUID::class.java),
        rs.getString("issue_type"),
        ReconciliationSeverity.valueOf(rs.getString("severity")),
        rs.getString("resource_type"),
        rs.getObject("resource_id", UUID::class.java),
        rs.getString("title"),
        rs.getString("detail"),
        rs.getString("action_path"),
        rs.getTimestamp("created_at").toInstant(),
    )
}
