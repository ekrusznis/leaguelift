package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.RoleAssignmentContextType
import com.leaguelift.authorization.persistence.RoleAssignmentRepository
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.AthleteOverviewResponse
import com.leaguelift.dashboard.web.AthleteTeamSummary
import com.leaguelift.dashboard.web.GuardianSummary
import com.leaguelift.dashboard.web.HistoryItem
import com.leaguelift.dashboard.web.OrderSummary
import com.leaguelift.dashboard.web.ScheduleItem
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Service

/**
 * One method per Athlete-dashboard card. As of Phase 7/ADR-020, real wherever the
 * schema supports it: the caller's own participant record, real team assignments, and
 * real guardian contact info — resolved through a `role_assignment(PARTICIPANT,
 * ATHLETE_SELF)` self-link ([AuthorizationService.findAthleteSelfLink]), which
 * formalizes `db/seed`'s existing "controlled test account" pattern rather than
 * opening general participant login (DESIGN-DOC.md section 4.6 — the under-13
 * consent/privacy workflow that would gate real self-service athlete accounts still
 * isn't designed). A caller with no self-link (the common case today, since linking is
 * still a manual/seed-only step) gets the same honest-empty response a linked athlete
 * with no schedule/order data would get — never fabricated content.
 *
 * Genuinely not real yet, honestly: week/history schedule data (no events model exists
 * — Phase 10, "Not started") and orders (the `order` table has no participant
 * association — orders are organization/store-scoped, not athlete-scoped). Both return
 * empty lists rather than demo content.
 */
@Service
class AthleteDashboardService(
	private val authorizationService: AuthorizationService,
	private val roleAssignmentRepository: RoleAssignmentRepository,
	private val participantRepository: ParticipantRepository,
	private val householdRepository: HouseholdRepository,
	private val teamRepository: TeamRepository,
	private val appUserRepository: AppUserRepository,
) {

	private fun linkedParticipant(currentUser: CurrentUser): Participant? {
		val link = authorizationService.findAthleteSelfLink(currentUser) ?: return null
		val organizationId = link.organizationId ?: return null
		val participantId = link.resourceId ?: return null
		return participantRepository.findById(participantId, organizationId)
	}

	fun getOverview(currentUser: CurrentUser): AthleteOverviewResponse {
		val participant = linkedParticipant(currentUser)
		return AthleteOverviewResponse(
			displayName = participant?.let { "${it.firstName} ${it.lastName}" } ?: currentUser.displayName,
			isDemoData = false,
			// No events/schedule model exists yet (Phase 10 — "Not started"); honestly
			// empty rather than a fabricated next event.
			nextEvent = null,
		)
	}

	fun getTeams(currentUser: CurrentUser): List<AthleteTeamSummary> {
		val participant = linkedParticipant(currentUser) ?: return emptyList()
		return participantRepository.listTeamAssignments(participant.id, participant.organizationId).mapNotNull { assignment ->
			val team = teamRepository.findById(assignment.teamId, participant.organizationId) ?: return@mapNotNull null
			val coachName = roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TEAM, team.id)
				.firstOrNull()
				?.let { appUserRepository.findById(it.userId)?.displayName }
				?: "Not yet assigned"
			AthleteTeamSummary(team.name, "${team.sport}${team.season?.let { " · $it" } ?: ""}", coachName)
		}
	}

	fun getWeekEvents(currentUser: CurrentUser): List<ScheduleItem> = emptyList()

	fun getRecentHistory(currentUser: CurrentUser): List<HistoryItem> = emptyList()

	fun getGuardians(currentUser: CurrentUser): List<GuardianSummary> {
		val participant = linkedParticipant(currentUser) ?: return emptyList()
		return householdRepository.listAdults(participant.householdId, participant.organizationId).map { adult ->
			GuardianSummary("${adult.firstName} ${adult.lastName}", adult.relationship ?: "Guardian", adult.email ?: "", adult.phone ?: "")
		}
	}

	fun getOrders(currentUser: CurrentUser): List<OrderSummary> = emptyList()
}
