package com.leaguelift.fee.persistence

import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.FeeAssignmentSummary
import com.leaguelift.fee.domain.FeeTemplate
import com.leaguelift.fee.domain.FeeTemplateStatus
import com.leaguelift.fee.domain.OrganizationFeeFinancialSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val T_COLS = "id, organization_id, name, description, amount_minor, currency, status, created_at, updated_at"
private const val A_COLS = "id, organization_id, household_id, participant_id, fee_template_id, description, original_amount_minor, currency, due_date, status, created_at, updated_at"

@Repository
class FeeRepository(private val jdbcClient: JdbcClient) {

    // --- Fee Templates ---

    fun findTemplateById(id: UUID, organizationId: UUID): FeeTemplate? =
        jdbcClient.sql("select $T_COLS from fee_template where id = :id and organization_id = :organizationId")
            .param("id", id).param("organizationId", organizationId)
            .query(::mapTemplate).optional().orElse(null)

    fun findAllTemplates(organizationId: UUID, offset: Int, limit: Int): List<FeeTemplate> =
        jdbcClient.sql(
            """
            select $T_COLS from fee_template
            where organization_id = :organizationId and status = 'ACTIVE'
            order by name asc
            limit :limit offset :offset
            """.trimIndent(),
        )
            .param("organizationId", organizationId).param("limit", limit).param("offset", offset)
            .query(::mapTemplate).list()

