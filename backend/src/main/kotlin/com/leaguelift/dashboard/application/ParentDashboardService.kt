package com.leaguelift.dashboard.application

import com.leaguelift.authorization.persistence.GuardianRelationshipRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.AthleteSummary
import com.leaguelift.dashboard.web.FamilyCredits
import com.leaguelift.dashboard.web.FeeLineItem
import com.leaguelift.dashboard.web.FundraiserSummary
import com.leaguelift.dashboard.web.HouseholdOverviewResponse
import com.leaguelift.dashboard.web.OrderSummary
import com.leaguelift.dashboard.web.OutstandingBalance
import com.leaguelift.dashboard.web.RequiredActionItem
import com.leaguelift.dashboard.web.ScheduleItem
import com.leaguelift.dashboard.web.UpdateItem
import com.leaguelift.event.application.EventService
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.computeFeeBalance
import com.leaguelift.fee.persistence.FeeAdjustmentRepository
import com.leaguelift.fee.persistence.FeePaymentRepository
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.household.domain.Household
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.persistence.MembershipRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

private val OUTSTANDING_STATUSES = setOf(FeeAssignmentStatus.OPEN, FeeAssignmentStatus.PARTIALLY_PAID)
private const val FEE_ASSIGNMENT_LIMIT = 50
private const val CAMPAIGN_LIMIT = 25

/**
 * One method per Parent-dashboard card, each its own controller endpoint. Real data
 * for athletes, schedule, and outstanding fees; canned sample data only for credits,
 * orders, required actions, and organization updates, whose attribution/models are not built yet. See
 * [OwnerDashboardService] for the same real/demo split pattern.
 *
 * Authorization (Phase 7/ADR-020): the caller is authorized if they're an active
 * organization member (staff support case), hold a real `guardian_relationship` to
 * this household, or — the pre-Phase-7 fallback, kept for households not yet linked —
 * their email matches an active `household_adult` on this household (the same interim
 * rule [DashboardContextService] still falls back to when resolving the Parent role).
 */
@Service
class ParentDashboardService(
	private val householdRepository: HouseholdRepository,
	private val membershipRepository: MembershipRepository,
	private val guardianRelationshipRepository: GuardianRelationshipRepository,
	private val participantRepository: ParticipantRepository,
	private val teamRepository: TeamRepository,
	private val feeRepository: FeeRepository,
	private val feePaymentRepository: FeePaymentRepository,
	private val feeAdjustmentRepository: FeeAdjustmentRepository,
	private val campaignRepository: CampaignRepository,
	private val contributionRepository: ContributionRepository,
	private val eventService: EventService,
	private val dashboardEventMapper: DashboardEventMapper,
) {

	fun getOverview(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): HouseholdOverviewResponse {
		val household = requireHousehold(organizationId, householdId, currentUser)
		return HouseholdOverviewResponse(household.displayName)
	}

	fun getAthletes(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<AthleteSummary> {
		requireHousehold(organizationId, householdId, currentUser)
		return participantRepository.findByHousehold(householdId, organizationId).map { participant ->
			val teamNames = participantRepository.listTeamAssignments(participant.id, organizationId)
				.mapNotNull { teamRepository.findById(it.teamId, organizationId)?.name }
			AthleteSummary(participant.id, "${participant.firstName} ${participant.lastName}", teamNames)
		}
	}

	fun getFamilySchedule(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<ScheduleItem> {
		requireHousehold(organizationId, householdId, currentUser)
		return dashboardEventMapper.upcoming(
			eventService.listForHousehold(organizationId, householdId, currentUser, offset = 0, limit = 50),
		).map { dashboardEventMapper.toScheduleItem(it, organizationId) }
	}

	fun getOutstandingBalance(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): OutstandingBalance {
		requireHousehold(organizationId, householdId, currentUser)
		val feeAssignments = feeRepository.findByHousehold(householdId, organizationId, offset = 0, limit = FEE_ASSIGNMENT_LIMIT)
		val currency = feeAssignments.firstOrNull()?.currency ?: "USD"
		val lineItems = feeAssignments
			.filter { it.status in OUTSTANDING_STATUSES }
			.map { assignment ->
				val paid = feePaymentRepository.sumActiveByAssignment(assignment.id, organizationId)
				val adjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, organizationId)
				val balance = computeFeeBalance(assignment.originalAmountMinor, paid, adjusted)
				FeeLineItem(assignment.description, balance.balanceMinor, assignment.status.name, assignment.dueDate)
			}
			.filter { it.balanceMinor > 0 }
		return OutstandingBalance(
			totalOutstandingMinor = lineItems.sumOf { it.balanceMinor },
			currency = currency,
			lineItems = lineItems,
		)
	}

	fun getFamilyCredits(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): FamilyCredits {
		requireHousehold(organizationId, householdId, currentUser)
		return FamilyCredits(isDemoData = true, currency = "USD", pendingMinor = 12_000, availableMinor = 8_500, appliedThisSeasonMinor = 21_500)
	}

	fun getActiveFundraisers(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<FundraiserSummary> {
		requireHousehold(organizationId, householdId, currentUser)
		return campaignRepository.findAll(organizationId, offset = 0, limit = CAMPAIGN_LIMIT)
			.filter { it.status == CampaignStatus.ACTIVE }
			.map {
				FundraiserSummary(
					campaignId = it.id,
					name = it.name,
					isRaisedDemoData = false,
					raisedMinor = contributionRepository.sumConfirmedByCampaign(it.id),
					goalMinor = it.goalAmountMinor,
					currency = it.currency,
				)
			}
	}

	fun getRecentOrders(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<OrderSummary> {
		requireHousehold(organizationId, householdId, currentUser)
		return listOf(OrderSummary("ord-1", "Riverside Soccer Hoodie", "#LL-78231", LocalDate.now().minusDays(10), "Shipped"))
	}

	fun getRequiredActions(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<RequiredActionItem> {
		requireHousehold(organizationId, householdId, currentUser)
		return listOf(RequiredActionItem("act-1", "warning", "Uniform Size Needed", "Uniform order needs a size selection", "Due soon"))
	}

	fun getOrganizationUpdates(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<UpdateItem> {
		requireHousehold(organizationId, householdId, currentUser)
		return listOf(UpdateItem("upd-1", "Spring Playoff Information", "Playoff brackets have been released.", "Posted recently"))
	}

	private fun requireHousehold(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): Household {
		val household = householdRepository.findById(householdId, organizationId)
			?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
		requireAccess(organizationId, householdId, currentUser)
		return household
	}

	private fun requireAccess(organizationId: UUID, householdId: UUID, currentUser: CurrentUser) {
		if (currentUser.platformAdministrator) return
		val hasOrgMembership = membershipRepository.findActiveMembership(organizationId, currentUser.userId) != null
		if (hasOrgMembership) return
		val hasGuardianRelationship = guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) != null
		if (hasGuardianRelationship) return
		val isHouseholdAdult = householdRepository.listAdults(householdId, organizationId)
			.any { it.email?.equals(currentUser.email, ignoreCase = true) == true }
		if (isHouseholdAdult) return
		throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household.")
	}
}
