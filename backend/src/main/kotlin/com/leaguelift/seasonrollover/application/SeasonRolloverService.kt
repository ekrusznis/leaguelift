package com.leaguelift.seasonrollover.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.seasonrollover.domain.SeasonRolloverCommand
import com.leaguelift.seasonrollover.domain.SeasonRolloverPreview
import com.leaguelift.seasonrollover.domain.SeasonRolloverResult
import com.leaguelift.seasonrollover.domain.SeasonRolloverRun
import com.leaguelift.seasonrollover.domain.SeasonRolloverTeamSummary
import com.leaguelift.seasonrollover.persistence.SeasonRolloverRepository
import com.leaguelift.team.application.TeamService
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

private const val MAX_TEAM_NAME_LENGTH = 120
private const val MAX_SEASON_LENGTH = 120

private val EXCLUDED_DATA = listOf(
	"Financial history, balances, fee assignments, payments, adjustments, credits, ledger entries, and payouts",
	"Orders, contributions, sponsorship purchases, refunds, and fulfillment history",
	"Events, imported-event identities, event templates, and RSVP responses",
	"Households, guardian relationships, invitations, credentials, athlete access, and consent or authorization assumptions",
	"Public pages, campaigns, stores, products, documents, integrations, and provider connections",
)

@Service
class SeasonRolloverService(
	private val repository: SeasonRolloverRepository,
	private val teamRepository: TeamRepository,
	private val teamService: TeamService,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val objectMapper: ObjectMapper,
) {

	fun preview(organizationId: UUID, command: SeasonRolloverCommand, currentUser: CurrentUser): SeasonRolloverPreview {
		membershipService.requireManagerRole(organizationId, currentUser)
		return analyze(organizationId, normalize(command))
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	fun execute(
		organizationId: UUID,
		command: SeasonRolloverCommand,
		expectedConfirmationHash: String,
		currentUser: CurrentUser,
	): SeasonRolloverResult {
		membershipService.requireManagerRole(organizationId, currentUser)
		val normalizedHash = expectedConfirmationHash.trim().lowercase()
		repository.findRunByHash(organizationId, normalizedHash)?.let { return completedResult(organizationId, it) }

		val normalized = normalize(command)
		val preview = analyze(organizationId, normalized)
		if (preview.confirmationHash != normalizedHash) {
			throw ConflictException(
				"SEASON_ROLLOVER_PREVIEW_STALE",
				"The source team or selected setup data changed after preview. Preview the rollover again before continuing.",
			)
		}

		val destination = teamService.create(
			organizationId = organizationId,
			name = normalized.newTeamName,
			sport = preview.sourceTeam.sport,
			season = normalized.newSeason,
			contactEmail = preview.sourceTeam.contactEmail,
			currentUser = currentUser,
		)

		val rosterCopied = if (normalized.copyRoster) {
			repository.copyRoster(organizationId, normalized.sourceTeamId, destination.id)
		} else 0
		val staffCopied = if (normalized.copyStaff) {
			repository.copyStaff(organizationId, normalized.sourceTeamId, destination.id, currentUser.userId)
		} else 0
		val brandingCopied = if (normalized.copyBranding) {
			repository.copyBranding(organizationId, normalized.sourceTeamId, destination.id)
		} else 0

		if (rosterCopied != preview.roster.size || staffCopied != preview.staff.size || brandingCopied != preview.branding.size) {
			throw ConflictException(
				"SEASON_ROLLOVER_SOURCE_CHANGED",
				"The selected setup data changed while the rollover was executing. No rollover was completed.",
			)
		}

		if (normalized.archiveSourceTeam) {
			teamService.archive(organizationId, normalized.sourceTeamId, currentUser)
		}

		val run = repository.insertRun(
			organizationId = organizationId,
			sourceTeamId = normalized.sourceTeamId,
			destinationTeamId = destination.id,
			confirmationHash = preview.confirmationHash,
			archiveSourceTeam = normalized.archiveSourceTeam,
			copyRoster = normalized.copyRoster,
			copyStaff = normalized.copyStaff,
			copyBranding = normalized.copyBranding,
			rosterCopiedCount = rosterCopied,
			staffCopiedCount = staffCopied,
			brandingCopiedCount = brandingCopied,
			executedByUserId = currentUser.userId,
		)

		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "season_rollover.executed",
			entityType = "season_rollover_run",
			entityId = run.id,
			metadataJson = objectMapper.writeValueAsString(
				mapOf(
					"sourceTeamId" to normalized.sourceTeamId,
					"destinationTeamId" to destination.id,
					"archiveSourceTeam" to normalized.archiveSourceTeam,
					"copyRoster" to normalized.copyRoster,
					"copyStaff" to normalized.copyStaff,
					"copyBranding" to normalized.copyBranding,
					"rosterCopiedCount" to rosterCopied,
					"staffCopiedCount" to staffCopied,
					"brandingCopiedCount" to brandingCopied,
				),
			),
		)

		return result(run, destination)
	}

	private fun analyze(organizationId: UUID, command: SeasonRolloverCommand): SeasonRolloverPreview {
		val source = teamRepository.findById(command.sourceTeamId, organizationId)
			?: throw NotFoundException("TEAM_NOT_FOUND", "The source team could not be found.")
		if (source.status != TeamStatus.ACTIVE) {
			throw ValidationException("Only an active team can be rolled into a new season.")
		}
		if (source.name.equals(command.newTeamName, ignoreCase = true)) {
			throw ValidationException("The new team name must differ from the source team name because archived team names remain reserved.")
		}
		if (repository.teamNameExists(organizationId, command.newTeamName)) {
			throw ConflictException("TEAM_NAME_TAKEN", "A team with this name already exists in the organization.")
		}

		val roster = if (command.copyRoster) repository.listRoster(organizationId, source.id) else emptyList()
		val staff = if (command.copyStaff) repository.listStaff(organizationId, source.id) else emptyList()
		val branding = if (command.copyBranding) repository.listBranding(organizationId, source.id) else emptyList()
		val sourceSummary = source.toSummary()
		val destinationSummary = SeasonRolloverTeamSummary(
			id = UUID(0L, 0L),
			name = command.newTeamName,
			sport = source.sport,
			season = command.newSeason,
			contactEmail = source.contactEmail,
		)
		val confirmationHash = hash(
			organizationId,
			command,
			source,
			roster.map {
				listOf(it.participantId, it.displayName, it.joinedAt, it.participantUpdatedAt, it.assignmentUpdatedAt)
			},
			staff.map {
				listOf(it.assignmentId, it.userId, it.displayName, it.email, it.role.name, it.assignmentUpdatedAt, it.userUpdatedAt)
			},
			branding.map {
				listOf(
					it.assignmentId, it.assetId, it.usageSlot, it.fileName, it.publicationStatus.name,
					it.visibility.name, it.altText, it.assignmentUpdatedAt, it.assetUpdatedAt,
				)
			},
		)
		return SeasonRolloverPreview(
			confirmationHash = confirmationHash,
			sourceTeam = sourceSummary,
			destinationTeam = destinationSummary,
			archiveSourceTeam = command.archiveSourceTeam,
			roster = roster,
			staff = staff,
			branding = branding,
			warnings = buildList {
				if (command.copyRoster) add("Roster copy reuses active participant records and creates new team links; prior joined dates are not carried forward.")
				if (command.copyStaff) add("Staff copy includes only explicit active TEAM role assignments. Organization owner/administrator inheritance already applies automatically.")
				if (command.copyBranding) add("Branding copy reuses the current ready logo/cover assets; it does not duplicate uploaded files.")
				if (command.archiveSourceTeam) add("Archiving changes only the source team's status. Its historical records remain intact and are not moved or deleted.")
			},
			excludedData = EXCLUDED_DATA,
		)
	}

	private fun normalize(command: SeasonRolloverCommand): SeasonRolloverCommand {
		val name = command.newTeamName.trim()
		val season = command.newSeason.trim()
		if (name.isBlank()) throw ValidationException("New team name is required.")
		if (name.length > MAX_TEAM_NAME_LENGTH) throw ValidationException("New team name must be $MAX_TEAM_NAME_LENGTH characters or fewer.")
		if (season.isBlank()) throw ValidationException("New season is required.")
		if (season.length > MAX_SEASON_LENGTH) throw ValidationException("New season must be $MAX_SEASON_LENGTH characters or fewer.")
		return command.copy(newTeamName = name, newSeason = season)
	}

	private fun hash(
		organizationId: UUID,
		command: SeasonRolloverCommand,
		source: Team,
		roster: List<List<Any?>>,
		staff: List<List<Any?>>,
		branding: List<List<Any?>>,
	): String {
		val payload = linkedMapOf<String, Any?>(
			"organizationId" to organizationId.toString(),
			"sourceTeam" to listOf(
				source.id.toString(), source.name, source.sport, source.season, source.status.name,
				source.contactEmail, source.createdAt.toString(), source.updatedAt.toString(),
			),
			"newTeamName" to command.newTeamName,
			"newSeason" to command.newSeason,
			"archiveSourceTeam" to command.archiveSourceTeam,
			"copyRoster" to command.copyRoster,
			"copyStaff" to command.copyStaff,
			"copyBranding" to command.copyBranding,
			"roster" to roster.map { row -> row.map { stableValue(it) } },
			"staff" to staff.map { row -> row.map { stableValue(it) } },
			"branding" to branding.map { row -> row.map { stableValue(it) } },
		)
		return MessageDigest.getInstance("SHA-256")
			.digest(objectMapper.writeValueAsBytes(payload))
			.joinToString("") { "%02x".format(it) }
	}

	private fun stableValue(value: Any?): Any? = when (value) {
		is UUID -> value.toString()
		is java.time.temporal.TemporalAccessor -> value.toString()
		else -> value
	}

	private fun completedResult(organizationId: UUID, run: SeasonRolloverRun): SeasonRolloverResult {
		val destination = teamRepository.findById(run.destinationTeamId, organizationId)
			?: throw NotFoundException("TEAM_NOT_FOUND", "The rollover destination team could not be found.")
		return result(run, destination)
	}

	private fun result(run: SeasonRolloverRun, destination: Team) = SeasonRolloverResult(
		runId = run.id,
		confirmationHash = run.confirmationHash,
		sourceTeamId = run.sourceTeamId,
		destinationTeam = destination.toSummary(),
		sourceArchived = run.archiveSourceTeam,
		rosterCopiedCount = run.rosterCopiedCount,
		staffCopiedCount = run.staffCopiedCount,
		brandingCopiedCount = run.brandingCopiedCount,
		completedAt = run.createdAt,
	)

	private fun Team.toSummary() = SeasonRolloverTeamSummary(id, name, sport, season, contactEmail)
}
