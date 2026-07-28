package com.leaguelift.dashboard.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.AnnouncementItem
import com.leaguelift.dashboard.web.CoachTeamSummary
import com.leaguelift.dashboard.web.FundraisingProgress
import com.leaguelift.dashboard.web.RequiredActionItem
import com.leaguelift.dashboard.web.RosterSummary
import com.leaguelift.dashboard.web.ScheduleItem
import com.leaguelift.dashboard.web.TeamPageStatusItem
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.publicpage.persistence.PublicPageRepository
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val TEAM_LIMIT = 25
private const val CAMPAIGN_LIMIT = 25

/**
 * One method per Coach-dashboard card, each its own controller endpoint and each
 * re-checking organization membership on every call. See [OwnerDashboardService] for
 * the same real/demo split pattern.
 */
@Service
class CoachDashboardService(
	private val membershipService: MembershipService,
	private val teamRepository: TeamRepository,
	private val participantRepository: ParticipantRepository,
	private val publicPageRepository: PublicPageRepository,
	private val campaignRepository: CampaignRepository,
) {

	/** Real: every team in the organization (no per-team coach scoping yet, see class doc). */
	fun getTeams(organizationId: UUID, currentUser: CurrentUser): List<CoachTeamSummary> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return teamRepository.findAll(organizationId, offset = 0, limit = TEAM_LIMIT)
			.map { CoachTeamSummary(it.id, it.name, it.sport, participantRepository.countActiveForTeam(it.id, organizationId)) }
	}

	fun getTeamSchedule(organizationId: UUID, currentUser: CurrentUser): List<ScheduleItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return listOf(
			ScheduleItem("sch-1", "SAT", "24", "vs Northview Falcons", "League Game", "10:00 AM", "Home"),
			ScheduleItem("sch-2", "SAT", "31", "vs Westlake Warriors", "League Game", "10:00 AM", "Away"),
		)
	}

	fun getRosterSummary(organizationId: UUID, currentUser: CurrentUser): RosterSummary {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val teams = teamRepository.findAll(organizationId, offset = 0, limit = TEAM_LIMIT)
		val totalAthletes = teams.sumOf { participantRepository.countActiveForTeam(it.id, organizationId) }
		return RosterSummary(
			athletes = totalAthletes,
			isAttendanceDemoData = true,
			attendanceRatePercent = 86,
			availabilityResponsePercent = 92,
		)
	}

	/** Real, for the organization's first team, when a public page exists for it. */
	fun getTeamPageStatus(organizationId: UUID, currentUser: CurrentUser): TeamPageStatusItem? {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val team = teamRepository.findAll(organizationId, offset = 0, limit = 1).firstOrNull() ?: return null
		val page = publicPageRepository.findByEntityId(team.id)
		return TeamPageStatusItem(team.id, team.name, page?.status?.name ?: "NOT_CREATED", page?.slug)
	}

	/** Real, when the organization has an active campaign; raised amount is demo (no contribution recording yet). */
	fun getFundraisingProgress(organizationId: UUID, currentUser: CurrentUser): FundraisingProgress? {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val campaign = campaignRepository.findAll(organizationId, offset = 0, limit = CAMPAIGN_LIMIT)
			.firstOrNull { it.status == CampaignStatus.ACTIVE }
			?: return null
		return FundraisingProgress(
			campaignId = campaign.id,
			name = campaign.name,
			status = campaign.status.name,
			goalAmountMinor = campaign.goalAmountMinor,
			currency = campaign.currency,
			isRaisedDemoData = true,
			raisedMinor = (campaign.goalAmountMinor * 0.65).toLong(),
		)
	}

	fun getAnnouncements(organizationId: UUID, currentUser: CurrentUser): List<AnnouncementItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return listOf(
			AnnouncementItem("ann-1", "Practice Change", "Practice moved to Field 3 due to field maintenance.", "Posted recently", true),
			AnnouncementItem("ann-2", "Team Store Now Open", "Grab your gear! Store closes soon.", "Posted recently", false),
		)
	}

	fun getRequiredActions(organizationId: UUID, currentUser: CurrentUser): List<RequiredActionItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return listOf(
			RequiredActionItem("act-1", "warning", "Approve Apparel Orders", "12 orders awaiting approval", "Due soon"),
			RequiredActionItem("act-2", "info", "Update Roster Info", "2 athletes missing jersey numbers", "Due soon"),
		)
	}
}
