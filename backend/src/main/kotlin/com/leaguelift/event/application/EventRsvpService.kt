package com.leaguelift.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.authorization.persistence.GuardianRelationshipRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventRsvp
import com.leaguelift.event.domain.RsvpResponse
import com.leaguelift.event.domain.RsvpSource
import com.leaguelift.event.domain.RsvpSummary
import com.leaguelift.event.persistence.EventRepository
import com.leaguelift.event.persistence.EventRsvpRepository
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.persistence.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** A staff-visible list of individual responses; guardians/athletes only ever get [summary]. */
data class EventRsvpsResult(val summary: RsvpSummary, val responses: List<EventRsvp>)

/**
 * RSVP (Phase 10 slice 2, ADR-027). Submission is gated by a real self/guardian
 * relationship check — deliberately not [AuthorizationService.hasHouseholdCapability],
 * whose "any active org member" branch is too broad for an action that impersonates a
 * specific guardian (see ADR-026 decision 4 / ADR-027). Simple v1 policy (founder
 * decision): MAYBE is a real response, no deadline/lock/staff-override.
 */
@Service
class EventRsvpService(
	private val eventRsvpRepository: EventRsvpRepository,
	private val eventRepository: EventRepository,
	private val participantRepository: ParticipantRepository,
	private val guardianRelationshipRepository: GuardianRelationshipRepository,
	private val authorizationService: AuthorizationService,
	private val auditService: AuditService,
	private val objectMapper: ObjectMapper,
	private val eventService: EventService,
	private val appUserRepository: AppUserRepository,
	private val outboxWriter: OutboxWriter,
) {

	@Transactional
	fun submit(organizationId: UUID, eventId: UUID, participantId: UUID, response: RsvpResponse, note: String?, currentUser: CurrentUser): EventRsvp {
		val event = eventRepository.findById(eventId, organizationId) ?: throw NotFoundException("EVENT_NOT_FOUND", "The event could not be found.")
		val participant = participantRepository.findById(participantId, organizationId)
			?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
		val eventTeamId = event.teamId ?: throw ValidationException("This event does not accept RSVPs (it has no owning team).")
		if (participantRepository.listTeamAssignments(participantId, organizationId).none { it.teamId == eventTeamId }) {
			throw ValidationException("This participant is not on the team this event belongs to.")
		}

		val source = resolveSource(organizationId, event, participant.householdId, participantId, currentUser)
		val previous = eventRsvpRepository.findByEventAndParticipant(eventId, participantId)
		val rsvp = eventRsvpRepository.upsert(eventId, participantId, response, note, currentUser.userId, source)

		auditService.record(
			currentUser.userId, organizationId, "event.rsvp_changed", "event_rsvp", rsvp.id,
			objectMapper.writeValueAsString(mapOf("previousResponse" to previous?.response?.name, "newResponse" to response.name, "source" to source.name)),
		)
		notifyStaffOfRsvpChange(event, participant, previous?.response, response, currentUser)
		return rsvp
	}

	/** Notifies team staff, not the submitting family — they already know what they just did (founder decision, Phase 10 slice 4, ADR-029). Excludes [currentUser] so a staff member submitting on a family's behalf doesn't get emailed their own action. */
	private fun notifyStaffOfRsvpChange(event: Event, participant: Participant, previousResponse: RsvpResponse?, newResponse: RsvpResponse, currentUser: CurrentUser) {
		val teamId = event.teamId ?: return
		val staffEmails = authorizationService.listTeamStaffUserIds(event.organizationId, teamId, Capabilities.EVENT_RSVP_READ_TEAM)
			.filter { it != currentUser.userId }
			.mapNotNull { appUserRepository.findById(it)?.email }
		val displayTitle = eventService.displayTitleFor(event, event.organizationId)
		outboxWriter.write(
			aggregateType = "event_rsvp",
			aggregateId = event.id,
			organizationId = event.organizationId,
			eventType = "event.rsvp_changed",
			payloadJson = objectMapper.writeValueAsString(
				RsvpChangeNotificationPayload(
					eventId = event.id,
					displayTitle = displayTitle,
					participantName = "${participant.firstName} ${participant.lastName}",
					previousResponse = previousResponse?.name,
					newResponse = newResponse.name,
					staffEmails = staffEmails,
				),
			),
		)
	}

	/**
	 * Staff (with `event.rsvp.read_team` at the event's team/tournament scope) get the
	 * full individual-response list; a guardian/athlete with a participant on the
	 * event's team gets [RsvpSummary] counts only (founder decision, 2026-07-30) — never
	 * another family's individual response.
	 */
	fun getRsvps(organizationId: UUID, eventId: UUID, currentUser: CurrentUser): EventRsvpsResult {
		val event = eventRepository.findById(eventId, organizationId) ?: throw NotFoundException("EVENT_NOT_FOUND", "The event could not be found.")
		val responses = eventRsvpRepository.findByEvent(eventId)
		val summary = RsvpSummary.of(responses)

		if (hasStaffReadAccess(event, currentUser)) return EventRsvpsResult(summary, responses)
		val teamId = event.teamId
		if (teamId != null && currentUserHasParticipantOnTeam(organizationId, teamId, currentUser)) return EventRsvpsResult(summary, emptyList())
		throw ForbiddenException("CAPABILITY_DENIED", "You do not have access to this event's RSVPs.")
	}

	private fun resolveSource(organizationId: UUID, event: Event, participantHouseholdId: UUID, participantId: UUID, currentUser: CurrentUser): RsvpSource {
		if (authorizationService.hasParticipantCapability(currentUser, participantId, Capabilities.EVENT_RSVP_SELF)) return RsvpSource.SELF
		if (guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, participantHouseholdId) != null) return RsvpSource.GUARDIAN
		if (hasStaffReadAccess(event, currentUser, requireManage = true)) return RsvpSource.ADMIN
		throw ForbiddenException("CAPABILITY_DENIED", "You are not authorized to submit an RSVP for this participant.")
	}

	private fun hasStaffReadAccess(event: Event, currentUser: CurrentUser, requireManage: Boolean = false): Boolean {
		val capability = if (requireManage) Capabilities.EVENT_UPDATE else Capabilities.EVENT_RSVP_READ_TEAM
		return when {
			event.teamId != null -> authorizationService.hasTeamCapability(event.organizationId, event.teamId, currentUser, capability)
			event.tournamentId != null -> authorizationService.hasTournamentCapability(event.organizationId, event.tournamentId, currentUser, capability)
			else -> false
		}
	}

	private fun currentUserHasParticipantOnTeam(organizationId: UUID, teamId: UUID, currentUser: CurrentUser): Boolean {
		authorizationService.findAthleteSelfLink(currentUser)?.resourceId?.let { participantId ->
			val participant = participantRepository.findById(participantId, organizationId)
			if (participant != null && participantRepository.listTeamAssignments(participantId, organizationId).any { it.teamId == teamId }) return true
		}
		for (relationship in guardianRelationshipRepository.findActiveForUser(currentUser.userId)) {
			if (relationship.organizationId != organizationId) continue
			for (participant in participantRepository.findByHousehold(relationship.householdId, organizationId)) {
				if (participantRepository.listTeamAssignments(participant.id, organizationId).any { it.teamId == teamId }) return true
			}
		}
		return false
	}
}
