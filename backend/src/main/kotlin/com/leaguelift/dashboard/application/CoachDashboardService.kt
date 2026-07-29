package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.AnnouncementItem
import com.leaguelift.dashboard.web.CoachTeamSummary
import com.leaguelift.dashboard.web.FundraisingProgress
import com.leaguelift.dashboard.web.RequiredActionItem
import com.leaguelift.dashboard.web.RosterSummary
import com.leaguelift.dashboard.web.ScheduleItem
import com.leaguelift.dashboard.web.TeamPageStatusItem
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.publicpage.persistence.PublicPageRepository
import com.leaguelift.team.persistence.TeamRepository
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
) {

	/** Real: only teams this user has TEAM_VIEW access to (explicit grant or org owner/admin inheritance). */
	fun getTeams(organizationId: UUID, currentUser: CurrentUser): List<CoachTeamSummary> {
		val teamIds = requireAnyTeamAccess(organizationId, currentUser)
		return teamIds
			.mapNotNull { teamRepository.findById(it, organizationId) }
			.sortedBy { it.name }
			.map { CoachTeamSummary(it.id, it.name, it.sport, participantRepository.countActiveForTeam(it.id, organizationId)) }
	}

	fun getTeamSchedule(organizationId: UUID, currentUser: CurrentUser): List<ScheduleItem> {
		requireAnyTeamAccess(organizationId, currentUser)
		// No schedule/events data model exists yet (DESIGN-DOC.md section 14.1 Phase 10
		// — "Not started"). Demo data, unlike every other card on this service, which is
		// real: inventing an events module is out of this phase's scope.
		return listOf(
			ScheduleItem("sch-1", "SAT", "24", "vs Northview Falcons", "League Game", "10:00 AM", "Home"),
			ScheduleItem("sch-2", "SAT", "31", "vs Westlake Warriors", "League Game", "10:00 AM", "Away"),
		)
	}

	fun getRosterSummary(organizationId: UUID, currentUser: CurrentUser): RosterSummary {
		val teamIds = requireAnyTeamAccess(organizationId, currentUser)
		val totalAthletes = teamIds.sumOf { participantRepository.countActiveForTeam(it, organizationId) }
		return RosterSummary(
			athletes = totalAthletes,
			isAttendanceDemoData = true,
			attendanceRatePercent = 86,
			availabilityResponsePercent = 92,
		)
	}

	/** Real, for the caller's first accessible team, when a public page exists for it. */
	fun getTeamPageStatus(organizationId: UUID, currentUser: CurrentUser): TeamPageStatusItem? {
		val teamIds = requireAnyTeamAccess(organizationId, currentUser)
		val team = teamIds.mapNotNull { teamRepository.findById(it, organizationId) }.minByOrNull { it.name } ?: return null
		val page = publicPageRepository.findByEntityId(team.id)
		return TeamPageStatusItem(team.id, team.name, page?.status?.name ?: "NOT_CREATED", page?.slug)
	}

	/** Real, when the caller's first accessible team has an active campaign; raised amount is real (Phase 3/ADR-015). */
	fun getFundraisingProgress(organizationId: UUID, currentUser: CurrentUser): FundraisingProgress? {
		val teamIds = requireAnyTeamAccess(organizationId, currentUser)
		val team = teamIds.mapNotNull { teamRepository.findById(it, organizationId) }.minByOrNull { it.name } ?: return null
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
