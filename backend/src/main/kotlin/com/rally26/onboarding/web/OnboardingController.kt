package com.rally26.onboarding.web

import com.rally26.common.web.CurrentUser
import com.rally26.onboarding.application.BulkOnboardingService
import com.rally26.onboarding.application.OnboardingImportService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/onboarding")
class OnboardingController(
    private val onboardingImportService: OnboardingImportService,
    private val bulkOnboardingService: BulkOnboardingService,
) {
    @PostMapping("/imports/preview")
    fun previewImport(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: OnboardingCsvPreviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OnboardingImportPreviewResponse =
        onboardingImportService.preview(organizationId, request.fileName, request.csvContent, currentUser).toResponse()

    @PostMapping("/imports/execute")
    fun executeImport(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: OnboardingCsvExecuteRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OnboardingImportResultResponse =
        onboardingImportService
            .execute(
                organizationId,
                request.fileName,
                request.csvContent,
                request.expectedContentHash,
                currentUser,
            ).toResponse()

    @PostMapping("/bulk-invitations")
    fun bulkInvitations(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: BulkInvitationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BulkActionResponse =
        bulkOnboardingService.inviteStaff(organizationId, request.items.map { it.toCommand() }, currentUser).toResponse()

    @PostMapping("/bulk-team-assignments")
    fun bulkTeamAssignments(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: BulkTeamAssignmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BulkActionResponse =
        bulkOnboardingService
            .assignParticipantsToTeam(
                organizationId,
                request.teamId,
                request.participantIds,
                currentUser,
            ).toResponse()

    @PostMapping("/bulk-fee-assignments")
    fun bulkFeeAssignments(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: BulkFeeAssignmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BulkActionResponse =
        bulkOnboardingService
            .assignFeeTemplate(
                organizationId,
                request.feeTemplateId,
                request.householdIds,
                request.dueDate,
                currentUser,
            ).toResponse()

    @PostMapping("/bulk-document-assignments")
    fun bulkDocumentAssignments(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: BulkDocumentAssignmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): BulkActionResponse =
        bulkOnboardingService
            .assignDocument(
                organizationId,
                request.assetId,
                request.householdIds,
                request.title,
                currentUser,
            ).toResponse()
}
