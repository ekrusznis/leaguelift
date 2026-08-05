package com.rally26.onboarding.domain

import java.util.UUID

enum class OnboardingRecordType { TEAM, HOUSEHOLD, GUARDIAN, PARTICIPANT }

enum class OnboardingPreviewAction { CREATE, UPDATE, MATCH_EXISTING, ERROR }

data class OnboardingPreviewRow(
    val rowNumber: Int,
    val recordType: OnboardingRecordType?,
    val externalId: String?,
    val label: String,
    val action: OnboardingPreviewAction,
    val warnings: List<String>,
    val errors: List<String>,
)

data class OnboardingImportPreview(
    val fileName: String?,
    val contentHash: String,
    val totalRows: Int,
    val validRows: Int,
    val errorRows: Int,
    val createCount: Int,
    val updateCount: Int,
    val matchCount: Int,
    val rows: List<OnboardingPreviewRow>,
)

data class ImportedOnboardingEntity(
    val recordType: OnboardingRecordType,
    val externalId: String,
    val entityId: UUID,
    val label: String,
    val action: OnboardingPreviewAction,
)

data class OnboardingImportResult(
    val fileName: String?,
    val contentHash: String,
    val createdCount: Int,
    val updatedCount: Int,
    val matchedCount: Int,
    val teamAssignmentsCreated: Int,
    val errors: List<OnboardingImportRowError>,
    val entities: List<ImportedOnboardingEntity>,
)

data class OnboardingImportRowError(
    val rowNumber: Int,
    val recordType: OnboardingRecordType?,
    val externalId: String?,
    val message: String,
)

enum class BulkActionStatus { SUCCEEDED, SKIPPED, FAILED }

data class BulkActionItemResult(
    val reference: String,
    val status: BulkActionStatus,
    val entityId: UUID? = null,
    val message: String? = null,
)

data class BulkActionResult(
    val requestedCount: Int,
    val succeededCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val items: List<BulkActionItemResult>,
)
