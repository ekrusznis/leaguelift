package com.rally26.dashboard.application

import com.rally26.audit.persistence.AuditEventRepository
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.dashboard.web.ActivityItem
import com.rally26.dashboard.web.AttentionItem
import com.rally26.dashboard.web.FinancialOverviewResponse
import com.rally26.dashboard.web.OwnerOnboardingProgress
import com.rally26.dashboard.web.OwnerSummaryResponse
import com.rally26.dashboard.web.ReportMetric
import com.rally26.dashboard.web.ScheduleItem
import com.rally26.dashboard.web.TeamPerformanceRow
import com.rally26.event.application.EventService
import com.rally26.fee.persistence.FeeRepository
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.ledger.domain.LedgerDirection
import com.rally26.ledger.domain.LedgerEntryType
import com.rally26.ledger.persistence.LedgerEntryRepository
import com.rally26.membership.application.MembershipService
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.payout.application.PayoutAccountService
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val TEAM_PERFORMANCE_LIMIT = 10
private const val RECENT_ACTIVITY_LIMIT = 10

/**
 * One method per Owner-dashboard card (DESIGN-DOC.md section 10.1/10.2) — each is its
 * own controller endpoint, each re-checks the caller's organization membership on
 * every call rather than trusting a previously-resolved context. Real data where the
 * schema supports it (summary counts, team performance identity/participants, recent
 * activity from audit_event, and — as of the Phase 7 completion demo-data audit —
 * financial overview's apparel/payout figures and reports snapshot's dollar values,
 * both now read from `ledger_entry`/`PayoutAccountService` instead of hardcoded
 * literals); canned sample data only where the backing model genuinely doesn't exist
 * yet (attention queue, onboarding progress, reports snapshot's
 * trend percentages — no historical/time-series tracking exists to compute a real
 * period-over-period change) — each demo-backed response is tagged `isDemoData` /
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
	private val feeRepository: FeeRepository,
	private val campaignRepository: CampaignRepository,
	private val contributionRepository: ContributionRepository,
	private val ledgerEntryRepository: LedgerEntryRepository,
	private val payoutAccountService: PayoutAccountService,
	private val eventService: EventService,
	private val dashboardEventMapper: DashboardEventMapper,
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
		val fees = feeRepository.getFinancialSummary(organizationId)
		return FinancialOverviewResponse(
			isFeesDemoData = false,
			isFundraisingDemoData = false,
			currency = "USD",
			feesAssignedMinor = fees.feesAssignedMinor,
			feesCollectedMinor = fees.feesCollectedMinor,
			outstandingMinor = fees.outstandingMinor,
			fundraisingMinor = contributionRepository.sumConfirmedByOrganization(organizationId),
			apparelSalesMinor = ledgerEntryRepository.sumByOrganizationTypeAndDirection(organizationId, LedgerEntryType.GROSS_SALE, LedgerDirection.CREDIT),
			pendingPayoutMinor = payoutAccountService.getPayoutSummary(organizationId, currentUser).netAvailableMinor,
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
				val activeCampaign = campaignRepository.findActiveByTeam(team.id, organizationId)
				TeamPerformanceRow(
					teamId = team.id,
					name = team.name,
					sport = team.sport,
					participants = participantRepository.countActiveForTeam(team.id, organizationId),
					status = team.status.name,
					isFundraisingDemoData = activeCampaign == null,
					fundraisingRaisedMinor = activeCampaign?.let { contributionRepository.sumConfirmedByCampaign(it.id) },
					fundraisingGoalMinor = activeCampaign?.goalAmountMinor,
				)
			}
	}

	fun getUpcomingEvents(organizationId: UUID, currentUser: CurrentUser): List<ScheduleItem> =
		dashboardEventMapper.upcoming(
			eventService.listForOrganization(organizationId, currentUser, offset = 0, limit = 50),
			includeDrafts = true,
		).map { dashboardEventMapper.toScheduleItem(it, organizationId) }

	fun getRecentActivity(organizationId: UUID, currentUser: CurrentUser): List<ActivityItem> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return auditEventRepository.listRecentForOrganization(organizationId, RECENT_ACTIVITY_LIMIT)
			.map { ActivityItem(it.id, it.action, it.entityType, it.entityId, it.createdAt) }
	}

	fun getOnboardingProgress(organizationId: UUID, currentUser: CurrentUser): OwnerOnboardingProgress {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return OwnerOnboardingProgress(isDemoData = true, completedSteps = 8, totalSteps = 10)
	}

	/**
	 * `valueMinor` for each metric is real (same queries as [getFinancialOverview]), so
	 * `isDemoData = false` now — the frontend's "Demo data" label would otherwise
	 * mislabel a real number. `trendPercent` is pinned to 0.0: no historical/time-series
	 * snapshot mechanism exists to compute a real period-over-period change, and
	 * fabricating one would be exactly the kind of invented growth story DESIGN-DOC.md
	 * section 12.2's truthfulness rules forbid on the marketing site — the same
	 * standard applies here even though this is an authenticated view. 0.0 reads as "no
	 * measured change" rather than asserting a specific (fabricated) trend.
	 */
	fun getReportsSnapshot(organizationId: UUID, currentUser: CurrentUser): List<ReportMetric> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val fees = feeRepository.getFinancialSummary(organizationId)
		val fundraising = contributionRepository.sumConfirmedByOrganization(organizationId)
		val apparel = ledgerEntryRepository.sumByOrganizationTypeAndDirection(organizationId, LedgerEntryType.GROSS_SALE, LedgerDirection.CREDIT)
		return listOf(
			ReportMetric(isDemoData = false, label = "Fee Collection", valueMinor = fees.feesCollectedMinor, trendPercent = 0.0),
			ReportMetric(isDemoData = false, label = "Fundraising", valueMinor = fundraising, trendPercent = 0.0),
			ReportMetric(isDemoData = false, label = "Apparel Sales", valueMinor = apparel, trendPercent = 0.0),
		)
	}
}