    fun countAllTemplates(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from fee_template where organization_id = :organizationId and status = 'ACTIVE'")
            .param("organizationId", organizationId)
            .query(Long::class.java).single()

    fun insertTemplate(organizationId: UUID, name: String, description: String?, amountMinor: Long, currency: String): FeeTemplate {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into fee_template (id, organization_id, name, description, amount_minor, currency, status, created_at, updated_at)
            values (:id, :organizationId, :name, :description, :amountMinor, :currency, 'ACTIVE', :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("organizationId", organizationId).param("name", name)
            .param("description", description).param("amountMinor", amountMinor).param("currency", currency)
            .param("now", Timestamp.from(now)).update()
        return FeeTemplate(id, organizationId, name, description, amountMinor, currency, FeeTemplateStatus.ACTIVE, now, now)
    }

    fun updateTemplate(id: UUID, organizationId: UUID, name: String?, description: String?, amountMinor: Long?): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update fee_template
            set name         = coalesce(:name, name),
                description  = coalesce(:description, description),
                amount_minor = coalesce(:amountMinor, amount_minor),
                updated_at   = :now
            where id = :id and organization_id = :organizationId and status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("name", name).param("description", description).param("amountMinor", amountMinor)
            .param("now", Timestamp.from(now)).param("id", id).param("organizationId", organizationId).update()
    }

    fun archiveTemplate(id: UUID, organizationId: UUID): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            "update fee_template set status = 'ARCHIVED', updated_at = :now where id = :id and organization_id = :organizationId and status = 'ACTIVE'",
        )
            .param("now", Timestamp.from(now)).param("id", id).param("organizationId", organizationId).update()
    }

    // --- Fee Assignments ---

    fun findAssignmentById(id: UUID, organizationId: UUID): FeeAssignment? =
        jdbcClient.sql("select $A_COLS from fee_assignment where id = :id and organization_id = :organizationId")
            .param("id", id).param("organizationId", organizationId)
            .query(::mapAssignment).optional().orElse(null)

    fun findByHousehold(householdId: UUID, organizationId: UUID, offset: Int, limit: Int): List<FeeAssignment> =
        jdbcClient.sql(
            """
            select $A_COLS from fee_assignment
            where household_id = :householdId and organization_id = :organizationId
            order by created_at desc
            limit :limit offset :offset
            """.trimIndent(),
        )
            .param("householdId", householdId).param("organizationId", organizationId)
            .param("limit", limit).param("offset", offset)
            .query(::mapAssignment).list()

    fun countByHousehold(householdId: UUID, organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from fee_assignment where household_id = :householdId and organization_id = :organizationId")
            .param("householdId", householdId).param("organizationId", organizationId)
            .query(Long::class.java).single()

    fun insertAssignment(
        organizationId: UUID,
        householdId: UUID,
        participantId: UUID?,
        feeTemplateId: UUID?,
        description: String,
        originalAmountMinor: Long,
        currency: String,
        dueDate: LocalDate?,
    ): FeeAssignment {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into fee_assignment (id, organization_id, household_id, participant_id, fee_template_id, description, original_amount_minor, currency, due_date, status, created_at, updated_at)
            values (:id, :organizationId, :householdId, :participantId, :feeTemplateId, :description, :originalAmountMinor, :currency, :dueDate, 'OPEN', :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("organizationId", organizationId).param("householdId", householdId)
            .param("participantId", participantId).param("feeTemplateId", feeTemplateId)
            .param("description", description).param("originalAmountMinor", originalAmountMinor)
            .param("currency", currency).param("dueDate", dueDate?.let { Date.valueOf(it) })
            .param("now", Timestamp.from(now)).update()
        return FeeAssignment(id, organizationId, householdId, participantId, feeTemplateId, description, originalAmountMinor, currency, dueDate, FeeAssignmentStatus.OPEN, now, now)
    }

    fun updateAssignmentStatus(id: UUID, organizationId: UUID, status: FeeAssignmentStatus): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            "update fee_assignment set status = :status, updated_at = :now where id = :id and organization_id = :organizationId",
        )
            .param("status", status.name).param("now", Timestamp.from(now))
            .param("id", id).param("organizationId", organizationId).update()
    }

    // --- Org-wide (collections dashboard / CSV export) ---

    /**
     * Joined with household/participant display names and payment/adjustment sums via
     * subquery joins (one SQL round trip, not N+1) — acceptable at pilot list-page
     * sizes; revisit with a materialized view if this becomes a bottleneck.
     */
    private val summarySelect = """
        select fa.id, fa.organization_id, fa.household_id, h.display_name as household_name,
               fa.participant_id, p.first_name as participant_first_name, p.last_name as participant_last_name,
               fa.fee_template_id, fa.description, fa.original_amount_minor, fa.currency, fa.due_date,
               fa.status, fa.created_at, fa.updated_at,
               coalesce(fp.paid_minor, 0) as paid_minor, coalesce(fadj.adjusted_minor, 0) as adjusted_minor
        from fee_assignment fa
        join household h on h.id = fa.household_id
        left join participant p on p.id = fa.participant_id
        left join (select fee_assignment_id, sum(amount_minor) as paid_minor from fee_payment where voided_at is null group by fee_assignment_id) fp
            on fp.fee_assignment_id = fa.id
        left join (select fee_assignment_id, sum(amount_minor) as adjusted_minor from fee_adjustment where voided_at is null group by fee_assignment_id) fadj
            on fadj.fee_assignment_id = fa.id
        where fa.organization_id = :organizationId
          and (:status::text is null or fa.status = :status)
          and (:overdueOnly = false or (fa.due_date < current_date
               and (fa.original_amount_minor - coalesce(fp.paid_minor, 0) - coalesce(fadj.adjusted_minor, 0)) > 0))
    """.trimIndent()

    fun findAllForOrganization(
        organizationId: UUID,
        status: FeeAssignmentStatus?,
        overdueOnly: Boolean,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentSummary> =
        jdbcClient.sql("$summarySelect order by fa.due_date asc nulls last, fa.created_at desc limit :limit offset :offset")
            .param("organizationId", organizationId).param("status", status?.name)
            .param("overdueOnly", overdueOnly).param("limit", limit).param("offset", offset)
            .query(::mapSummary).list()

    fun countAllForOrganization(organizationId: UUID, status: FeeAssignmentStatus?, overdueOnly: Boolean): Long =
        jdbcClient.sql("select count(*) from ($summarySelect) counted")
            .param("organizationId", organizationId).param("status", status?.name).param("overdueOnly", overdueOnly)
            .query(Long::class.java).single()

    fun getFinancialSummary(organizationId: UUID): OrganizationFeeFinancialSummary =
        jdbcClient.sql(
            """
            select
                coalesce(sum(fa.original_amount_minor), 0) as fees_assigned_minor,
                coalesce(sum(coalesce(fp.paid_minor, 0)), 0) as fees_collected_minor,
                coalesce(sum(greatest(0, fa.original_amount_minor - coalesce(fp.paid_minor, 0) - coalesce(fadj.adjusted_minor, 0))), 0) as outstanding_minor
            from fee_assignment fa
            left join (select fee_assignment_id, sum(amount_minor) as paid_minor from fee_payment where voided_at is null group by fee_assignment_id) fp
                on fp.fee_assignment_id = fa.id
            left join (select fee_assignment_id, sum(amount_minor) as adjusted_minor from fee_adjustment where voided_at is null group by fee_assignment_id) fadj
                on fadj.fee_assignment_id = fa.id
            where fa.organization_id = :organizationId and fa.status != 'CANCELLED'
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .query { rs, _ ->
                OrganizationFeeFinancialSummary(
                    feesAssignedMinor = rs.getLong("fees_assigned_minor"),
                    feesCollectedMinor = rs.getLong("fees_collected_minor"),
                    outstandingMinor = rs.getLong("outstanding_minor"),
                )
            }
            .single()

    private fun mapSummary(rs: java.sql.ResultSet, row: Int): FeeAssignmentSummary {
        val firstName = rs.getString("participant_first_name")
        val lastName = rs.getString("participant_last_name")
        return FeeAssignmentSummary(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            householdName = rs.getString("household_name"),
            participantId = rs.getObject("participant_id", UUID::class.java),
            participantName = if (firstName != null) "$firstName $lastName" else null,
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

    private fun mapTemplate(rs: java.sql.ResultSet, row: Int) = FeeTemplate(
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

    private fun mapAssignment(rs: java.sql.ResultSet, row: Int) = FeeAssignment(
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
}
