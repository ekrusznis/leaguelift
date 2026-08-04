package com.rally26.fee.paymentplan.persistence

import com.rally26.fee.paymentplan.domain.FeePaymentPlan
import com.rally26.fee.paymentplan.domain.FeePaymentPlanStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val PLAN_COLUMNS = """
    id, organization_id, fee_assignment_id, household_id, status, total_minor, currency,
    note, created_by_user_id, cancelled_by_user_id, cancelled_at, cancel_reason, created_at, updated_at
"""

data class InstallmentRow(
    val id: UUID,
    val organizationId: UUID,
    val paymentPlanId: UUID,
    val sequenceNumber: Int,
    val amountMinor: Long,
    val dueDate: LocalDate,
    val paidMinor: Long,
    val createdAt: Instant,
)

@Repository
class FeePaymentPlanRepository(private val jdbcClient: JdbcClient) {
    fun findLatestByAssignment(organizationId: UUID, feeAssignmentId: UUID): FeePaymentPlan? =
        jdbcClient.sql(
            "select $PLAN_COLUMNS from fee_payment_plan where organization_id = :organizationId and fee_assignment_id = :assignmentId order by created_at desc limit 1",
        )
            .param("organizationId", organizationId)
            .param("assignmentId", feeAssignmentId)
            .query(::mapPlan)
            .optional().orElse(null)

    fun findActiveByAssignment(organizationId: UUID, feeAssignmentId: UUID): FeePaymentPlan? =
        jdbcClient.sql(
            "select $PLAN_COLUMNS from fee_payment_plan where organization_id = :organizationId and fee_assignment_id = :assignmentId and status = 'ACTIVE'",
        )
            .param("organizationId", organizationId)
            .param("assignmentId", feeAssignmentId)
            .query(::mapPlan)
            .optional().orElse(null)

    fun findByIdForUpdate(organizationId: UUID, planId: UUID): FeePaymentPlan? =
        jdbcClient.sql("select $PLAN_COLUMNS from fee_payment_plan where organization_id = :organizationId and id = :planId for update")
            .param("organizationId", organizationId)
            .param("planId", planId)
            .query(::mapPlan)
            .optional().orElse(null)

    fun insertPlan(
        organizationId: UUID,
        feeAssignmentId: UUID,
        householdId: UUID,
        totalMinor: Long,
        currency: String,
        note: String?,
        createdByUserId: UUID,
    ): FeePaymentPlan {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient.sql(
            """
            insert into fee_payment_plan
                (id, organization_id, fee_assignment_id, household_id, status, total_minor, currency,
                 note, created_by_user_id, created_at, updated_at)
            values
                (:id, :organizationId, :assignmentId, :householdId, 'ACTIVE', :totalMinor, :currency,
                 :note, :createdByUserId, :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("organizationId", organizationId).param("assignmentId", feeAssignmentId)
            .param("householdId", householdId).param("totalMinor", totalMinor).param("currency", currency)
            .param("note", note).param("createdByUserId", createdByUserId).param("now", Timestamp.from(now))
            .update()
        return FeePaymentPlan(
            id, organizationId, feeAssignmentId, householdId, FeePaymentPlanStatus.ACTIVE,
            totalMinor, currency, note, createdByUserId, null, null, null, now, now,
        )
    }

    fun insertInstallment(
        organizationId: UUID,
        paymentPlanId: UUID,
        sequenceNumber: Int,
        amountMinor: Long,
        dueDate: LocalDate,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into fee_installment
                (id, organization_id, payment_plan_id, sequence_number, amount_minor, due_date)
            values (:id, :organizationId, :planId, :sequenceNumber, :amountMinor, :dueDate)
            """.trimIndent(),
        )
            .param("id", id).param("organizationId", organizationId).param("planId", paymentPlanId)
            .param("sequenceNumber", sequenceNumber).param("amountMinor", amountMinor).param("dueDate", Date.valueOf(dueDate))
            .update()
        return id
    }

