package com.leaguelift.onboarding.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ApiException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.document.application.DocumentService
import com.leaguelift.fee.application.FeeService
import com.leaguelift.fee.domain.FeeTemplateStatus
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.household.domain.HouseholdStatus
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.invitation.application.InvitationService
import com.leaguelift.invitation.persistence.InvitationRepository
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.persistence.MediaAssignmentRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.onboarding.domain.BulkActionItemResult
import com.leaguelift.onboarding.domain.BulkActionResult
import com.leaguelift.onboarding.domain.BulkActionStatus
import com.leaguelift.participant.domain.ParticipantStatus
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class BulkInvitationCommand(val email: String, val role: MembershipRole)

/**
 * Phase 16 slice 2 bulk operations. Every operation reuses the existing domain
 * service or repository invariant rather than introducing a generic table editor.
 * Per-item results let a pilot admin correct only failed rows without repeating work
 * that already succeeded.
 */
@Service
class BulkOnboardingService(
	private val invitationService: InvitationService,
	private val invitationRepository: InvitationRepository,
	private val participantRepository: ParticipantRepository,
	private val teamRepository: TeamRepository,
	private val feeService: FeeService,
	private val feeRepository: FeeRepository,
	private val documentService: DocumentService,
	private val mediaAssignmentRepository: MediaAssignmentRepository,
	private val householdRepository: HouseholdRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val objectMapper: ObjectMapper,
) {

	fun inviteStaff(
		organizationId: UUID,
		commands: List<BulkInvitationCommand>,
		currentUser: CurrentUser,
	): BulkActionResult {
		membershipService.requireManagerRole(organizationId, currentUser)
		val seen = mutableSetOf<String>()
		val results = commands.map { command ->
			val email = command.email.trim().lowercase()
			when {
				email.isBlank() ->
					BulkActionItemResult(command.email, BulkActionStatus.FAILED, message = "Email is required.")
				!seen.add(email) ->
					BulkActionItemResult(email, BulkActionStatus.SKIPPED, message = "Duplicate email in this request.")
				invitationRepository.findPendingForOrganizationAndEmail(organizationId, email) != null ->
					BulkActionItemResult(email, BulkActionStatus.SKIPPED, message = "A pending invitation already exists.")
				else -> {
					try {
						val created = invitationService.invite(organizationId, email, command.role, currentUser)
						BulkActionItemResult(
							reference = email,
							status = BulkActionStatus.SUCCEEDED,
							entityId = created.invitation.id,
						)
					} catch (error: ApiException) {
						BulkActionItemResult(email, BulkActionStatus.FAILED, message = error.message)
					}
				}
			}
		}
		recordSummary(currentUser, organizationId, "onboarding.bulk_staff_invitations", results)
		return results.toBulkResult()
	}

	@Transactional
	fun assignParticipantsToTeam(
		organizationId: UUID,
		teamId: UUID,
		participantIds: List<UUID>,
		currentUser: CurrentUser,
	): BulkActionResult {
		membershipService.requireManagerRole(organizationId, currentUser)
		val team = teamRepository.findById(teamId, organizationId)
		if (team == null || team.status != TeamStatus.ACTIVE) {
			val results = participantIds.map {
				BulkActionItemResult(it.toString(), BulkActionStatus.FAILED, message = "The team could not be found.")
			}
			recordSummary(currentUser, organizationId, "onboarding.bulk_team_assignments", results, mapOf("teamId" to teamId))
			return results.toBulkResult()
		}
		val results = participantIds.distinct().map { participantId ->
			val participant = participantRepository.findById(participantId, organizationId)
			if (participant == null || participant.status != ParticipantStatus.ACTIVE) {
				BulkActionItemResult(participantId.toString(), BulkActionStatus.FAILED, message = "The active participant could not be found.")
			} else {
				val created = participantRepository.ensureTeamAssignment(participantId, teamId, organizationId, joinedAt = null)
				BulkActionItemResult(
					reference = "${participant.firstName} ${participant.lastName}",
					status = if (created) BulkActionStatus.SUCCEEDED else BulkActionStatus.SKIPPED,
					entityId = participantId,
					message = if (created) "Assigned to ${team.name}." else "Already assigned to ${team.name}.",
				)
			}
		}
		recordSummary(currentUser, organizationId, "onboarding.bulk_team_assignments", results, mapOf("teamId" to teamId))
		return results.toBulkResult()
	}

	fun assignFeeTemplate(
		organizationId: UUID,
		feeTemplateId: UUID,
		householdIds: List<UUID>,
		dueDate: LocalDate?,
		currentUser: CurrentUser,
	): BulkActionResult {
		membershipService.requireManagerRole(organizationId, currentUser)
		val template = try {
			feeService.getTemplate(organizationId, feeTemplateId, currentUser)
		} catch (error: ApiException) {
			val results = householdIds.map {
				BulkActionItemResult(it.toString(), BulkActionStatus.FAILED, message = error.message)
			}
			recordSummary(
				currentUser,
				organizationId,
				"onboarding.bulk_fee_assignments",
				results,
				mapOf("feeTemplateId" to feeTemplateId, "dueDate" to dueDate),
			)
			return results.toBulkResult()
		}
		if (template.status != FeeTemplateStatus.ACTIVE) {
			val results = householdIds.distinct().map {
				BulkActionItemResult(it.toString(), BulkActionStatus.FAILED, message = "The fee template is archived.")
			}
			recordSummary(
				currentUser,
				organizationId,
				"onboarding.bulk_fee_assignments",
				results,
				mapOf("feeTemplateId" to feeTemplateId, "dueDate" to dueDate),
			)
			return results.toBulkResult()
		}
		val results = householdIds.distinct().map { householdId ->
			val household = householdRepository.findById(householdId, organizationId)
			when {
				household == null || household.status != HouseholdStatus.ACTIVE ->
					BulkActionItemResult(householdId.toString(), BulkActionStatus.FAILED, message = "The active household could not be found.")
				feeRepository.hasActiveTemplateAssignment(organizationId, householdId, feeTemplateId, dueDate) ->
					BulkActionItemResult(household.displayName, BulkActionStatus.SKIPPED, message = "This fee is already assigned for the selected due date.")
				else -> {
					try {
						val assignment = feeService.createAssignment(
							organizationId = organizationId,
							householdId = householdId,
							participantId = null,
							feeTemplateId = template.id,
							description = template.name,
							originalAmountMinor = template.amountMinor,
							currency = template.currency,
							dueDate = dueDate,
							currentUser = currentUser,
						)
						BulkActionItemResult(household.displayName, BulkActionStatus.SUCCEEDED, entityId = assignment.assignment.id)
					} catch (error: ApiException) {
						BulkActionItemResult(household.displayName, BulkActionStatus.FAILED, message = error.message)
					}
				}
			}
		}
		recordSummary(
			currentUser,
			organizationId,
			"onboarding.bulk_fee_assignments",
			results,
			mapOf("feeTemplateId" to feeTemplateId, "dueDate" to dueDate),
		)
		return results.toBulkResult()
	}

	fun assignDocument(
		organizationId: UUID,
		assetId: UUID,
		householdIds: List<UUID>,
		title: String?,
		currentUser: CurrentUser,
	): BulkActionResult {
		membershipService.requireManagerRole(organizationId, currentUser)
		val results = householdIds.distinct().map { householdId ->
			val household = householdRepository.findById(householdId, organizationId)
			when {
				household == null || household.status != HouseholdStatus.ACTIVE ->
					BulkActionItemResult(householdId.toString(), BulkActionStatus.FAILED, message = "The active household could not be found.")
				mediaAssignmentRepository.findActiveByAssetAndEntity(
					organizationId,
					assetId,
					MediaEntityType.HOUSEHOLD,
					householdId,
					MediaUsageSlot.DOCUMENT,
				) != null ->
					BulkActionItemResult(household.displayName, BulkActionStatus.SKIPPED, message = "This document is already assigned.")
				else -> {
					try {
						val assignment = documentService.assignToHousehold(
							organizationId = organizationId,
							householdId = householdId,
							assetId = assetId,
							title = title,
							currentUser = currentUser,
						)
						BulkActionItemResult(household.displayName, BulkActionStatus.SUCCEEDED, entityId = assignment.id)
					} catch (error: ApiException) {
						BulkActionItemResult(household.displayName, BulkActionStatus.FAILED, message = error.message)
					}
				}
			}
		}
		recordSummary(
			currentUser,
			organizationId,
			"onboarding.bulk_document_assignments",
			results,
			mapOf("assetId" to assetId, "title" to title),
		)
		return results.toBulkResult()
	}

	private fun recordSummary(
		currentUser: CurrentUser,
		organizationId: UUID,
		action: String,
		results: List<BulkActionItemResult>,
		extra: Map<String, Any?> = emptyMap(),
	) {
		val result = results.toBulkResult()
		val metadata = objectMapper.writeValueAsString(
			extra + mapOf(
				"requested" to result.requestedCount,
				"succeeded" to result.succeededCount,
				"skipped" to result.skippedCount,
				"failed" to result.failedCount,
			),
		)
		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = action,
			entityType = "onboarding_bulk_action",
			entityId = UUID.randomUUID(),
			metadataJson = metadata,
		)
	}

	private fun List<BulkActionItemResult>.toBulkResult() = BulkActionResult(
		requestedCount = size,
		succeededCount = count { it.status == BulkActionStatus.SUCCEEDED },
		skippedCount = count { it.status == BulkActionStatus.SKIPPED },
		failedCount = count { it.status == BulkActionStatus.FAILED },
		items = this,
	)
}
