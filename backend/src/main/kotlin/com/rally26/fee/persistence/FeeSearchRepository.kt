package com.rally26.fee.persistence

import com.rally26.fee.domain.FeeAssignment
import com.rally26.fee.domain.FeeAssignmentSearchCriteria
import com.rally26.fee.domain.FeeAssignmentSearchSort
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.FeeAssignmentSummary
import com.rally26.fee.domain.FeeAssignmentWithBalance
import com.rally26.fee.domain.FeeBalance
import com.rally26.fee.domain.FeeTemplate
import com.rally26.fee.domain.FeeTemplateSearchCriteria
import com.rally26.fee.domain.FeeTemplateSearchSort
import com.rally26.fee.domain.FeeTemplateStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

private const val TEMPLATE_COLUMNS =
    "ft.id, ft.organization_id, ft.name, ft.description, ft.amount_minor, ft.currency, ft.status, ft.created_at, ft.updated_at"

private const val BALANCE_EXPRESSION =
    "greatest(0, fa.original_amount_minor - coalesce(fp.paid_minor, 0) - coalesce(fadj.adjusted_minor, 0))"

private const val ASSIGNMENT_SUMMARY_FROM = """
    from fee_assignment fa
    join household h on h.id = fa.household_id and h.organization_id = fa.organization_id
    left join participant p on p.id = fa.participant_id and p.organization_id = fa.organization_id
    left join fee_template ft on ft.id = fa.fee_template_id and ft.organization_id = fa.organization_id
    left join (
        select fee_assignment_id, sum(amount_minor) as paid_minor
        from fee_payment
        where voided_at is null
        group by fee_assignment_id
    ) fp on fp.fee_assignment_id = fa.id
    left join (
        select fee_assignment_id, sum(amount_minor) as adjusted_minor
        from fee_adjustment
        where voided_at is null
        group by fee_assignment_id
    ) fadj on fadj.fee_assignment_id = fa.id
"""

