package com.rally26.financialcorrection.persistence

import com.rally26.financialcorrection.domain.FinancialCorrection
import com.rally26.financialcorrection.domain.FinancialCorrectionTargetType
import com.rally26.financialcorrection.domain.FinancialCorrectionType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, correction_type, target_type, target_id, amount_minor, currency,
    reason, provider_reference, confirmation_hash, idempotency_key, created_by_user_id, created_at
"""

@Repository
class FinancialCorrectionRepository(
    private val jdbcClient: JdbcClient,
) {
    /** Serializes corrections for one source record without inventing a mutable financial row. */
    fun lockTarget(
        organizationId: UUID,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
    ) {
        val lockKey = "$organizationId:${targetType.name}:$targetId"
        jdbcClient
            .sql("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
            .param("lockKey", lockKey)
            .query { _, _ -> Unit }
            .single()
    }

    fun findByIdempotencyKey(
        organizationId: UUID,
        idempotencyKey: String,
    ): FinancialCorrection? =
        jdbcClient
            .sql("select $COLUMNS from financial_correction where organization_id = :organizationId and idempotency_key = :key")
            .param("organizationId", organizationId)
            .param("key", idempotencyKey)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun sumByTarget(
        organizationId: UUID,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
    ): Long =
        jdbcClient
            .sql(
                "select coalesce(sum(amount_minor), 0) from financial_correction where organization_id = :organizationId and target_type = :targetType and target_id = :targetId",
            ).param("organizationId", organizationId)
            .param("targetType", targetType.name)
            .param("targetId", targetId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        correctionType: FinancialCorrectionType,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
        amountMinor: Long,
        currency: String,
        reason: String,
        providerReference: String?,
        confirmationHash: String,
        idempotencyKey: String,
        createdByUserId: UUID,
    ): FinancialCorrection {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into financial_correction
                    (id, organization_id, correction_type, target_type, target_id, amount_minor, currency,
                     reason, provider_reference, confirmation_hash, idempotency_key, created_by_user_id, created_at)
                values
                    (:id, :organizationId, :correctionType, :targetType, :targetId, :amountMinor, :currency,
                     :reason, :providerReference, :confirmationHash, :idempotencyKey, :createdByUserId, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("correctionType", correctionType.name)
            .param("targetType", targetType.name)
            .param("targetId", targetId)
            .param("amountMinor", amountMinor)
            .param("currency", currency)
            .param("reason", reason)
            .param("providerReference", providerReference)
            .param("confirmationHash", confirmationHash)
            .param("idempotencyKey", idempotencyKey)
            .param("createdByUserId", createdByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return FinancialCorrection(
            id,
            organizationId,
            correctionType,
            targetType,
            targetId,
            amountMinor,
            currency,
            reason,
            providerReference,
            confirmationHash,
            idempotencyKey,
            createdByUserId,
            now,
        )
    }

    fun list(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<FinancialCorrection> =
        jdbcClient
            .sql(
                "select $COLUMNS from financial_correction where organization_id = :organizationId order by created_at desc offset :offset limit :limit",
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun count(organizationId: UUID): Long =
        jdbcClient
            .sql("select count(*) from financial_correction where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun search(
        organizationId: UUID,
        query: String?,
        targetType: FinancialCorrectionTargetType?,
        correctionType: FinancialCorrectionType?,
        ascending: Boolean,
        offset: Int,
        limit: Int,
    ): List<FinancialCorrection> {
        val sql =
            buildString {
                append("select $COLUMNS from financial_correction where organization_id = :organizationId")
                if (query != null) {
                    append(
                        " and (lower(reason) like :query or lower(coalesce(provider_reference, '')) like :query" +
                            " or lower(cast(target_id as text)) like :query)",
                    )
                }
                if (targetType != null) append(" and target_type = :targetType")
                if (correctionType != null) append(" and correction_type = :correctionType")
                append(" order by created_at ${if (ascending) "asc" else "desc"} offset :offset limit :limit")
            }
        var statement =
            jdbcClient
                .sql(sql)
                .param("organizationId", organizationId)
                .param("offset", offset)
                .param("limit", limit)
        if (query != null) statement = statement.param("query", "%${query.lowercase()}%")
        if (targetType != null) statement = statement.param("targetType", targetType.name)
        if (correctionType != null) statement = statement.param("correctionType", correctionType.name)
        return statement.query(::mapRow).list()
    }

    fun countSearch(
        organizationId: UUID,
        query: String?,
        targetType: FinancialCorrectionTargetType?,
        correctionType: FinancialCorrectionType?,
    ): Long {
        val sql =
            buildString {
                append("select count(*) from financial_correction where organization_id = :organizationId")
                if (query != null) {
                    append(
                        " and (lower(reason) like :query or lower(coalesce(provider_reference, '')) like :query" +
                            " or lower(cast(target_id as text)) like :query)",
                    )
                }
                if (targetType != null) append(" and target_type = :targetType")
                if (correctionType != null) append(" and correction_type = :correctionType")
            }
        var statement = jdbcClient.sql(sql).param("organizationId", organizationId)
        if (query != null) statement = statement.param("query", "%${query.lowercase()}%")
        if (targetType != null) statement = statement.param("targetType", targetType.name)
        if (correctionType != null) statement = statement.param("correctionType", correctionType.name)
        return statement.query(Long::class.java).single()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ) = FinancialCorrection(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        correctionType = FinancialCorrectionType.valueOf(rs.getString("correction_type")),
        targetType = FinancialCorrectionTargetType.valueOf(rs.getString("target_type")),
        targetId = rs.getObject("target_id", UUID::class.java),
        amountMinor = rs.getLong("amount_minor"),
        currency = rs.getString("currency"),
        reason = rs.getString("reason"),
        providerReference = rs.getString("provider_reference"),
        confirmationHash = rs.getString("confirmation_hash"),
        idempotencyKey = rs.getString("idempotency_key"),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
