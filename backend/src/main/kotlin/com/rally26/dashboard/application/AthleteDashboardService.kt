package com.rally26.dashboard.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.web.CurrentUser
import com.rally26.dashboard.web.AthleteOverviewResponse
import com.rally26.dashboard.web.AthleteTeamSummary
import com.rally26.dashboard.web.GuardianSummary
import com.rally26.dashboard.web.HistoryItem
import com.rally26.dashboard.web.NextEventSummary
import com.rally26.dashboard.web.OrderSummary
import com.rally26.dashboard.web.ScheduleItem
import com.rally26.event.application.EventService
import com.rally26.event.domain.Event
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.displayTitle
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.participant.domain.Participant
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private const val WEEK_EVENTS_LIMIT = 50

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
 * As of Phase 10 slice 2 (ADR-027), the next-event/week-schedule cards are real,
 * reusing [EventService.listForParticipant]'s own authorization (the caller already
 * holds the `ATHLETE_SELF` link this method resolves [linkedParticipant] from, so that
 * check always passes here — it's not redundant paranoia, just the same seam every
 * other caller of that method goes through). Orders remain honestly empty — the
 * `order` table still has no participant association (orders are organization/store-
 * scoped, not athlete-scoped).
 */
@Service
class AthleteDashboardService(
	private val authorizationService: AuthorizationService,
	private val roleAssignmentRepository: RoleAssignmentRepository,
	private val participantRepository: ParticipantRepository,
	private val householdRepository: HouseholdRepository,
	private val teamRepository: TeamRepository,
	private val appUserRepository: AppUserRepository,
	private val eventService: EventService,
) {

	private fun linkedParticipant(currentUser: CurrentUser): Participant? {
		val link = authorizationService.findAthleteSelfLink(currentUser) ?: return null
		val organizationId = link.organizationId ?: return null
		val participantId = link.resourceId ?: return null
		return participantRepository.findById(participantId, organizationId)
	}

	/** Upcoming (not cancelled/completed), soonest first. */
	private fun upcomingEvents(participant: Participant, currentUser: CurrentUser): List<Event> {
		val now = Instant.now()
		return eventService.listForParticipant(participant.organizationId, participant.id, currentUser, 0, WEEK_EVENTS_LIMIT)
			.filter { it.status != EventStatus.CANCELLED && it.status != EventStatus.COMPLETED }
			.filter { it.startAt == null || it.startAt.isAfter(now) }
			.sortedBy { it.startAt ?: Instant.MAX }
	}

	private fun toScheduleItem(event: Event, participant: Participant): ScheduleItem {
		val teamName = event.teamId?.let { teamRepository.findById(it, participant.organizationId)?.name }
		val opponentName = event.opponentTeamId?.let { teamRepository.findById(it, participant.organizationId)?.name }
		val zone = runCatching { ZoneId.of(event.timezone) }.getOrDefault(ZoneId.systemDefault())
		val zoned = event.startAt?.atZone(zone)
		return ScheduleItem(
			id = event.id.toString(),
			day = zoned?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.US) ?: "TBD",
			date = zoned?.toLocalDate()?.toString() ?: "TBD",
			title = displayTitle(event, teamName, opponentName),
			subtitle = event.venueName ?: event.address ?: "",
			time = zoned?.toLocalTime()?.toString() ?: "TBD",
			tag = event.eventType.name,
		)
	}

	fun getOverview(currentUser: CurrentUser): AthleteOverviewResponse {
		val participant = linkedParticipant(currentUser)
		return AthleteOverviewResponse(
			displayName = participant?.let { "${it.firstName} ${it.lastName}" } ?: currentUser.displayName,
			isDemoData = false,
			nextEvent = participant?.let { p ->
				upcomingEvents(p, currentUser).firstOrNull()?.let { event ->
					val item = toScheduleItem(event, p)
					NextEventSummary(item.title, item.tag ?: "", "${item.day} ${item.date} ${item.time}".trim(), item.subtitle)
				}
			},
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

	fun getWeekEvents(currentUser: CurrentUser): List<ScheduleItem> {
		val participant = linkedParticipant(currentUser) ?: return emptyList()
		val weekFromNow = Instant.now().plusSeconds(7 * 24 * 60 * 60)
		return upcomingEvents(participant, currentUser)
			.filter { it.startAt == null || it.startAt.isBefore(weekFromNow) }
			.map { toScheduleItem(it, participant) }
	}

	fun getRecentHistory(currentUser: CurrentUser): List<HistoryItem> = emptyList()

	fun getGuardians(currentUser: CurrentUser): List<GuardianSummary> {
		val participant = linkedParticipant(currentUser) ?: return emptyList()
		return householdRepository.listAdults(participant.householdId, participant.organizationId).map { adult ->
			GuardianSummary("${adult.firstName} ${adult.lastName}", adult.relationship ?: "Guardian", adult.email ?: "", adult.phone ?: "")
		}
	}

	fun getOrders(currentUser: CurrentUser): List<OrderSummary> = emptyList()
}
