package com.rally26.integration.sportsdata.web

import com.rally26.integration.core.web.IntegrationCatalogResponse
import com.rally26.integration.core.web.toResponse
import com.rally26.integration.sportsdata.application.SportsDataOverview
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import com.rally26.integration.sportsdata.domain.SportsDataExternalRecord
import com.rally26.integration.sportsdata.domain.SportsDataImportIssue
import com.rally26.integration.sportsdata.domain.SportsDataImportRun
import com.rally26.integration.sportsdata.domain.SportsDataPreview
import java.time.Instant
import java.util.UUID

data class SportsDataOverviewResponse(
    val providers: List<IntegrationCatalogResponse>,
    val recentRuns: List<SportsDataImportRunResponse>,
    val directProviderImportEnabled: Boolean,
    val reviewedFileImportAvailable: Boolean,
)

data class SportsDataExternalRecordRequest(
    val entityType: String,
    val externalId: String,
    val externalParentId: String? = null,
    val name: String? = null,
    val payload: Map<String, String?> = emptyMap(),
)

data class SportsDataFilePreviewRequest(
    val provider: String,
    val records: List<SportsDataExternalRecordRequest>,
)

data class SportsDataExternalRecordResponse(
    val entityType: String,
    val externalId: String,
    val externalParentId: String?,
    val name: String?,
    val payload: Map<String, String?>,
)

data class SportsDataImportRunResponse(
    val id: UUID,
    val provider: String,
    val sourceMode: String,
    val status: String,
    val commitAllowed: Boolean,
    val discoveredCount: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val conflictCount: Int,
    val errorCount: Int,
    val previewHash: String,
    val requestedAt: Instant,
    val completedAt: Instant?,
)

data class SportsDataImportIssueResponse(
    val id: UUID,
    val rowNumber: Int?,
    val entityType: String?,
    val externalEntityId: String?,
    val severity: String,
    val code: String,
    val message: String,
)

data class SportsDataPreviewResponse(
    val run: SportsDataImportRunResponse,
    val issues: List<SportsDataImportIssueResponse>,
    val records: List<SportsDataExternalRecordResponse>,
    val directImportEnabled: Boolean,
    val message: String,
)

fun SportsDataOverview.toResponse() =
    SportsDataOverviewResponse(
        providers.map {
            it.toResponse()
        },
        recentRuns.map { it.toResponse() },
        directProviderImportEnabled,
        reviewedFileImportAvailable,
    )

fun SportsDataImportRun.toResponse() =
    SportsDataImportRunResponse(
        id,
        provider.name,
        sourceMode.name,
        status.name,
        commitAllowed,
        discoveredCount,
        validCount,
        duplicateCount,
        conflictCount,
        errorCount,
        previewHash,
        requestedAt,
        completedAt,
    )

fun SportsDataImportIssue.toResponse() =
    SportsDataImportIssueResponse(id, rowNumber, entityType?.name, externalEntityId, severity.name, code, message)

fun SportsDataExternalRecord.toResponse() = SportsDataExternalRecordResponse(entityType.name, externalId, externalParentId, name, payload)

fun SportsDataPreview.toResponse() =
    SportsDataPreviewResponse(
        run.toResponse(),
        issues.map {
            it.toResponse()
        },
        records.map { it.toResponse() },
        directImportEnabled,
        message,
    )

fun SportsDataExternalRecordRequest.toDomain() =
    SportsDataExternalRecord(
        runCatching {
            SportsDataEntityType.valueOf(entityType.uppercase())
        }.getOrElse {
            throw com.rally26.common.error
                .ValidationException("Unknown sports-data entity type.")
        },
        externalId,
        externalParentId,
        name,
        payload,
    )
