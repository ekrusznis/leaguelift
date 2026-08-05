package com.rally26.integration.quickbooks.persistence

import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksAccountingBasis
import com.rally26.integration.quickbooks.domain.QuickBooksConnectionSetting
import com.rally26.integration.quickbooks.domain.QuickBooksEnvironment
import com.rally26.integration.quickbooks.domain.QuickBooksExportBatch
import com.rally26.integration.quickbooks.domain.QuickBooksExportCandidateCounts
import com.rally26.integration.quickbooks.domain.QuickBooksExportPolicy
import com.rally26.integration.quickbooks.domain.QuickBooksExportStatus
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class QuickBooksRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findSetting(connectionId: UUID): QuickBooksConnectionSetting? =
        jdbcClient
            .sql("select $SETTING_COLUMNS from quickbooks_connection_setting where connection_id = :connectionId")
            .param("connectionId", connectionId)
            .query(::mapSetting)
            .optional()
            .orElse(null)

    fun upsertCompany(
        connectionId: UUID,
        realmId: String,
        companyName: String,
        defaultCurrency: String?,
        environment: QuickBooksEnvironment,
    ): QuickBooksConnectionSetting {
        jdbcClient
            .sql(
                """
                insert into quickbooks_connection_setting
                    (connection_id, realm_id, company_name, environment, export_policy,
                     accounting_basis, default_currency, last_company_read_at, created_at, updated_at)
                values
                    (:connectionId, :realmId, :companyName, :environment, 'READ_ONLY',
                     'ACCRUAL', :currency, now(), now(), now())
                on conflict (connection_id) do update
                set realm_id = excluded.realm_id, company_name = excluded.company_name,
                    environment = excluded.environment, default_currency = excluded.default_currency,
                    last_company_read_at = now(), updated_at = now()
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .param("realmId", realmId.take(200))
            .param("companyName", companyName.take(300))
            .param("environment", environment.name)
            .param("currency", defaultCurrency?.uppercase()?.take(3))
            .update()
        return requireNotNull(findSetting(connectionId))
    }

    fun markAccountsRead(connectionId: UUID) {
        jdbcClient
            .sql(
                """
                insert into quickbooks_connection_setting
                    (connection_id, environment, export_policy, accounting_basis,
                     last_accounts_read_at, created_at, updated_at)
                values (:connectionId, 'SANDBOX', 'READ_ONLY', 'ACCRUAL', now(), now(), now())
                on conflict (connection_id) do update
                set last_accounts_read_at = now(), updated_at = now()
                """.trimIndent(),
            ).param("connectionId", connectionId)
            .update()
    }

    fun listMappings(connectionId: UUID): List<QuickBooksAccountMapping> =
        jdbcClient
            .sql(
                "select $MAPPING_COLUMNS from quickbooks_account_mapping where connection_id = :connectionId and active order by mapping_type",
            ).param("connectionId", connectionId)
            .query(::mapMapping)
            .list()

    fun replaceMapping(
        connectionId: UUID,
        mappingType: QuickBooksMappingType,
        accountId: String,
        accountName: String,
        accountType: String?,
        userId: UUID,
    ): QuickBooksAccountMapping {
        jdbcClient
            .sql(
                "update quickbooks_account_mapping set active = false, updated_at = now() where connection_id = :connectionId and mapping_type = :mappingType and active",
            ).param("connectionId", connectionId)
            .param("mappingType", mappingType.name)
            .update()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into quickbooks_account_mapping
                    (id, connection_id, mapping_type, external_account_id, external_account_name,
                     external_account_type, configured_by_user_id)
                values
                    (:id, :connectionId, :mappingType, :accountId, :accountName,
                     :accountType, :userId)
                """.trimIndent(),
            ).param("id", id)
            .param("connectionId", connectionId)
            .param("mappingType", mappingType.name)
            .param("accountId", accountId.take(200))
            .param("accountName", accountName.take(300))
            .param("accountType", accountType?.take(100))
            .param("userId", userId)
            .update()
        return jdbcClient
            .sql("select $MAPPING_COLUMNS from quickbooks_account_mapping where id = :id")
            .param("id", id)
            .query(::mapMapping)
            .single()
    }

    fun countExportCandidates(
        organizationId: UUID,
        start: LocalDate,
        end: LocalDate,
    ): QuickBooksExportCandidateCounts =
        jdbcClient
            .sql(
                """
                select
                    (select count(*) from contribution where organization_id = :organizationId and status = 'CONFIRMED' and confirmed_at::date between :start and :end) as contributions,
                    (select count(*) from sponsorship where organization_id = :organizationId and status in ('CONFIRMED','REFUNDED') and coalesce(confirmed_at, created_at)::date between :start and :end) as sponsorships,
                    (select count(*) from "order" where organization_id = :organizationId and status = 'CONFIRMED' and confirmed_at::date between :start and :end) as orders,
                    (select count(*) from fee_payment where organization_id = :organizationId and paid_at between :start and :end) as fee_payments,
                    (select count(*) from financial_correction where organization_id = :organizationId and created_at::date between :start and :end) as corrections
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("start", start)
            .param("end", end)
            .query {
                rs,
                _,
                ->
                QuickBooksExportCandidateCounts(
                    rs.getInt("contributions"),
                    rs.getInt("sponsorships"),
                    rs.getInt("orders"),
                    rs.getInt("fee_payments"),
                    rs.getInt("corrections"),
                )
            }.single()

    fun insertPreviewBatch(
        connectionId: UUID,
        organizationId: UUID,
        syncRunId: UUID?,
        start: LocalDate,
        end: LocalDate,
        candidateCount: Int,
        idempotencyKey: String,
        userId: UUID,
        blocked: Boolean,
    ): QuickBooksExportBatch {
        val existing = findBatchByKey(organizationId, idempotencyKey)
        if (existing != null) return existing
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into quickbooks_export_batch
                    (id, connection_id, organization_id, sync_run_id, status, period_start, period_end,
                     candidate_count, idempotency_key, requested_by_user_id)
                values
                    (:id, :connectionId, :organizationId, :syncRunId, :status, :periodStart, :periodEnd,
                     :candidateCount, :idempotencyKey, :userId)
                """.trimIndent(),
            ).param("id", id)
            .param("connectionId", connectionId)
            .param("organizationId", organizationId)
            .param("syncRunId", syncRunId)
            .param("status", if (blocked) "BLOCKED" else "PREVIEWED")
            .param("periodStart", start)
            .param("periodEnd", end)
            .param("candidateCount", candidateCount)
            .param("idempotencyKey", idempotencyKey.take(200))
            .param("userId", userId)
            .update()
        return requireNotNull(findBatch(id))
    }

    fun listBatches(
        organizationId: UUID,
        limit: Int = 20,
    ): List<QuickBooksExportBatch> =
        jdbcClient
            .sql(
                "select $BATCH_COLUMNS from quickbooks_export_batch where organization_id = :organizationId order by created_at desc limit :limit",
            ).param("organizationId", organizationId)
            .param("limit", limit)
            .query(::mapBatch)
            .list()

    private fun findBatch(id: UUID): QuickBooksExportBatch? =
        jdbcClient
            .sql("select $BATCH_COLUMNS from quickbooks_export_batch where id = :id")
            .param("id", id)
            .query(::mapBatch)
            .optional()
            .orElse(null)

    private fun findBatchByKey(
        organizationId: UUID,
        key: String,
    ): QuickBooksExportBatch? =
        jdbcClient
            .sql(
                "select $BATCH_COLUMNS from quickbooks_export_batch where organization_id = :organizationId and idempotency_key = :key",
            ).param("organizationId", organizationId)
            .param("key", key)
            .query(::mapBatch)
            .optional()
            .orElse(null)

    private fun mapSetting(
        rs: ResultSet,
        rowNum: Int,
    ) = QuickBooksConnectionSetting(
        rs.getObject("connection_id", UUID::class.java),
        rs.getString("realm_id"),
        rs.getString("company_name"),
        QuickBooksEnvironment.valueOf(rs.getString("environment")),
        QuickBooksExportPolicy.valueOf(rs.getString("export_policy")),
        QuickBooksAccountingBasis.valueOf(rs.getString("accounting_basis")),
        rs.getString("default_currency"),
        rs.getTimestamp("last_company_read_at")?.toInstant(),
        rs.getTimestamp("last_accounts_read_at")?.toInstant(),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapMapping(
        rs: ResultSet,
        rowNum: Int,
    ) = QuickBooksAccountMapping(
        rs.getObject("id", UUID::class.java),
        rs.getObject("connection_id", UUID::class.java),
        QuickBooksMappingType.valueOf(rs.getString("mapping_type")),
        rs.getString("external_account_id"),
        rs.getString("external_account_name"),
        rs.getString("external_account_type"),
        rs.getBoolean("active"),
        rs.getObject("configured_by_user_id", UUID::class.java),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapBatch(
        rs: ResultSet,
        rowNum: Int,
    ) = QuickBooksExportBatch(
        rs.getObject("id", UUID::class.java),
        rs.getObject("connection_id", UUID::class.java),
        rs.getObject("organization_id", UUID::class.java),
        rs.getObject("sync_run_id", UUID::class.java),
        QuickBooksExportStatus.valueOf(rs.getString("status")),
        rs.getDate("period_start").toLocalDate(),
        rs.getDate("period_end").toLocalDate(),
        rs.getInt("candidate_count"),
        rs.getInt("exported_count"),
        rs.getInt("failed_count"),
        rs.getObject("requested_by_user_id", UUID::class.java),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("completed_at")?.toInstant(),
    )

    private companion object {
        const val SETTING_COLUMNS =
            "connection_id, realm_id, company_name, environment, export_policy, accounting_basis, default_currency, " +
                "last_company_read_at, last_accounts_read_at, created_at, updated_at"
        const val MAPPING_COLUMNS =
            "id, connection_id, mapping_type, external_account_id, external_account_name, external_account_type, active, " +
                "configured_by_user_id, created_at, updated_at"
        const val BATCH_COLUMNS =
            "id, connection_id, organization_id, sync_run_id, status, period_start, period_end, candidate_count, exported_count, " +
                "failed_count, requested_by_user_id, created_at, completed_at"
    }
}
