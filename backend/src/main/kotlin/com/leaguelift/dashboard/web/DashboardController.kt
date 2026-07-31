package com.leaguelift.dashboard.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.application.AthleteDashboardService
import com.leaguelift.dashboard.application.CoachDashboardService
import com.leaguelift.dashboard.application.DashboardContextService
import com.leaguelift.dashboard.application.OwnerDashboardService
import com.leaguelift.dashboard.application.ParentDashboardService
import com.leaguelift.dashboard.application.PlatformAdminDashboardService
import com.leaguelift.dashboard.application.TournamentDashboardService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * One endpoint per dashboard card/section (DESIGN-DOC.md section 10.1/10.2), not one
 * bundled response per dashboard — each card loads, errors, and empties
 * independently on the frontend. Every endpoint authorizes off the real,
 * JWT-resolved [CurrentUser] on every call (no client-supplied user ID is ever
 * trusted) — see each application service for its specific check.
 */
@RestController
@RequestMapping("/api/v1")
class DashboardController(
	private val dashboardContextService: DashboardContextService,
	private val ownerDashboardService: OwnerDashboardService,
	private val coachDashboardService: CoachDashboardService,
	private val parentDashboardService: ParentDashboardService,
	private val athleteDashboardService: AthleteDashboardService,
	private val tournamentDashboardService: TournamentDashboardService,
	private val platformAdminDashboardService: PlatformAdminDashboardService,
) {

	@GetMapping("/me/dashboard-context")
	fun dashboardContext(@AuthenticationPrincipal currentUser: CurrentUser): DashboardContextResponse =
		dashboardContextService.resolve(currentUser).toResponse()

	// --- Athlete ---

	@GetMapping("/me/dashboard/athlete/overview")
	fun athleteOverview(@AuthenticationPrincipal currentUser: CurrentUser) = athleteDashboardService.getOverview(currentUser)

	@GetMapping("/me/dashboard/athlete/teams")
	fun athleteTeams(@AuthenticationPrincipal currentUser: CurrentUser) = athleteDashboardService.getTeams(currentUser)

	@GetMapping("/me/dashboard/athlete/week-events")
	fun athleteWeekEvents(@AuthenticationPrincipal currentUser: CurrentUser) = athleteDashboardService.getWeekEvents(currentUser)

	@GetMapping("/me/dashboard/athlete/recent-history")
	fun athleteRecentHistory(@AuthenticationPrincipal currentUser: CurrentUser) = athleteDashboardService.getRecentHistory(currentUser)

	@GetMapping("/me/dashboard/athlete/guardians")
	fun athleteGuardians(@AuthenticationPrincipal currentUser: CurrentUser) = athleteDashboardService.getGuardians(currentUser)

	@GetMapping("/me/dashboard/athlete/orders")
	fun athleteOrders(@AuthenticationPrincipal currentUser: CurrentUser) = athleteDashboardService.getOrders(currentUser)

	// --- Owner ---

	@GetMapping("/organizations/{organizationId}/dashboard/owner/summary")
	fun ownerSummary(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getSummary(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/financial-overview")
	fun ownerFinancialOverview(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getFinancialOverview(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/attention-required")
	fun ownerAttentionRequired(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getAttentionRequired(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/team-performance")
	fun ownerTeamPerformance(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getTeamPerformance(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/upcoming-events")
	fun ownerUpcomingEvents(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getUpcomingEvents(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/recent-activity")
	fun ownerRecentActivity(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getRecentActivity(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/onboarding-progress")
	fun ownerOnboardingProgress(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getOnboardingProgress(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/owner/reports-snapshot")
	fun ownerReportsSnapshot(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		ownerDashboardService.getReportsSnapshot(organizationId, currentUser)

	// --- Coach ---

	@GetMapping("/organizations/{organizationId}/dashboard/coach/teams")
	fun coachTeams(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		coachDashboardService.getTeams(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/coach/team-schedule")
	fun coachTeamSchedule(
		@PathVariable organizationId: UUID,
		@RequestParam(required = false) teamId: UUID?,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = coachDashboardService.getTeamSchedule(organizationId, currentUser, teamId)

	@GetMapping("/organizations/{organizationId}/dashboard/coach/roster-summary")
	fun coachRosterSummary(
		@PathVariable organizationId: UUID,
		@RequestParam(required = false) teamId: UUID?,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = coachDashboardService.getRosterSummary(organizationId, currentUser, teamId)

	@GetMapping("/organizations/{organizationId}/dashboard/coach/team-page-status")
	fun coachTeamPageStatus(
		@PathVariable organizationId: UUID,
		@RequestParam(required = false) teamId: UUID?,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = coachDashboardService.getTeamPageStatus(organizationId, currentUser, teamId)

	@GetMapping("/organizations/{organizationId}/dashboard/coach/fundraising-progress")
	fun coachFundraisingProgress(
		@PathVariable organizationId: UUID,
		@RequestParam(required = false) teamId: UUID?,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = coachDashboardService.getFundraisingProgress(organizationId, currentUser, teamId)

	@GetMapping("/organizations/{organizationId}/dashboard/coach/announcements")
	fun coachAnnouncements(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		coachDashboardService.getAnnouncements(organizationId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/coach/required-actions")
	fun coachRequiredActions(@PathVariable organizationId: UUID, @AuthenticationPrincipal currentUser: CurrentUser) =
		coachDashboardService.getRequiredActions(organizationId, currentUser)

	// --- Parent ---

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/overview")
	fun parentOverview(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getOverview(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/athletes")
	fun parentAthletes(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getAthletes(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/family-schedule")
	fun parentFamilySchedule(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getFamilySchedule(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/outstanding-balance")
	fun parentOutstandingBalance(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getOutstandingBalance(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/family-credits")
	fun parentFamilyCredits(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getFamilyCredits(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/active-fundraisers")
	fun parentActiveFundraisers(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getActiveFundraisers(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/recent-orders")
	fun parentRecentOrders(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getRecentOrders(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/required-actions")
	fun parentRequiredActions(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getRequiredActions(organizationId, householdId, currentUser)

	@GetMapping("/organizations/{organizationId}/households/{householdId}/dashboard/parent/organization-updates")
	fun parentOrganizationUpdates(
		@PathVariable organizationId: UUID,
		@PathVariable householdId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = parentDashboardService.getOrganizationUpdates(organizationId, householdId, currentUser)

	// --- Tournament ---

	@GetMapping("/organizations/{organizationId}/dashboard/tournament/{tournamentId}/summary")
	fun tournamentSummary(
		@PathVariable organizationId: UUID,
		@PathVariable tournamentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = tournamentDashboardService.getSummary(organizationId, tournamentId, currentUser)

	@GetMapping("/organizations/{organizationId}/dashboard/tournament/{tournamentId}/page-status")
	fun tournamentPageStatus(
		@PathVariable organizationId: UUID,
		@PathVariable tournamentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) = tournamentDashboardService.getPageStatus(organizationId, tournamentId, currentUser)

	// --- Platform Admin ---

	@GetMapping("/platform/dashboard/summary")
	fun platformSummary(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.getSummary(currentUser)

	@GetMapping("/platform/dashboard/organizations")
	fun platformOrganizations(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.listOrganizations(currentUser)

	@GetMapping("/platform/dashboard/webhook-health")
	fun platformWebhookHealth(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.getWebhookHealth(currentUser)

	@GetMapping("/platform/dashboard/outbox-health")
	fun platformOutboxHealth(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.getOutboxHealth(currentUser)

	@GetMapping("/platform/dashboard/orders-summary")
	fun platformOrdersSummary(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.getOrdersSummary(currentUser)

	@GetMapping("/platform/dashboard/payments-summary")
	fun platformPaymentsSummary(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.getPaymentsSummary(currentUser)

	@GetMapping("/platform/dashboard/payouts-summary")
	fun platformPayoutsSummary(@AuthenticationPrincipal currentUser: CurrentUser) = platformAdminDashboardService.getPayoutsSummary(currentUser)
}
