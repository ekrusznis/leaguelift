package com.rally26.onboarding.web

import com.rally26.membership.domain.MembershipRole
import com.rally26.onboarding.application.BulkInvitationCommand
import com.rally26.onboarding.domain.BulkActionResult
import com.rally26.onboarding.domain.ImportedOnboardingEntity
import com.rally26.onboarding.domain.OnboardingImportPreview
import com.rally26.onboarding.domain.OnboardingImportResult
import com.rally26.onboarding.domain.OnboardingImportRowError
import com.rally26.onboarding.domain.OnboardingPreviewRow
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class OnboardingCsvPreviewRequest(
    @field:Size(max = 255)
    val fileName: String? = null,
    @field:NotBlank
    val csvContent: String,
)

data class OnboardingCsvExecuteRequest(
    @field:Size(max = 255)
    val fileName: String? = null,
    @field:NotBlank
    val csvContent: String,
    @field:NotBlank
    @field:Size(min = 64, max = 64)
    val expectedContentHash: String,
)

data class OnboardingPreviewRowResponse(
    val rowNumber: Int,
    val recordType: String?,
    val externalId: String?,
    val label: String,
    val action: String,
    val warnings: List<String>,
    val errors: List<String>,
)

data class OnboardingImportPreviewResponse(
    val fileName: String?,
    val contentHash: String,
    val totalRows: Int,
    val validRows: Int,
    val errorRows: Int,
    val createCount: Int,
    val updateCount: Int,
    val matchCount: Int,
    val rows: List<OnboardingPreviewRowResponse>,
)

data class ImportedOnboardingEntityResponse(
    val recordType: String,
    val externalId: String,
    val entityId: UUID,
    val label: String,
    val action: String,
)

data class OnboardingImportRowErrorResponse(
    val rowNumber: Int,
    val recordType: String?,
    val externalId: String?,
    val message: String,
)

data class OnboardingImportResultResponse(
    val fileName: String?,
    val contentHash: String,
    val createdCount: Int,
    val updatedCount: Int,
    val matchedCount: Int,
    val teamAssignmentsCreated: Int,
    val errors: List<OnboardingImportRowErrorResponse>,
    val entities: List<ImportedOnboardingEntityResponse>,
)

data class BulkInvitationItemRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    val role: MembershipRole,
) {
    fun toCommand() = BulkInvitationCommand(email, role)
}

data class BulkInvitationRequest(
    @field:NotEmpty
    @field:Size(max = 500)
    @field:Valid
    val items: List<BulkInvitationItemRequest>,
)

data class BulkTeamAssignmentRequest(
    val teamId: UUID,
    @field:NotEmpty
    @field:Size(max = 1_000)
    val participantIds: List<UUID>,
)

data class BulkFeeAssignmentRequest(
    val feeTemplateId: UUID,
    @field:NotEmpty
    @field:Size(max = 1_000)
    val householdIds: List<UUID>,
    val dueDate: LocalDate? = null,
)

data class BulkDocumentAssignmentRequest(
    val assetId: UUID,
    @field:NotEmpty
    @field:Size(max = 1_000)
    val householdIds: List<UUID>,
    @field:Size(max = 200)
    val title: String? = null,
)

data class BulkActionItemResponse(
    val reference: String,
    val status: String,
    val entityId: UUID?,
    val message: String?,
)

data class BulkActionResponse(
    val requestedCount: Int,
    val succeededCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val items: List<BulkActionItemResponse>,
)

fun OnboardingPreviewRow.toResponse() =
    OnboardingPreviewRowResponse(
        rowNumber,
        recordType?.name,
        externalId,
        label,
        action.name,
        warnings,
        errors,
    )

fun OnboardingImportPreview.toResponse() =
    OnboardingImportPreviewResponse(
        fileName,
        contentHash,
        totalRows,
        validRows,
        errorRows,
        createCount,
        updateCount,
        matchCount,
        rows.map { it.toResponse() },
    )

fun ImportedOnboardingEntity.toResponse() =
    ImportedOnboardingEntityResponse(
        recordType.name,
        externalId,
        entityId,
        label,
        action.name,
    )

fun OnboardingImportRowError.toResponse() =
    OnboardingImportRowErrorResponse(
        rowNumber,
        recordType?.name,
        externalId,
        message,
    )

fun OnboardingImportResult.toResponse() =
    OnboardingImportResultResponse(
        fileName,
        contentHash,
        createdCount,
        updatedCount,
        matchedCount,
        teamAssignmentsCreated,
        errors.map { it.toResponse() },
        entities.map { it.toResponse() },
    )

fun BulkActionResult.toResponse() =
    BulkActionResponse(
        requestedCount,
        succeededCount,
        skippedCount,
        failedCount,
        items.map { BulkActionItemResponse(it.reference, it.status.name, it.entityId, it.message) },
    )
