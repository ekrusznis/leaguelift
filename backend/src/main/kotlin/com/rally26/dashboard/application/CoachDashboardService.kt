package com.rally26.dashboard.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.dashboard.web.AnnouncementItem
import com.rally26.dashboard.web.CoachTeamSummary
import com.rally26.dashboard.web.FundraisingProgress
import com.rally26.dashboard.web.RequiredActionItem
import com.rally26.dashboard.web.RosterSummary
import com.rally26.dashboard.web.ScheduleItem
import com.rally26.dashboard.web.TeamPageStatusItem
import com.rally26.event.application.EventService
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.publicpage.persistence.PublicPageRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * One method per Coach-dashboard card, each its own controller endpoint. As of
 * Phase 7/ADR-020, every card is scoped to the caller's *actual* assigned teams —
 * resolved via [AuthorizationService.listAccessibleTeamIds] (explicit `role_assignment`
 * grants plus organization OWNER/ADMINISTRATOR inheritance) — replacing the previous
 * "every team in the organization" behavior `db/seed/V9000` explicitly flagged as not
 * real scoping. A coach with zero assigned teams sees empty results, not another
 * team's data (DESIGN-DOC.md section 18.1's "a coach cannot access an unrelated team").
 */
@Service
class CoachDashboardService(
	private val authorizationService: AuthorizationService,
	private val teamRepository: TeamRepository,
	private val participantRepository: ParticipantRepository,
	private val publicPageRepository: PublicPageRepository,
	private val campaignRepository: CampaignRepository,
	private val contributionRepository: ContributionRepository,
	private val eventService: EventService,
	private val dashboardEventMapper: DashboardEventMapper,
) {

	/** Real: only teams this user has TEAM_VIEW access to (explicit grant or org owner/admin inheritance). */
	fun getTeams(organizationId: UUID, currentUser: CurrentUser): List<CoachTeamSummary> {
		val teamIds = requireAnyTeamAccess(organizationId, currentUser)
		return teamIds
			.mapNotNull { teamRepository.findById(it, organizationId) }
			.sortedBy { it.name }
			.map { CoachTeamSummary(it.id, it.name, it.sport, participantRepository.countActiveForTeam(it.id, organizationId)) }
	}

	fun getTeamSchedule(organizationId: UUID, currentUser: CurrentUser, teamId: UUID? = null): List<ScheduleItem> {
		val team = resolveSelectedOrDefaultTeam(organizationId, currentUser, teamId) ?: return emptyList()
		return dashboardEventMapper.upcoming(
			eventService.listForTeam(organizationId, team.id, currentUser, offset = 0, limit = 50),
			includeDrafts = true,
		).map { dashboardEventMapper.toScheduleItem(it, organizationId) }
	}

	fun getRosterSummary(organizationId: UUID, currentUser: CurrentUser, teamId: UUID? = null): RosterSummary {
		val team = resolveSelectedOrDefaultTeam(organizationId, currentUser, teamId)
		val totalAthletes = team?.let { participantRepository.countActiveForTeam(it.id, organizationId) } ?: 0
		return RosterSummary(
			athletes = totalAthletes,
			isAttendanceDemoData = true,
			attendanceRatePercent = 86,
			availabilityResponsePercent = 92,
		)
	}

	/**
	 * Real, for the selected team when one is passed (a coach's team selector, see
	 * ADR-020 consequences), defaulting to the caller's alphabetically-first
	 * accessible team otherwise — when a public page exists for it.
	 */
	fun getTeamPageStatus(organizationId: UUID, currentUser: CurrentUser, teamId: UUID? = null): TeamPageStatusItem? {
		val team = resolveSelectedOrDefaultTeam(organizationId, currentUser, teamId) ?: return null
		val page = publicPageRepository.findByEntityId(team.id)
		return TeamPageStatusItem(team.id, team.name, page?.status?.name ?: "NOT_CREATED", page?.slug)
	}

	/**
	 * Real, for the selected team when one is passed (a coach's team selector, see
	 * ADR-020 consequences), defaulting to the caller's alphabetically-first
	 * accessible team otherwise — when that team has an active campaign; raised
	 * amount is real (Phase 3/ADR-015).
	 */
	fun getFundraisingProgress(organizationId: UUID, currentUser: CurrentUser, teamId: UUID? = null): FundraisingProgress? {
		val team = resolveSelectedOrDefaultTeam(organizationId, currentUser, teamId) ?: return null
		val campaign = campaignRepository.findActiveByTeam(team.id, organizationId) ?: return null
		return FundraisingProgress(
			campaignId = campaign.id,
			name = campaign.name,
			status = campaign.status.name,
			goalAmountMinor = campaign.goalAmountMinor,
			currency = campaign.currency,
			isRaisedDemoData = false,
			raisedMinor = contributionRepository.sumConfirmedByCampaign(campaign.id),
		)
	}

	/**
	 * Resolves [teamId] against the caller's accessible teams when given — throwing
	 * if it isn't one of them, the same "no unrelated team" boundary every other
	 * method here enforces via [requireAnyTeamAccess] — or falls back to the
	 * alphabetically-first accessible team (the pre-selector default every caller saw
	 * before a team parameter existed).
	 */
	private fun resolveSelectedOrDefaultTeam(organizationId: UUID, currentUser: CurrentUser, teamId: UUID?) =
		run {
			val teamIds = requireAnyTeamAccess(organizationId, currentUser)
			if (teamId != null) {
				if (teamId !in teamIds) {
					throw ForbiddenException("TEAM_ACCESS_DENIED", "You are not assigned to this team in this organization.")
				}
				teamRepository.findById(teamId, organizationId)
			} else {
				teamIds.mapNotNull { teamRepository.findById(it, organizationId) }.minByOrNull { it.name }
			}
		}

	fun getAnnouncements(organizationId: UUID, currentUser: CurrentUser): List<AnnouncementItem> {
		requireAnyTeamAccess(organizationId, currentUser)
		// No announcements/communications model exists yet (Phase 8 — "Not started").
		return listOf(
			AnnouncementItem("ann-1", "Practice Change", "Practice moved to Field 3 due to field maintenance.", "Posted recently", true),
			AnnouncementItem("ann-2", "Team Store Now Open", "Grab your gear! Store closes soon.", "Posted recently", false),
		)
	}

	fun getRequiredActions(organizationId: UUID, currentUser: CurrentUser): List<RequiredActionItem> {
		requireAnyTeamAccess(organizationId, currentUser)
		return listOf(
			RequiredActionItem("act-1", "warning", "Approve Apparel Orders", "12 orders awaiting approval", "Due soon"),
			RequiredActionItem("act-2", "info", "Update Roster Info", "2 athletes missing jersey numbers", "Due soon"),
		)
	}

	private fun requireAnyTeamAccess(organizationId: UUID, currentUser: CurrentUser): Set<UUID> {
		val teamIds = authorizationService.listAccessibleTeamIds(organizationId, currentUser, Capabilities.TEAM_VIEW)
		if (teamIds.isEmpty() && !currentUser.platformAdministrator) {
			throw ForbiddenException("TEAM_ACCESS_DENIED", "You are not assigned to any team in this organization.")
		}
		return teamIds
	}
}
