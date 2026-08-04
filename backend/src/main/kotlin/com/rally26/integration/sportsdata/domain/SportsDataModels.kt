package com.rally26.integration.sportsdata.domain

import com.rally26.integration.core.domain.IntegrationProvider
import java.time.Instant
import java.util.UUID

enum class SportsDataEntityType { ORGANIZATION, TEAM, PARTICIPANT, ROSTER_MEMBERSHIP, EVENT }
enum class SportsDataSourceMode { OAUTH, API_TOKEN, FILE_IMPORT, ICS }
enum class SportsDataImportStatus { PREVIEWED, READY, BLOCKED, IMPORTED, PARTIAL, FAILED }
enum class SportsDataIssueSeverity { WARNING, ERROR }

data class SportsDataExternalRecord(
    val entityType: SportsDataEntityType,
    val externalId: String,
    val externalParentId: String?,
    val name: String?,
    val payload: Map<String, String?>,
)

data class SportsDataMapping(
    val id: UUID,
    val connectionId: UUID,
    val provider: IntegrationProvider,
    val entityType: SportsDataEntityType,
    val internalEntityId: UUID?,
    val externalEntityId: String,
    val externalParentId: String?,
    val externalHash: String?,
    val mappingJson: String,
    val lastSeenAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SportsDataImportIssue(
    val id: UUID,
    val importRunId: UUID,
    val rowNumber: Int?,
    val entityType: SportsDataEntityType?,
    val externalEntityId: String?,
    val severity: SportsDataIssueSeverity,
    val code: String,
    val message: String,
    val createdAt: Instant,
)

data class SportsDataImportRun(
    val id: UUID,
    val organizationId: UUID,
    val connectionId: UUID?,
    val provider: IntegrationProvider,
    val syncRunId: UUID?,
    val sourceMode: SportsDataSourceMode,
    val status: SportsDataImportStatus,
    val commitAllowed: Boolean,
    val discoveredCount: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val conflictCount: Int,
    val errorCount: Int,
    val previewHash: String,
    val requestedByUserId: UUID,
    val requestedAt: Instant,
    val completedAt: Instant?,
)

data class SportsDataPreview(
    val run: SportsDataImportRun,
    val issues: List<SportsDataImportIssue>,
    val records: List<SportsDataExternalRecord>,
    val directImportEnabled: Boolean,
    val message: String,
)
