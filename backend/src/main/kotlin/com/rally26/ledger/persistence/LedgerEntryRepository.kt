package com.rally26.ledger.persistence

import com.rally26.ledger.domain.LedgerDirection
import com.rally26.ledger.domain.LedgerEntry
import com.rally26.ledger.domain.LedgerEntryType
import com.rally26.ledger.domain.LedgerSourceType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, account_code, entry_type, direction, amount_minor, currency,
    source_type, source_id, external_reference, description, included_in_transfer_entry_id,
    effective_at, created_at
"""

@Repository
class LedgerEntryRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(
        organizationId: UUID,
        accountCode: String,
        entryType: LedgerEntryType,
        direction: LedgerDirection,
        amountMinor: Long,
        currency: String,
        sourceType: LedgerSourceType,
        sourceId: UUID,
        externalReference: String?,
        description: String?,
    ): LedgerEntry {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into ledger_entry
                	(id, organization_id, account_code, entry_type, direction, amount_minor, currency,
                	 source_type, source_id, external_reference, description, effective_at, created_at)
                values
                	(:id, :organizationId, :accountCode, :entryType, :direction, :amountMinor, :currency,
                	 :sourceType, :sourceId, :externalReference, :description, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("accountCode", accountCode)
            .param("entryType", entryType.name)
            .param("direction", direction.name)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .param("externalReference", externalReference)
            .param("description", description)
            .param("now", Timestamp.from(now))
            .update()
        return LedgerEntry(
            id,
            organizationId,
            accountCode,
            entryType,
            direction,
            amountMinor,
            currency,
            sourceType,
            sourceId,
            externalReference,
            description,
            null,
            now,
            now,
        )
    }

    /** ORGANIZATION_EARNING credits not yet paid out, whose holding period has elapsed — the actually-transferable pool. */
    fun findEligibleOrganizationEarningCredits(
        organizationId: UUID,
        cutoff: Instant,
    ): List<LedgerEntry> = findUnclaimedOrganizationEarnings(organizationId, "CREDIT", "effective_at <= :cutoff", cutoff)

    /** ORGANIZATION_EARNING credits not yet paid out, still within the holding period — shown to the admin as "held," not yet actionable. */
    fun findHeldOrganizationEarningCredits(
        organizationId: UUID,
        cutoff: Instant,
    ): List<LedgerEntry> = findUnclaimedOrganizationEarnings(organizationId, "CREDIT", "effective_at > :cutoff", cutoff)

    /** Reversal debits (from refunds) not yet netted against a transfer — counted immediately, no holding period, since they represent money already owed back. */
    fun findPendingOrganizationEarningDebits(organizationId: UUID): List<LedgerEntry> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from ledger_entry
                where organization_id = :organizationId and entry_type = 'ORGANIZATION_EARNING' and direction = 'DEBIT'
                  and included_in_transfer_entry_id is null
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    private fun findUnclaimedOrganizationEarnings(
        organizationId: UUID,
        direction: String,
        cutoffPredicate: String,
        cutoff: Instant,
    ): List<LedgerEntry> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from ledger_entry
                where organization_id = :organizationId and entry_type = 'ORGANIZATION_EARNING' and direction = :direction
                  and included_in_transfer_entry_id is null and $cutoffPredicate
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("direction", direction)
            .param("cutoff", Timestamp.from(cutoff))
            .query(::mapRow)
            .list()

    fun findBySource(
        organizationId: UUID,
        sourceType: LedgerSourceType,
        sourceId: UUID,
    ): List<LedgerEntry> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from ledger_entry
                where organization_id = :organizationId and source_type = :sourceType and source_id = :sourceId
                order by created_at, id
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .query(::mapRow)
            .list()

    /** Platform-wide sum for one entry type + direction (Platform Admin dashboard, Phase 7 completion) — no organization filter, unlike every other query here. */
    fun sumAllByTypeAndDirection(
        entryType: LedgerEntryType,
        direction: LedgerDirection,
    ): Long =
        jdbcClient
            .sql("select coalesce(sum(amount_minor), 0) from ledger_entry where entry_type = :entryType and direction = :direction")
            .param("entryType", entryType.name)
            .param("direction", direction.name)
            .query(Long::class.java)
            .single()

    /** Org-scoped sum for one entry type + direction (Owner dashboard Financial Overview/Reports Snapshot, Phase 7 completion demo-data cleanup). */
    fun sumByOrganizationTypeAndDirection(
        organizationId: UUID,
        entryType: LedgerEntryType,
        direction: LedgerDirection,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(amount_minor), 0) from ledger_entry
                where organization_id = :organizationId and entry_type = :entryType and direction = :direction
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("entryType", entryType.name)
            .param("direction", direction.name)
            .query(Long::class.java)
            .single()

    fun markIncludedInTransfer(
        entryIds: List<UUID>,
        transferEntryId: UUID,
    ): Int {
        if (entryIds.isEmpty()) return 0
        return jdbcClient
            .sql("update ledger_entry set included_in_transfer_entry_id = :transferEntryId where id in (:entryIds)")
            .param("transferEntryId", transferEntryId)
            .param("entryIds", entryIds)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): LedgerEntry =
        LedgerEntry(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            accountCode = rs.getString("account_code"),
            entryType = LedgerEntryType.valueOf(rs.getString("entry_type")),
            direction = LedgerDirection.valueOf(rs.getString("direction")),
            amountMinor = rs.getLong("amount_minor"),
            currency = rs.getString("currency"),
            sourceType = LedgerSourceType.valueOf(rs.getString("source_type")),
            sourceId = rs.getObject("source_id", UUID::class.java),
            externalReference = rs.getString("external_reference"),
            description = rs.getString("description"),
            includedInTransferEntryId = rs.getObject("included_in_transfer_entry_id", UUID::class.java),
            effectiveAt = rs.getTimestamp("effective_at").toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
