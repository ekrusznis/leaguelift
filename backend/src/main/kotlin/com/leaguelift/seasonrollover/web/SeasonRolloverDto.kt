package com.leaguelift.seasonrollover.web

import com.leaguelift.seasonrollover.domain.SeasonRolloverCommand
import com.leaguelift.seasonrollover.domain.SeasonRolloverPreview
import com.leaguelift.seasonrollover.domain.SeasonRolloverResult
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SeasonRolloverPreviewRequest(
	val sourceTeamId: UUID,
	@field:NotBlank @field:Size(max = 120)
	val newTeamName: String,
	@field:NotBlank @field:Size(max = 120)
	val newSeason: String,
	val archiveSourceTeam: Boolean = true,
	val copyRoster: Boolean = false,
	val copyStaff: Boolean = false,
	val copyBranding: Boolean = false,
) {
	fun toCommand() = SeasonRolloverCommand(
		sourceTeamId, newTeamName, newSeason, archiveSourceTeam, copyRoster, copyStaff, copyBranding,
	)
}

data class SeasonRolloverExecuteRequest(
	val sourceTeamId: UUID,
	@field:NotBlank @field:Size(max = 120)
	val newTeamName: String,
	@field:NotBlank @field:Size(max = 120)
	val newSeason: String,
	val archiveSourceTeam: Boolean = true,
	val copyRoster: Boolean = false,
	val copyStaff: Boolean = false,
	val copyBranding: Boolean = false,
	@field:NotBlank @field:Size(min = 64, max = 64)
	val expectedConfirmationHash: String,
) {
	fun toCommand() = SeasonRolloverCommand(
		sourceTeamId, newTeamName, newSeason, archiveSourceTeam, copyRoster, copyStaff, copyBranding,
	)
}

data class SeasonRolloverTeamResponse(
	val id: UUID?,
	val name: String,
	val sport: String,
	val season: String?,
	val contactEmail: String?,
)

data class SeasonRolloverRosterItemResponse(
	val participantId: UUID,
	val displayName: String,
	val priorJoinedAt: LocalDate?,
)

data class SeasonRolloverStaffItemResponse(
	val assignmentId: UUID,
	val userId: UUID,
	val displayName: String,
	val email: String,
	val role: String,
)

data class SeasonRolloverBrandingItemResponse(
	val assignmentId: UUID,
	val assetId: UUID,
	val usageSlot: String,
	val fileName: String,
	val publicationStatus: String,
	val visibility: String,
	val altText: String?,
)

data class SeasonRolloverPreviewResponse(
	val confirmationHash: String,
	val sourceTeam: SeasonRolloverTeamResponse,
	val destinationTeam: SeasonRolloverTeamResponse,
	val archiveSourceTeam: Boolean,
	val roster: List<SeasonRolloverRosterItemResponse>,
	val staff: List<SeasonRolloverStaffItemResponse>,
	val branding: List<SeasonRolloverBrandingItemResponse>,
	val warnings: List<String>,
	val excludedData: List<String>,
)

data class SeasonRolloverResultResponse(
	val runId: UUID,
	val confirmationHash: String,
	val sourceTeamId: UUID,
	val destinationTeam: SeasonRolloverTeamResponse,
	val sourceArchived: Boolean,
	val rosterCopiedCount: Int,
	val staffCopiedCount: Int,
	val brandingCopiedCount: Int,
	val completedAt: Instant,
)

fun SeasonRolloverPreview.toResponse() = SeasonRolloverPreviewResponse(
	confirmationHash = confirmationHash,
	sourceTeam = SeasonRolloverTeamResponse(sourceTeam.id, sourceTeam.name, sourceTeam.sport, sourceTeam.season, sourceTeam.contactEmail),
	destinationTeam = SeasonRolloverTeamResponse(null, destinationTeam.name, destinationTeam.sport, destinationTeam.season, destinationTeam.contactEmail),
	archiveSourceTeam = archiveSourceTeam,
	roster = roster.map { SeasonRolloverRosterItemResponse(it.participantId, it.displayName, it.joinedAt) },
	staff = staff.map { SeasonRolloverStaffItemResponse(it.assignmentId, it.userId, it.displayName, it.email, it.role.name) },
	branding = branding.map {
		SeasonRolloverBrandingItemResponse(
			it.assignmentId, it.assetId, it.usageSlot, it.fileName, it.publicationStatus.name, it.visibility.name, it.altText,
		)
	},
	warnings = warnings,
	excludedData = excludedData,
)

fun SeasonRolloverResult.toResponse() = SeasonRolloverResultResponse(
	runId = runId,
	confirmationHash = confirmationHash,
	sourceTeamId = sourceTeamId,
	destinationTeam = SeasonRolloverTeamResponse(
		destinationTeam.id, destinationTeam.name, destinationTeam.sport, destinationTeam.season, destinationTeam.contactEmail,
	),
	sourceArchived = sourceArchived,
	rosterCopiedCount = rosterCopiedCount,
	staffCopiedCount = staffCopiedCount,
	brandingCopiedCount = brandingCopiedCount,
	completedAt = completedAt,
)
