package com.rally26.seasonrollover.domain

import com.rally26.authorization.domain.ResourceRole
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SeasonRolloverCommand(
	val sourceTeamId: UUID,
	val newTeamName: String,
	val newSeason: String,
	val archiveSourceTeam: Boolean,
	val copyRoster: Boolean,
	val copyStaff: Boolean,
	val copyBranding: Boolean,
)

data class SeasonRolloverTeamSummary(
	val id: UUID,
	val name: String,
	val sport: String,
	val season: String?,
	val contactEmail: String?,
)

data class SeasonRolloverRosterItem(
	val participantId: UUID,
	val displayName: String,
	val joinedAt: LocalDate?,
	val participantUpdatedAt: Instant,
	val assignmentUpdatedAt: Instant,
)

data class SeasonRolloverStaffItem(
	val assignmentId: UUID,
	val userId: UUID,
	val displayName: String,
	val email: String,
	val role: ResourceRole,
	val assignmentUpdatedAt: Instant,
	val userUpdatedAt: Instant,
)

data class SeasonRolloverBrandingItem(
	val assignmentId: UUID,
	val assetId: UUID,
	val usageSlot: String,
	val fileName: String,
	val publicationStatus: PublicationStatus,
	val visibility: Visibility,
	val altText: String?,
	val assignmentUpdatedAt: Instant,
	val assetUpdatedAt: Instant,
)

data class SeasonRolloverPreview(
	val confirmationHash: String,
	val sourceTeam: SeasonRolloverTeamSummary,
	val destinationTeam: SeasonRolloverTeamSummary,
	val archiveSourceTeam: Boolean,
	val roster: List<SeasonRolloverRosterItem>,
	val staff: List<SeasonRolloverStaffItem>,
	val branding: List<SeasonRolloverBrandingItem>,
	val warnings: List<String>,
	val excludedData: List<String>,
)

data class SeasonRolloverRun(
	val id: UUID,
	val organizationId: UUID,
	val sourceTeamId: UUID,
	val destinationTeamId: UUID,
	val confirmationHash: String,
	val archiveSourceTeam: Boolean,
	val copyRoster: Boolean,
	val copyStaff: Boolean,
	val copyBranding: Boolean,
	val rosterCopiedCount: Int,
	val staffCopiedCount: Int,
	val brandingCopiedCount: Int,
	val executedByUserId: UUID,
	val createdAt: Instant,
)

data class SeasonRolloverResult(
	val runId: UUID,
	val confirmationHash: String,
	val sourceTeamId: UUID,
	val destinationTeam: SeasonRolloverTeamSummary,
	val sourceArchived: Boolean,
	val rosterCopiedCount: Int,
	val staffCopiedCount: Int,
	val brandingCopiedCount: Int,
	val completedAt: Instant,
)
