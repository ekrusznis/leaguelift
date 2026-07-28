package com.leaguelift.dashboard.application

import com.leaguelift.audit.persistence.AuditEventRepository
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.ActivityItem
import com.leaguelift.dashboard.web.AttentionItem
import com.leaguelift.dashboard.web.FinancialOverviewResponse
import com.leaguelift.dashboard.web.OwnerOnboardingProgress
import com.leaguelift.dashboard.web.OwnerSummaryResponse
import com.leaguelift.dashboard.web.ReportMetric
import com.leaguelift.dashboard.web.ScheduleItem
import com.leaguelift.dashboard.web.TeamPerformanceRow
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.team.persistence.TeamRepository
import com.leaguelift.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val TEAM_PERFORMANCE_LIMIT = 10
private const val RECENT_ACTIVITY_LIMIT = 10

/**
 * One method per Owner-dashboard card (DESIGN-DOC.md section 10.1/10.2) — each is its
 * own controller endpoint, each re-checks the caller's organization membership on
 * every call rather than trusting a previously-resolved context. Real data where the
 * schema supports it (summary counts, team performance identity/participants, recent
 * activity from audit_event); canned sample data everywhere the backing table doesn't
 * exist yet (financial overview, attention queue, upcoming events, onboarding
 * progress, reports snapshot) — each demo-backed response is tagged `isDemoData` /
 * `isFundraisingDemoData` so the frontend can label it rather than presenting it as
 * real.
 */
@Service
class OwnerDashboardService(
	private val membershipService: MembershipService,
	private val organizationRepository: OrganizationRepository,
	private val teamRepository: TeamRepository,
	private val householdRepository: HouseholdRepository,
	private val participantRepository: ParticipantRepository,
	private val tournamentRepository: TournamentRepository,
	private val auditEventRepository: AuditEventRepository,
) {

	fun getSummary(organizationId: UUID, currentUser: CurrentUser): OwnerSummaryResponse {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val organization = organizationRepository.findById(organizationId)
			?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
		return OwnerSummaryResponse(
			organizationName = organization.name,
			activeTeams = teamRepository.countAll(organizationId),
			participants = participantRepository.countActiveForOrganization(organizationId),
			households = householdRepository.countAll(organizationId),
			upcomingTournaments = tournamentRepository.countUpcoming(organizationId),
		)
	}

	fun getFinancialOverview(organizationId: UUID, currentUser: CurrentUser): FinancialOverviewResponse {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return FinancialOverviewResponse(
			isDemoData = true,
			currency = "USD",
			feesAssignedMinor = 28_475_000,
			feesCollectedMinor = 21_243_000,
			outstandingMinor = 7_232_000,
			fundraisingMinor = 3_468_000,
			apparelSalesMinor = 1_892_000,
			pendingPayoutMinor = 784_500,
		)
	}

	fun getAttentionRequired(organizationId: UUID, currentUser: CurrentUser): List<AttentionItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return listOf(
			AttentionItem("failed-payments", "error", "Failed Payment Attempts", "12 households · Total: $3,240", 12),
			AttentionItem("pending-approvals", "warning", "Pending Approvals", "Player passes, waivers, and orders", 18),
			AttentionItem("expiring-invitations", "info", "Expiring Invitations", "Members invited to join", 7),
			AttentionItem("fulfillment-issues", "purple", "Fulfillment Issues", "Apparel orders need attention", 4),
		)
	}

	fun getTeamPerformance(organizationId: UUID, currentUser: CurrentUser): List<TeamPerformanceRow> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return teamRepository.findAll(organizationId, offset = 0, limit = TEAM_PERFORMANCE_LIMIT)
			.map { team ->
				TeamPerformanceRow(
					teamId = team.id,
					name = team.name,
					sport = team.sport,
					participants = participantRepository.countActiveForTeam(team.id, organizationId),
					status = team.status.name,
					isFundraisingDemoData = true,
					fundraisingRaisedMinor = null,
					fundraisingGoalMinor = null,
				)
			}
	}

	fun getUpcomingEvents(organizationId: UUID, currentUser: CurrentUser): List<ScheduleItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return listOf(
			ScheduleItem("evt-1", "MAY", "24", "Boys U12 Blue vs. Northfork FC", "League Game", "10:00 AM", "Home"),
			ScheduleItem("evt-2", "MAY", "31", "Girls U14 Elite Tournament", "Tournament", "All Day", "Tournament"),
		)
	}

	fun getRecentActivity(organizationId: UUID, currentUser: CurrentUser): List<ActivityItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return auditEventRepository.listRecentForOrganization(organizationId, RECENT_ACTIVITY_LIMIT)
			.map { ActivityItem(it.id, it.action, it.entityType, it.entityId, it.createdAt) }
	}

	fun getOnboardingProgress(organizationId: UUID, currentUser: CurrentUser): OwnerOnboardingProgress {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return OwnerOnboardingProgress(isDemoData = true, completedSteps = 8, totalSteps = 10)
	}

	fun getReportsSnapshot(organizationId: UUID, currentUser: CurrentUser): List<ReportMetric> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return listOf(
			ReportMetric(isDemoData = true, label = "Fee Collection", valueMinor = 21_243_000, trendPercent = 12.0),
			ReportMetric(isDemoData = true, label = "Fundraising", valueMinor = 3_468_000, trendPercent = 18.0),
			ReportMetric(isDemoData = true, label = "Apparel Sales", valueMinor = 1_892_000, trendPercent = 9.0),
		)
	}
}