    fun listInstallments(organizationId: UUID, paymentPlanId: UUID): List<InstallmentRow> =
        jdbcClient.sql(
            """
            select i.id, i.organization_id, i.payment_plan_id, i.sequence_number, i.amount_minor, i.due_date,
                   coalesce(sum(case when p.voided_at is null then a.amount_minor else 0 end), 0) as paid_minor,
                   i.created_at
            from fee_installment i
            left join fee_payment_installment_allocation a on a.installment_id = i.id
            left join fee_payment p on p.id = a.fee_payment_id
            where i.organization_id = :organizationId and i.payment_plan_id = :planId
            group by i.id
            order by i.due_date, i.sequence_number
            """.trimIndent(),
        )
            .param("organizationId", organizationId).param("planId", paymentPlanId)
            .query { rs, _ ->
                InstallmentRow(
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    paymentPlanId = rs.getObject("payment_plan_id", UUID::class.java),
                    sequenceNumber = rs.getInt("sequence_number"),
                    amountMinor = rs.getLong("amount_minor"),
                    dueDate = rs.getDate("due_date").toLocalDate(),
                    paidMinor = rs.getLong("paid_minor"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

    fun allocatePayment(
        organizationId: UUID,
        paymentPlanId: UUID,
        installmentId: UUID,
        feePaymentId: UUID,
        amountMinor: Long,
    ) {
        jdbcClient.sql(
            """
            insert into fee_payment_installment_allocation
                (id, organization_id, payment_plan_id, installment_id, fee_payment_id, amount_minor)
            values (:id, :organizationId, :planId, :installmentId, :paymentId, :amountMinor)
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID()).param("organizationId", organizationId).param("planId", paymentPlanId)
            .param("installmentId", installmentId).param("paymentId", feePaymentId).param("amountMinor", amountMinor)
            .update()
    }

    fun refreshStatus(organizationId: UUID, paymentPlanId: UUID): FeePaymentPlanStatus {
        val paid = jdbcClient.sql(
            """
            select coalesce(sum(a.amount_minor), 0)
            from fee_payment_installment_allocation a
            join fee_payment p on p.id = a.fee_payment_id and p.voided_at is null
            where a.organization_id = :organizationId and a.payment_plan_id = :planId
            """.trimIndent(),
        )
            .param("organizationId", organizationId).param("planId", paymentPlanId)
            .query(Long::class.java).single()
        val plan = findByIdForUpdate(organizationId, paymentPlanId) ?: return FeePaymentPlanStatus.CANCELLED
        val status = if (plan.status == FeePaymentPlanStatus.CANCELLED) FeePaymentPlanStatus.CANCELLED
        else if (paid >= plan.totalMinor) FeePaymentPlanStatus.COMPLETED else FeePaymentPlanStatus.ACTIVE
        if (status != plan.status) {
            jdbcClient.sql("update fee_payment_plan set status = :status, updated_at = :now where id = :planId and organization_id = :organizationId")
                .param("status", status.name).param("now", Timestamp.from(Instant.now()))
                .param("planId", paymentPlanId).param("organizationId", organizationId).update()
        }
        return status
    }

    fun cancel(organizationId: UUID, paymentPlanId: UUID, userId: UUID, reason: String): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update fee_payment_plan
            set status = 'CANCELLED', cancelled_by_user_id = :userId, cancelled_at = :now,
                cancel_reason = :reason, updated_at = :now
            where id = :planId and organization_id = :organizationId and status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("userId", userId).param("now", Timestamp.from(now)).param("reason", reason)
            .param("planId", paymentPlanId).param("organizationId", organizationId).update()
    }

    fun countOverdueInstallments(organizationId: UUID): Long =
        jdbcClient.sql(
            """
            select count(*)
            from fee_installment i
            join fee_payment_plan plan on plan.id = i.payment_plan_id and plan.status = 'ACTIVE'
            left join (
                select a.installment_id, sum(a.amount_minor) as paid_minor
                from fee_payment_installment_allocation a
                join fee_payment p on p.id = a.fee_payment_id and p.voided_at is null
                group by a.installment_id
            ) paid on paid.installment_id = i.id
            where i.organization_id = :organizationId and i.due_date < current_date
              and coalesce(paid.paid_minor, 0) < i.amount_minor
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .query(Long::class.java).single()

    private fun mapPlan(rs: ResultSet, rowNum: Int): FeePaymentPlan = FeePaymentPlan(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        feeAssignmentId = rs.getObject("fee_assignment_id", UUID::class.java),
        householdId = rs.getObject("household_id", UUID::class.java),
        status = FeePaymentPlanStatus.valueOf(rs.getString("status")),
        totalMinor = rs.getLong("total_minor"),
        currency = rs.getString("currency"),
        note = rs.getString("note"),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        cancelledByUserId = rs.getObject("cancelled_by_user_id", UUID::class.java),
        cancelledAt = rs.getTimestamp("cancelled_at")?.toInstant(),
        cancelReason = rs.getString("cancel_reason"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
