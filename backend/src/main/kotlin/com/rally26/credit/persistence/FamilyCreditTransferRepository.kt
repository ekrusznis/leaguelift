package com.rally26.credit.persistence

import com.rally26.credit.domain.CreditTransferStatus
import com.rally26.credit.domain.FamilyCreditTransfer
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, from_household_id, to_household_id, amount_minor, initiated_by_user_id, " +
        "status, reviewed_by_user_id, review_note, created_at, reviewed_at"

@Repository
class FamilyCreditTransferRepository(
    private val jdbcClient: JdbcClient,
) {
    /** Always inserted PENDING — the receiver's grant is only created once an org manager approves. */
    fun insert(
        organizationId: UUID,
        fromHouseholdId: UUID,
        toHouseholdId: UUID,
        amountMinor: Long,
        initiatedByUserId: UUID,
    ): FamilyCreditTransfer {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into family_credit_transfer
                    (id, organization_id, from_household_id, to_household_id, amount_minor, initiated_by_user_id, status, created_at)
                values
                    (:id, :organizationId, :fromHouseholdId, :toHouseholdId, :amountMinor, :initiatedByUserId, 'PENDING', :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("fromHouseholdId", fromHouseholdId)
            .param("toHouseholdId", toHouseholdId)
            .param("amountMinor", amountMinor)
            .param("initiatedByUserId", initiatedByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return FamilyCreditTransfer(
            id,
            organizationId,
            fromHouseholdId,
            toHouseholdId,
            amountMinor,
            initiatedByUserId,
            CreditTransferStatus.PENDING,
            null,
            null,
            now,
            null,
        )
    }

    fun findById(
        id: UUID,
        organizationId: UUID,
    ): FamilyCreditTransfer? =
        jdbcClient
            .sql("select $COLUMNS from family_credit_transfer where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listPendingForOrganization(organizationId: UUID): List<FamilyCreditTransfer> =
        jdbcClient
            .sql(
                "select $COLUMNS from family_credit_transfer where organization_id = :organizationId and status = 'PENDING' order by created_at asc",
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    fun listForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): List<FamilyCreditTransfer> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from family_credit_transfer
                where organization_id = :organizationId and (from_household_id = :householdId or to_household_id = :householdId)
                order by created_at desc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(::mapRow)
            .list()

    /** Sum of PENDING outgoing transfers — the amount already held from this household's grants but not yet moved. */
    fun sumPendingOutgoingForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): Long =
        jdbcClient
            .sql(
                "select coalesce(sum(amount_minor), 0) from family_credit_transfer " +
                    "where organization_id = :organizationId and from_household_id = :householdId and status = 'PENDING'",
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(Long::class.java)
            .single()

    /** Returns updated row count — 0 means the transfer was no longer PENDING (already reviewed), a conflict the caller must surface. */
    fun review(
        id: UUID,
        organizationId: UUID,
        status: CreditTransferStatus,
        reviewedByUserId: UUID,
        reviewNote: String?,
    ): Int {
        require(status == CreditTransferStatus.APPROVED || status == CreditTransferStatus.REJECTED)
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update family_credit_transfer
                set status = :status,
                    reviewed_by_user_id = :reviewedByUserId,
                    review_note = :reviewNote,
                    reviewed_at = :now
                where id = :id and organization_id = :organizationId and status = 'PENDING'
                """.trimIndent(),
            ).param("status", status.name)
            .param("reviewedByUserId", reviewedByUserId)
            .param("reviewNote", reviewNote)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): FamilyCreditTransfer =
        FamilyCreditTransfer(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            fromHouseholdId = rs.getObject("from_household_id", UUID::class.java),
            toHouseholdId = rs.getObject("to_household_id", UUID::class.java),
            amountMinor = rs.getLong("amount_minor"),
            initiatedByUserId = rs.getObject("initiated_by_user_id", UUID::class.java),
            status = CreditTransferStatus.valueOf(rs.getString("status")),
            reviewedByUserId = rs.getObject("reviewed_by_user_id", UUID::class.java),
            reviewNote = rs.getString("review_note"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
        )
}
