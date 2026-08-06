package com.rally26.dashboard.application

import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.application.FamilyCreditService
import com.rally26.dashboard.web.AthleteSummary
import com.rally26.dashboard.web.FamilyCredits
import com.rally26.dashboard.web.FeeLineItem
import com.rally26.dashboard.web.FundraiserSummary
import com.rally26.dashboard.web.HouseholdOverviewResponse
import com.rally26.dashboard.web.OrderSummary
import com.rally26.dashboard.web.OutstandingBalance
import com.rally26.dashboard.web.RequiredActionItem
import com.rally26.dashboard.web.ScheduleItem
import com.rally26.dashboard.web.UpdateItem
import com.rally26.event.application.EventService
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.computeFeeBalance
import com.rally26.fee.persistence.FeeAdjustmentRepository
import com.rally26.fee.persistence.FeePaymentRepository
import com.rally26.fee.persistence.FeeRepository
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.household.domain.Household
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.persistence.TeamRepository
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
    private val familyCreditService: FamilyCreditService,
) {
    fun getOverview(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): HouseholdOverviewResponse {
        val household = requireHousehold(organizationId, householdId, currentUser)
        return HouseholdOverviewResponse(household.displayName)
    }

    fun getAthletes(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<AthleteSummary> {
        requireHousehold(organizationId, householdId, currentUser)
        return participantRepository.findByHousehold(householdId, organizationId).map { participant ->
            val teamNames =
                participantRepository
                    .listTeamAssignments(participant.id, organizationId)
                    .mapNotNull { teamRepository.findById(it.teamId, organizationId)?.name }
            AthleteSummary(participant.id, "${participant.firstName} ${participant.lastName}", teamNames)
        }
    }

    fun getFamilySchedule(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<ScheduleItem> {
        requireHousehold(organizationId, householdId, currentUser)
        return dashboardEventMapper
            .upcoming(
                eventService.listForHousehold(organizationId, householdId, currentUser, offset = 0, limit = 50),
            ).map { dashboardEventMapper.toScheduleItem(it, organizationId) }
    }

    fun getOutstandingBalance(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): OutstandingBalance {
        requireHousehold(organizationId, householdId, currentUser)
        val feeAssignments = feeRepository.findByHousehold(householdId, organizationId, offset = 0, limit = FEE_ASSIGNMENT_LIMIT)
        val currency = feeAssignments.firstOrNull()?.currency ?: "USD"
        val lineItems =
            feeAssignments
                .filter { it.status in OUTSTANDING_STATUSES }
                .map { assignment ->
                    val paid = feePaymentRepository.sumActiveByAssignment(assignment.id, organizationId)
                    val adjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, organizationId)
                    val balance = computeFeeBalance(assignment.originalAmountMinor, paid, adjusted)
                    FeeLineItem(assignment.description, balance.balanceMinor, assignment.status.name, assignment.dueDate)
                }.filter { it.balanceMinor > 0 }
        return OutstandingBalance(
            totalOutstandingMinor = lineItems.sumOf { it.balanceMinor },
            currency = currency,
            lineItems = lineItems,
        )
    }

    fun getFamilyCredits(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): FamilyCredits {
        requireHousehold(organizationId, householdId, currentUser)
        val balance = familyCreditService.getBalance(organizationId, householdId, currentUser)
        return FamilyCredits(
            isDemoData = false,
            currency = balance.currency,
            pendingMinor = balance.pendingMinor,
            availableMinor = balance.availableMinor,
            appliedThisSeasonMinor = balance.appliedAllTimeMinor,
        )
    }

    fun getActiveFundraisers(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<FundraiserSummary> {
        requireHousehold(organizationId, householdId, currentUser)
        return campaignRepository
            .findAll(organizationId, offset = 0, limit = CAMPAIGN_LIMIT)
            .filter { it.status == CampaignStatus.ACTIVE }
            .map {
                FundraiserSummary(
                    campaignId = it.id,
                    name = it.name,
                    slug = it.slug,
                    isRaisedDemoData = false,
                    raisedMinor = contributionRepository.sumConfirmedByCampaign(it.id),
                    goalMinor = it.goalAmountMinor,
                    currency = it.currency,
                )
            }
    }

    fun getRecentOrders(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<OrderSummary> {
        requireHousehold(organizationId, householdId, currentUser)
        return listOf(OrderSummary("ord-1", "Riverside Soccer Hoodie", "#LL-78231", LocalDate.now().minusDays(10), "Shipped"))
    }

    fun getRequiredActions(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<RequiredActionItem> {
        requireHousehold(organizationId, householdId, currentUser)
        return listOf(RequiredActionItem("act-1", "warning", "Uniform Size Needed", "Uniform order needs a size selection", "Due soon"))
    }

    fun getOrganizationUpdates(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<UpdateItem> {
        requireHousehold(organizationId, householdId, currentUser)
        return listOf(UpdateItem("upd-1", "Spring Playoff Information", "Playoff brackets have been released.", "Posted recently"))
    }

    private fun requireHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): Household {
        val household =
            householdRepository.findById(householdId, organizationId)
                ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        requireAccess(organizationId, householdId, currentUser)
        return household
    }

    private fun requireAccess(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ) {
        if (currentUser.platformAdministrator) return
        val hasOrgMembership = membershipRepository.findActiveMembership(organizationId, currentUser.userId) != null
        if (hasOrgMembership) return
        val hasGuardianRelationship = guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) != null
        if (hasGuardianRelationship) return
        val isHouseholdAdult =
            householdRepository
                .listAdults(householdId, organizationId)
                .any { it.email?.equals(currentUser.email, ignoreCase = true) == true }
        if (isHouseholdAdult) return
        throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household.")
    }
}