@Repository
class FeeSearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun searchTemplates(
        organizationId: UUID,
        criteria: FeeTemplateSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<FeeTemplate> {
        val built = buildTemplateSql(criteria, countOnly = false)
        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("offset", offset)
                .param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapTemplate).list()
    }

    fun countTemplates(
        organizationId: UUID,
        criteria: FeeTemplateSearchCriteria,
    ): Long {
        val built = buildTemplateSql(criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first).param("organizationId", organizationId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    fun searchHouseholdAssignments(
        organizationId: UUID,
        householdId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentWithBalance> {
        val built = buildAssignmentSql(criteria, householdId, includeHouseholdDisplay = false, countOnly = false)
        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("householdId", householdId)
                .param("offset", offset)
                .param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapAssignmentWithBalance).list()
    }

    fun countHouseholdAssignments(
        organizationId: UUID,
        householdId: UUID,
        criteria: FeeAssignmentSearchCriteria,
    ): Long {
        val built = buildAssignmentSql(criteria, householdId, includeHouseholdDisplay = false, countOnly = true)
        var statement =
            jdbcClient
                .sql(built.first)
                .param("organizationId", organizationId)
                .param("householdId", householdId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    fun searchOrganizationAssignments(
        organizationId: UUID,
        criteria: FeeAssignmentSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentSummary> {
        val built = buildAssignmentSql(criteria, householdId = null, includeHouseholdDisplay = true, countOnly = false)
        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("offset", offset)
                .param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapSummary).list()
    }

    fun countOrganizationAssignments(
        organizationId: UUID,
        criteria: FeeAssignmentSearchCriteria,
    ): Long {
        val built = buildAssignmentSql(criteria, householdId = null, includeHouseholdDisplay = true, countOnly = true)
        var statement = jdbcClient.sql(built.first).param("organizationId", organizationId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildTemplateSql(
        criteria: FeeTemplateSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from fee_template ft where ft.organization_id = :organizationId"
                } else {
                    "select $TEMPLATE_COLUMNS from fee_template ft where ft.organization_id = :organizationId"
                },
            )
        val params = linkedMapOf<String, Any>()

        criteria.status?.let {
            sql.append(" and ft.status = :templateStatus")
            params["templateStatus"] = it.name
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                " and (" +
                    " lower(ft.name) like :keyword" +
                    " or lower(coalesce(ft.description, '')) like :keyword" +
                    " )",
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    FeeTemplateSearchSort.NAME_ASC -> " order by lower(ft.name) asc, ft.created_at asc"
                    FeeTemplateSearchSort.NAME_DESC -> " order by lower(ft.name) desc, ft.created_at desc"
                    FeeTemplateSearchSort.AMOUNT_ASC -> " order by ft.amount_minor asc, lower(ft.name) asc"
                    FeeTemplateSearchSort.AMOUNT_DESC -> " order by ft.amount_minor desc, lower(ft.name) asc"
                    FeeTemplateSearchSort.NEWEST -> " order by ft.created_at desc, lower(ft.name) asc"
                    FeeTemplateSearchSort.OLDEST -> " order by ft.created_at asc, lower(ft.name) asc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun buildAssignmentSql(
        criteria: FeeAssignmentSearchCriteria,
        householdId: UUID?,
        includeHouseholdDisplay: Boolean,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val select =
            if (countOnly) {
                "select count(*) "
            } else if (includeHouseholdDisplay) {
                """
                select fa.id, fa.organization_id, fa.household_id, h.display_name as household_name,
                       fa.participant_id, p.first_name as participant_first_name, p.last_name as participant_last_name,
                       fa.fee_template_id, fa.description, fa.original_amount_minor, fa.currency, fa.due_date,
                       fa.status, fa.created_at, fa.updated_at,
                       coalesce(fp.paid_minor, 0) as paid_minor,
                       coalesce(fadj.adjusted_minor, 0) as adjusted_minor
                """.trimIndent() + " "
            } else {
                """
                select fa.id, fa.organization_id, fa.household_id,
                       fa.participant_id, fa.fee_template_id, fa.description, fa.original_amount_minor,
                       fa.currency, fa.due_date, fa.status, fa.created_at, fa.updated_at,
                       coalesce(fp.paid_minor, 0) as paid_minor,
                       coalesce(fadj.adjusted_minor, 0) as adjusted_minor
                """.trimIndent() + " "
            }

        val sql = StringBuilder(select).append(ASSIGNMENT_SUMMARY_FROM)
        sql.append(" where fa.organization_id = :organizationId")
        val params = linkedMapOf<String, Any>()

        if (householdId != null) {
            sql.append(" and fa.household_id = :householdId")
        }
        criteria.status?.let {
            sql.append(" and fa.status = :assignmentStatus")
            params["assignmentStatus"] = it.name
        }
        if (criteria.overdueOnly) {
            sql.append(" and fa.due_date < current_date and $BALANCE_EXPRESSION > 0")
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                " and (" +
                    " lower(fa.description) like :keyword" +
                    " or lower(coalesce(ft.name, '')) like :keyword" +
                    " or lower(coalesce(p.first_name, '') || ' ' || coalesce(p.last_name, '')) like :keyword" +
                    " or lower(h.display_name) like :keyword" +
                    " )",
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    FeeAssignmentSearchSort.DUE_DATE_ASC ->
                        " order by fa.due_date asc nulls last, fa.created_at desc"
                    FeeAssignmentSearchSort.DUE_DATE_DESC ->
                        " order by fa.due_date desc nulls last, fa.created_at desc"
                    FeeAssignmentSearchSort.BALANCE_DESC ->
                        " order by $BALANCE_EXPRESSION desc, fa.due_date asc nulls last"
                    FeeAssignmentSearchSort.BALANCE_ASC ->
                        " order by $BALANCE_EXPRESSION asc, fa.due_date asc nulls last"
                    FeeAssignmentSearchSort.DESCRIPTION_ASC ->
                        " order by lower(fa.description) asc, fa.created_at desc"
                    FeeAssignmentSearchSort.HOUSEHOLD_ASC ->
                        " order by lower(h.display_name) asc, fa.due_date asc nulls last"
                    FeeAssignmentSearchSort.NEWEST ->
                        " order by fa.created_at desc"
                    FeeAssignmentSearchSort.OLDEST ->
                        " order by fa.created_at asc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun mapTemplate(
        rs: java.sql.ResultSet,
        row: Int,
    ): FeeTemplate =
        FeeTemplate(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            description = rs.getString("description"),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            status = FeeTemplateStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )

    private fun mapAssignmentWithBalance(
        rs: java.sql.ResultSet,
        row: Int,
    ): FeeAssignmentWithBalance {
        val assignment =
            FeeAssignment(
                id = rs.getObject("id", UUID::class.java),
                organizationId = rs.getObject("organization_id", UUID::class.java),
                householdId = rs.getObject("household_id", UUID::class.java),
                participantId = rs.getObject("participant_id", UUID::class.java),
                feeTemplateId = rs.getObject("fee_template_id", UUID::class.java),
                description = rs.getString("description"),
                originalAmountMinor = rs.getLong("original_amount_minor"),
                currency = rs.getString("currency"),
                dueDate = rs.getDate("due_date")?.toLocalDate(),
                status = FeeAssignmentStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
            )
        val paid = rs.getLong("paid_minor")
        val adjusted = rs.getLong("adjusted_minor")
        return FeeAssignmentWithBalance(
            assignment = assignment,
            balance =
                FeeBalance(
                    paidMinor = paid,
                    adjustedMinor = adjusted,
                    balanceMinor = (assignment.originalAmountMinor - paid - adjusted).coerceAtLeast(0),
                ),
        )
    }

    private fun mapSummary(
        rs: java.sql.ResultSet,
        row: Int,
    ): FeeAssignmentSummary {
        val firstName = rs.getString("participant_first_name")
        val lastName = rs.getString("participant_last_name")
        return FeeAssignmentSummary(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            householdName = rs.getString("household_name"),
            participantId = rs.getObject("participant_id", UUID::class.java),
            participantName =
                listOfNotNull(firstName, lastName)
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() },
            feeTemplateId = rs.getObject("fee_template_id", UUID::class.java),
            description = rs.getString("description"),
            originalAmountMinor = rs.getLong("original_amount_minor"),
            currency = rs.getString("currency"),
            dueDate = rs.getDate("due_date")?.toLocalDate(),
            status = FeeAssignmentStatus.valueOf(rs.getString("status")),
            paidMinor = rs.getLong("paid_minor"),
            adjustedMinor = rs.getLong("adjusted_minor"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }
}
