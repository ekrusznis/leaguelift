package com.rally26.fee.persistence

import com.rally26.fee.domain.AdjustmentType
import com.rally26.fee.domain.FeeAdjustment
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, fee_assignment_id, household_id, adjustment_type, amount_minor, currency,
    reason, created_by_user_id, voided_at, voided_by_user_id, void_reason, created_at
"""

@Repository
class FeeAdjustmentRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): FeeAdjustment? =
        jdbcClient
            .sql("select $COLUMNS from fee_adjustment where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAllByAssignment(
        feeAssignmentId: UUID,
        organizationId: UUID,
    ): List<FeeAdjustment> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from fee_adjustment
                where fee_assignment_id = :feeAssignmentId and organization_id = :organizationId
                order by created_at desc
                """.trimIndent(),
            ).param("feeAssignmentId", feeAssignmentId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    fun sumActiveByAssignment(
        feeAssignmentId: UUID,
        organizationId: UUID,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(amount_minor), 0) from fee_adjustment
                where fee_assignment_id = :feeAssignmentId and organization_id = :organizationId and voided_at is null
                """.trimIndent(),
            ).param("feeAssignmentId", feeAssignmentId)
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        feeAssignmentId: UUID,
        householdId: UUID,
        adjustmentType: AdjustmentType,
        amountMinor: Long,
        currency: String,
        reason: String?,
        createdByUserId: UUID,
    ): FeeAdjustment {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into fee_adjustment
                    (id, organization_id, fee_assignment_id, household_id, adjustment_type, amount_minor, currency,
                     reason, created_by_user_id, created_at)
                values
                    (:id, :organizationId, :feeAssignmentId, :householdId, :adjustmentType, :amountMinor, :currency,
                     :reason, :createdByUserId, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("feeAssignmentId", feeAssignmentId)
            .param("householdId", householdId)
            .param("adjustmentType", adjustmentType.name)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("reason", reason)
            .param("createdByUserId", createdByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return FeeAdjustment(
            id,
            organizationId,
            feeAssignmentId,
            householdId,
            adjustmentType,
            amountMinor,
            currency,
            reason,
            createdByUserId,
            voidedAt = null,
            voidedByUserId = null,
            voidReason = null,
            createdAt = now,
        )
    }

    fun void(
        id: UUID,
        organizationId: UUID,
        voidedByUserId: UUID,
        voidReason: String,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update fee_adjustment
                set voided_at = :now, voided_by_user_id = :voidedByUserId, void_reason = :voidReason
                where id = :id and organization_id = :organizationId and voided_at is null
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("voidedByUserId", voidedByUserId)
            .param("voidReason", voidReason)
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): FeeAdjustment =
        FeeAdjustment(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            feeAssignmentId = rs.getObject("fee_assignment_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            adjustmentType = AdjustmentType.valueOf(rs.getString("adjustment_type")),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            reason = rs.getString("reason"),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            voidedAt = rs.getTimestamp("voided_at")?.toInstant(),
            voidedByUserId = rs.getObject("voided_by_user_id", UUID::class.java),
            voidReason = rs.getString("void_reason"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
