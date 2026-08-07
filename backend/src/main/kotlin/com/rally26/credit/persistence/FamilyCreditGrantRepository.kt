package com.rally26.credit.persistence

import com.rally26.credit.domain.CreditSourceType
import com.rally26.credit.domain.FamilyCreditGrant
import com.rally26.credit.domain.FamilyCreditGrantStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, household_id, amount_minor, remaining_minor, currency, status,
    source_type, source_id, granted_at, available_at, expires_at, created_at
"""

@Repository
class FamilyCreditGrantRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): FamilyCreditGrant? =
        jdbcClient
            .sql("select $COLUMNS from family_credit_grant where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): List<FamilyCreditGrant> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from family_credit_grant
                where organization_id = :organizationId and household_id = :householdId
                order by expires_at asc nulls last, created_at asc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(::mapRow)
            .list()

    /** Grants with real remaining balance, oldest-expiring-first — the FIFO consumption order for applying credit. */
    fun listAvailableForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): List<FamilyCreditGrant> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from family_credit_grant
                where organization_id = :organizationId and household_id = :householdId
                  and status = 'AVAILABLE' and remaining_minor > 0
                order by expires_at asc nulls last, created_at asc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(::mapRow)
            .list()

    fun sumAvailableForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(remaining_minor), 0) from family_credit_grant
                where organization_id = :organizationId and household_id = :householdId and status = 'AVAILABLE'
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(Long::class.java)
            .single()

    fun sumPendingForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(remaining_minor), 0) from family_credit_grant
                where organization_id = :organizationId and household_id = :householdId and status = 'PENDING'
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(Long::class.java)
            .single()

    fun sumExpiringSoonForHousehold(
        organizationId: UUID,
        householdId: UUID,
        before: Instant,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(remaining_minor), 0) from family_credit_grant
                where organization_id = :organizationId and household_id = :householdId and status = 'AVAILABLE'
                  and expires_at is not null and expires_at <= :before
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("before", Timestamp.from(before))
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        householdId: UUID,
        amountMinor: Long,
        currency: String,
        status: FamilyCreditGrantStatus,
        sourceType: CreditSourceType,
        sourceId: UUID?,
        expiresAt: Instant?,
    ): FamilyCreditGrant {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into family_credit_grant
                    (id, organization_id, household_id, amount_minor, remaining_minor, currency, status,
                     source_type, source_id, granted_at, available_at, expires_at, created_at)
                values
                    (:id, :organizationId, :householdId, :amountMinor, :amountMinor, :currency, :status,
                     :sourceType, :sourceId, :now, :now, :expiresAt, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("status", status.name)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .param("now", Timestamp.from(now))
            .param("expiresAt", expiresAt?.let { Timestamp.from(it) })
            .update()
        return findById(id, organizationId)!!
    }

    /** Decrements remaining_minor by exactly `amount` — the caller (FamilyCreditService) enforces amount <= current remaining. */
    fun decrementRemaining(
        id: UUID,
        organizationId: UUID,
        amountMinor: Long,
    ): Int =
        jdbcClient
            .sql(
                "update family_credit_grant set remaining_minor = remaining_minor - :amountMinor where id = :id and organization_id = :organizationId",
            ).param("amountMinor", amountMinor)
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: FamilyCreditGrantStatus,
    ): Int =
        jdbcClient
            .sql("update family_credit_grant set status = :status where id = :id and organization_id = :organizationId")
            .param("status", status.name)
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    /** Phase 24 slice 24.3 — the guardian dashboard's real Recent Orders card looks up a single order's own grant, if any, by exact source. */
    fun findBySource(
        organizationId: UUID,
        sourceType: CreditSourceType,
        sourceId: UUID,
    ): FamilyCreditGrant? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from family_credit_grant
                where organization_id = :organizationId and source_type = :sourceType and source_id = :sourceId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Revokes the remaining (unapplied) balance of every grant sourced from a specific contribution — used when that contribution is refunded. */
    fun revokeRemainingBySource(
        organizationId: UUID,
        sourceType: CreditSourceType,
        sourceId: UUID,
    ): Int =
        jdbcClient
            .sql(
                """
                update family_credit_grant
                set status = 'REVOKED', remaining_minor = 0
                where organization_id = :organizationId and source_type = :sourceType and source_id = :sourceId
                  and status in ('PENDING', 'AVAILABLE')
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .update()

    /** Flips PENDING/AVAILABLE grants past their expiry to EXPIRED — used by the scheduled scanner. */
    fun findExpiring(before: Instant): List<FamilyCreditGrant> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from family_credit_grant
                where status in ('PENDING', 'AVAILABLE') and expires_at is not null and expires_at <= :before and remaining_minor > 0
                """.trimIndent(),
            ).param("before", Timestamp.from(before))
            .query(::mapRow)
            .list()

    fun markExpired(
        id: UUID,
        organizationId: UUID,
    ): Int =
        jdbcClient
            .sql(
                "update family_credit_grant set status = 'EXPIRED' where id = :id and organization_id = :organizationId and status in ('PENDING', 'AVAILABLE')",
            ).param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): FamilyCreditGrant =
        FamilyCreditGrant(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            amountMinor = rs.getLong("amount_minor"),
            remainingMinor = rs.getLong("remaining_minor"),
            currency = rs.getString("currency"),
            status = FamilyCreditGrantStatus.valueOf(rs.getString("status")),
            sourceType = CreditSourceType.valueOf(rs.getString("source_type")),
            sourceId = rs.getObject("source_id", UUID::class.java),
            grantedAt = rs.getTimestamp("granted_at").toInstant(),
            availableAt = rs.getTimestamp("available_at").toInstant(),
            expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
