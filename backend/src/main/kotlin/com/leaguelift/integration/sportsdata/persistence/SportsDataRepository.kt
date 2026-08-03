package com.leaguelift.integration.sportsdata.persistence

import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.sportsdata.domain.SportsDataEntityType
import com.leaguelift.integration.sportsdata.domain.SportsDataImportIssue
import com.leaguelift.integration.sportsdata.domain.SportsDataImportRun
import com.leaguelift.integration.sportsdata.domain.SportsDataImportStatus
import com.leaguelift.integration.sportsdata.domain.SportsDataIssueSeverity
import com.leaguelift.integration.sportsdata.domain.SportsDataMapping
import com.leaguelift.integration.sportsdata.domain.SportsDataSourceMode
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class SportsDataRepository(private val jdbcClient: JdbcClient) {
    fun findMapping(connectionId: UUID, entityType: SportsDataEntityType, externalId: String): SportsDataMapping? =
        jdbcClient.sql(
            "select $MAPPING_COLUMNS from sports_data_mapping where connection_id = :connectionId and entity_type = :entityType and external_entity_id = :externalId",
        )
            .param("connectionId", connectionId)
            .param("entityType", entityType.name)
            .param("externalId", externalId)
            .query(::mapMapping)
            .optional()
            .orElse(null)

    fun listMappings(connectionId: UUID): List<SportsDataMapping> =
        jdbcClient.sql("select $MAPPING_COLUMNS from sports_data_mapping where connection_id = :connectionId order by entity_type, external_entity_id")
            .param("connectionId", connectionId).query(::mapMapping).list()

    fun createPreviewRun(
        organizationId: UUID,
        connectionId: UUID?,
        syncRunId: UUID?,
        provider: IntegrationProvider,
        sourceMode: SportsDataSourceMode,
        status: SportsDataImportStatus,
        discoveredCount: Int,
        validCount: Int,
        duplicateCount: Int,
        conflictCount: Int,
        errorCount: Int,
        previewHash: String,
        userId: UUID,
    ): SportsDataImportRun {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into sports_data_import_run
                (id, organization_id, connection_id, provider, sync_run_id, source_mode, status,
                 commit_allowed, discovered_count, valid_count, duplicate_count,
                 conflict_count, error_count, preview_hash, requested_by_user_id, completed_at)
            values
                (:id, :organizationId, :connectionId, :provider, :syncRunId, :sourceMode, :status,
                 false, :discoveredCount, :validCount, :duplicateCount,
                 :conflictCount, :errorCount, :previewHash, :userId, now())
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .param("connectionId", connectionId)
            .param("provider", provider.name)
            .param("syncRunId", syncRunId)
            .param("sourceMode", sourceMode.name)
            .param("status", status.name)
            .param("discoveredCount", discoveredCount)
            .param("validCount", validCount)
            .param("duplicateCount", duplicateCount)
            .param("conflictCount", conflictCount)
            .param("errorCount", errorCount)
            .param("previewHash", previewHash)
            .param("userId", userId)
            .update()
        return requireNotNull(findRun(id))
    }

    fun addIssue(
        importRunId: UUID,
        rowNumber: Int?,
        entityType: SportsDataEntityType?,
        externalEntityId: String?,
        severity: SportsDataIssueSeverity,
        code: String,
        message: String,
    ): SportsDataImportIssue {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into sports_data_import_issue
                (id, import_run_id, row_number, entity_type, external_entity_id, severity, code, message)
            values (:id, :importRunId, :rowNumber, :entityType, :externalEntityId, :severity, :code, :message)
            """.trimIndent(),
        )
            .param("id", id)
            .param("importRunId", importRunId)
            .param("rowNumber", rowNumber)
            .param("entityType", entityType?.name)
            .param("externalEntityId", externalEntityId?.take(500))
            .param("severity", severity.name)
            .param("code", code.take(120))
            .param("message", message.take(1000))
            .update()
        return jdbcClient.sql("select $ISSUE_COLUMNS from sports_data_import_issue where id = :id")
            .param("id", id).query(::mapIssue).single()
    }

    fun listRuns(organizationId: UUID, limit: Int = 30): List<SportsDataImportRun> =
        jdbcClient.sql("select $RUN_COLUMNS from sports_data_import_run where organization_id = :organizationId order by requested_at desc limit :limit")
            .param("organizationId", organizationId).param("limit", limit).query(::mapRun).list()

    fun listIssues(importRunId: UUID): List<SportsDataImportIssue> =
        jdbcClient.sql("select $ISSUE_COLUMNS from sports_data_import_issue where import_run_id = :importRunId order by row_number nulls first, created_at")
            .param("importRunId", importRunId).query(::mapIssue).list()

    fun findRun(id: UUID): SportsDataImportRun? =
        jdbcClient.sql("select $RUN_COLUMNS from sports_data_import_run where id = :id")
            .param("id", id).query(::mapRun).optional().orElse(null)

    private fun mapMapping(rs: ResultSet, rowNum: Int) = SportsDataMapping(
        rs.getObject("id", UUID::class.java),
        rs.getObject("connection_id", UUID::class.java),
        IntegrationProvider.valueOf(rs.getString("provider")),
        SportsDataEntityType.valueOf(rs.getString("entity_type")),
        rs.getObject("internal_entity_id", UUID::class.java),
        rs.getString("external_entity_id"),
        rs.getString("external_parent_id"),
        rs.getString("external_hash"),
        rs.getString("mapping_json"),
        rs.getTimestamp("last_seen_at").toInstant(),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapRun(rs: ResultSet, rowNum: Int) = SportsDataImportRun(
        rs.getObject("id", UUID::class.java),
        rs.getObject("organization_id", UUID::class.java),
        rs.getObject("connection_id", UUID::class.java),
        IntegrationProvider.valueOf(rs.getString("provider")),
        rs.getObject("sync_run_id", UUID::class.java),
        SportsDataSourceMode.valueOf(rs.getString("source_mode")),
        SportsDataImportStatus.valueOf(rs.getString("status")),
        rs.getBoolean("commit_allowed"),
        rs.getInt("discovered_count"),
        rs.getInt("valid_count"),
        rs.getInt("duplicate_count"),
        rs.getInt("conflict_count"),
        rs.getInt("error_count"),
        rs.getString("preview_hash"),
        rs.getObject("requested_by_user_id", UUID::class.java),
        rs.getTimestamp("requested_at").toInstant(),
        rs.getTimestamp("completed_at")?.toInstant(),
    )

    private fun mapIssue(rs: ResultSet, rowNum: Int) = SportsDataImportIssue(
        rs.getObject("id", UUID::class.java),
        rs.getObject("import_run_id", UUID::class.java),
        (rs.getObject("row_number") as Number?)?.toInt(),
        rs.getString("entity_type")?.let(SportsDataEntityType::valueOf),
        rs.getString("external_entity_id"),
        SportsDataIssueSeverity.valueOf(rs.getString("severity")),
        rs.getString("code"),
        rs.getString("message"),
        rs.getTimestamp("created_at").toInstant(),
    )

    private companion object {
        const val MAPPING_COLUMNS = "id, connection_id, provider, entity_type, internal_entity_id, external_entity_id, external_parent_id, external_hash, mapping_json, last_seen_at, created_at, updated_at"
        const val RUN_COLUMNS = "id, organization_id, connection_id, provider, sync_run_id, source_mode, status, commit_allowed, discovered_count, valid_count, duplicate_count, conflict_count, error_count, preview_hash, requested_by_user_id, requested_at, completed_at"
        const val ISSUE_COLUMNS = "id, import_run_id, row_number, entity_type, external_entity_id, severity, code, message, created_at"
    }
}
