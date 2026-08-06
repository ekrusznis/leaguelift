package com.rally26.credit.persistence

import com.rally26.credit.domain.FamilyCreditApplication
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, grant_id, fee_adjustment_id, amount_minor, applied_by_user_id, created_at"

@Repository
class FamilyCreditApplicationRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(
        organizationId: UUID,
        grantId: UUID,
        feeAdjustmentId: UUID,
        amountMinor: Long,
        appliedByUserId: UUID,
    ): FamilyCreditApplication {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into family_credit_application
                    (id, organization_id, grant_id, fee_adjustment_id, amount_minor, applied_by_user_id, created_at)
                values
                    (:id, :organizationId, :grantId, :feeAdjustmentId, :amountMinor, :appliedByUserId, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("grantId", grantId)
            .param("feeAdjustmentId", feeAdjustmentId)
            .param("amountMinor", amountMinor)
            .param("appliedByUserId", appliedByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return FamilyCreditApplication(id, organizationId, grantId, feeAdjustmentId, amountMinor, appliedByUserId, now)
    }

    fun sumAppliedForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(fca.amount_minor), 0) from family_credit_application fca
                join family_credit_grant fcg on fcg.id = fca.grant_id
                where fcg.organization_id = :organizationId and fcg.household_id = :householdId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(Long::class.java)
            .single()

    fun findByFeeAdjustment(
        organizationId: UUID,
        feeAdjustmentId: UUID,
    ): List<FamilyCreditApplication> =
        jdbcClient
            .sql(
                "select $COLUMNS from family_credit_application where organization_id = :organizationId and fee_adjustment_id = :feeAdjustmentId",
            ).param("organizationId", organizationId)
            .param("feeAdjustmentId", feeAdjustmentId)
            .query(::mapRow)
            .list()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): FamilyCreditApplication =
        FamilyCreditApplication(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            grantId = rs.getObject("grant_id", UUID::class.java),
            feeAdjustmentId = rs.getObject("fee_adjustment_id", UUID::class.java),
            amountMinor = rs.getLong("amount_minor"),
            appliedByUserId = rs.getObject("applied_by_user_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
