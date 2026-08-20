package com.rally26.subscription.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.persistence.OrganizationCreditSettingsRepository
import com.rally26.fee.persistence.FeeRepository
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.integration.core.persistence.IntegrationConnectionRepository
import com.rally26.membership.application.MembershipService
import com.rally26.sponsorship.persistence.SponsorshipPackageRepository
import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.infra.StripeSubscriptionBillingClient
import com.rally26.subscription.infra.StripeSubscriptionCheckout
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.subscription.persistence.SubscriptionPlanRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Never CONTACT_RALLY26 — League is sales-only, never a self-serve upgrade/downgrade target. */
private val PLAN_RANK = mapOf("FREE" to 0, "STARTER" to 1, "FOUNDING_CLUB" to 2)

enum class PlanChangeDirection { UPGRADE, DOWNGRADE }

data class PlanChangeViolation(
    val code: String,
    val message: String,
    val actionLink: String?,
)

data class PlanChangePreview(
    val currentPlanCode: String,
    val targetPlanCode: String,
    val direction: PlanChangeDirection,
    val violations: List<PlanChangeViolation>,
)

sealed class PlanChangeResult {
    data class Blocked(
        val violations: List<PlanChangeViolation>,
    ) : PlanChangeResult()

    data class CheckoutRequired(
        val checkout: StripeSubscriptionCheckout,
    ) : PlanChangeResult()

    data class AppliedImmediately(
        val subscription: OrganizationSubscription,
    ) : PlanChangeResult()

    data class ScheduledDowngrade(
        val effectiveAt: Instant,
    ) : PlanChangeResult()
}

/**
 * Real in-app upgrade/downgrade (Phase 46, DESIGN-DOC.md §14.1U) — kept separate from
 * [OrganizationSubscriptionService], which owns checkout/webhook lifecycle; this owns the
 * live-org plan-switch flow. Every gate reused here comes straight from
 * [PlanEntitlementService]'s `...ForPlan(planCode)` functions — the exact same rules a
 * live organization is held to, evaluated against a *target* tier the organization isn't
 * on yet, so a downgrade can never be approved into a state the entitlement layer would
 * then immediately reject on the next mutating call.
 */
@Service
class OrganizationPlanChangeService(
    private val membershipService: MembershipService,
    private val planEntitlementService: PlanEntitlementService,
    private val planRepository: SubscriptionPlanRepository,
    private val subscriptionRepository: OrganizationSubscriptionRepository,
    private val subscriptionService: OrganizationSubscriptionService,
    private val stripeBillingClient: StripeSubscriptionBillingClient,
    private val teamRepository: TeamRepository,
    private val feeRepository: FeeRepository,
    private val campaignRepository: CampaignRepository,
    private val sponsorshipPackageRepository: SponsorshipPackageRepository,
    private val creditSettingsRepository: OrganizationCreditSettingsRepository,
    private val integrationConnectionRepository: IntegrationConnectionRepository,
    private val auditService: AuditService,
) {
    fun previewPlanChange(
        organizationId: UUID,
        targetPlanCode: String,
        currentUser: CurrentUser,
    ): PlanChangePreview {
        membershipService.requireOwnerRoleForBilling(organizationId, currentUser)
        val local = requireSubscription(organizationId)
        val direction = directionFor(local.planCode, targetPlanCode)
        val violations =
            if (direction ==
                PlanChangeDirection.DOWNGRADE
            ) {
                scanDowngradeViolations(organizationId, targetPlanCode)
            } else {
                emptyList()
            }
        return PlanChangePreview(local.planCode, targetPlanCode, direction, violations)
    }

    @Transactional
    fun applyPlanChange(
        organizationId: UUID,
        targetPlanCode: String,
        currentUser: CurrentUser,
    ): PlanChangeResult {
        membershipService.requireOwnerRoleForBilling(organizationId, currentUser)
        val local = requireSubscription(organizationId)
        val targetPlan =
            planRepository
                .findByCodeForUpdate(targetPlanCode)
                ?.takeIf { it.active }
                ?: throw NotFoundException("SUBSCRIPTION_PLAN_NOT_FOUND", "The subscription plan could not be found.")
        if (targetPlan.contactOnly) {
            throw ValidationException("Contact Rally26 to move to the League plan.")
        }
        val direction = directionFor(local.planCode, targetPlan.code)

        if (direction == PlanChangeDirection.DOWNGRADE) {
            val violations = scanDowngradeViolations(organizationId, targetPlan.code)
            if (violations.isNotEmpty()) {
                auditService.record(
                    actorUserId = currentUser.userId,
                    organizationId = organizationId,
                    action = "organization_subscription.plan_change_blocked",
                    entityType = "organization_subscription",
                    entityId = local.id,
                    metadataJson = "{\"targetPlanCode\":\"${targetPlan.code}\",\"violationCount\":${violations.size}}",
                )
                return PlanChangeResult.Blocked(violations)
            }
        }

        return when {
            local.planCode == "FREE" -> {
                val checkout = subscriptionService.startUpgradeCheckout(organizationId, targetPlan.code, currentUser)
                PlanChangeResult.CheckoutRequired(checkout)
            }
            targetPlan.code == "FREE" -> {
                val subscriptionId =
                    local.stripeSubscriptionId
                        ?: throw ConflictException(
                            "SUBSCRIPTION_MISSING_STRIPE_ID",
                            "This subscription has no linked Stripe subscription to schedule a downgrade on.",
                        )
                val generation = subscriptionRepository.nextPlanChangeGeneration(local.id)
                val result = stripeBillingClient.scheduleCancelAtPeriodEnd(subscriptionId, generation)
                val effectiveAt =
                    result.currentPeriodEnd
                        ?: throw ConflictException(
                            "SUBSCRIPTION_PERIOD_END_UNKNOWN",
                            "Stripe did not report a current billing period end for this subscription.",
                        )
                subscriptionRepository.markPendingDowngrade(local.id, targetPlan.code, effectiveAt)
                auditService.record(
                    actorUserId = currentUser.userId,
                    organizationId = organizationId,
                    action = "organization_subscription.downgrade_scheduled",
                    entityType = "organization_subscription",
                    entityId = local.id,
                    metadataJson = "{\"targetPlanCode\":\"${targetPlan.code}\",\"effectiveAt\":\"$effectiveAt\"}",
                )
                subscriptionService.enqueueLifecycleEmail(organizationId, local.id, "organization_subscription.downgrade_scheduled")
                PlanChangeResult.ScheduledDowngrade(effectiveAt)
            }
            else -> {
                val subscriptionId =
                    local.stripeSubscriptionId
                        ?: throw ConflictException(
                            "SUBSCRIPTION_MISSING_STRIPE_ID",
                            "This subscription has no linked Stripe subscription to change the price on.",
                        )
                val assets = stripeBillingClient.ensurePlanAssets(targetPlan)
                if (targetPlan.stripeProductId != assets.productId || targetPlan.stripePriceId != assets.priceId) {
                    planRepository.saveStripeIds(targetPlan.code, assets.productId, assets.priceId)
                }
                val generation = subscriptionRepository.nextPlanChangeGeneration(local.id)
                stripeBillingClient.updateSubscriptionPrice(subscriptionId, assets.priceId, targetPlan.code, generation)
                subscriptionRepository.updatePlan(local.id, targetPlan.code)
                val updated = subscriptionRepository.findById(local.id)!!
                auditService.record(
                    actorUserId = currentUser.userId,
                    organizationId = organizationId,
                    action = "organization_subscription.plan_change_applied",
                    entityType = "organization_subscription",
                    entityId = local.id,
                    metadataJson = "{\"targetPlanCode\":\"${targetPlan.code}\"}",
                )
                subscriptionService.enqueueLifecycleEmail(organizationId, local.id, "organization_subscription.plan_change_applied")
                PlanChangeResult.AppliedImmediately(updated)
            }
        }
    }

    /** Checked against the *target* tier — every gate [PlanEntitlementService] enforces live, evaluated one tier ahead of where the organization actually is. */
    private fun scanDowngradeViolations(
        organizationId: UUID,
        targetPlanCode: String,
    ): List<PlanChangeViolation> {
        val violations = mutableListOf<PlanChangeViolation>()
        val basePath = "/app/organizations/$organizationId"

        planEntitlementService.maxTeamsForPlan(targetPlanCode)?.let { max ->
            val count = teamRepository.countAll(organizationId)
            if (count > max) {
                violations +=
                    PlanChangeViolation(
                        "TEAM_COUNT_OVER_LIMIT",
                        "You have $count teams, but this plan allows up to $max. Remove teams first, or contact support for help.",
                        "$basePath/teams",
                    )
            }
        }

        if (!planEntitlementService.feesAllowedForPlan(targetPlanCode)) {
            val hasFees =
                feeRepository.countAllTemplates(organizationId) > 0 ||
                    feeRepository.countAllForOrganization(organizationId, null, false) > 0
            if (hasFees) {
                violations +=
                    PlanChangeViolation(
                        "FEES_EXIST",
                        "This plan does not include dues and fee collection, but your organization has existing fee templates or assignments. Remove them first, or contact support for help.",
                        "$basePath/fees",
                    )
            }
        }

        planEntitlementService.maxConcurrentCampaignsForPlan(targetPlanCode)?.let { max ->
            val count = campaignRepository.countActive(organizationId)
            if (count > max) {
                violations +=
                    PlanChangeViolation(
                        "CAMPAIGN_COUNT_OVER_LIMIT",
                        "You have $count active fundraising campaign(s), but this plan allows up to $max at a time. Close a campaign first, or contact support for help.",
                        "$basePath/fundraising",
                    )
            }
        }

        if (!planEntitlementService.sponsorshipsAllowedForPlan(targetPlanCode) &&
            sponsorshipPackageRepository.countAll(organizationId) > 0
        ) {
            violations +=
                PlanChangeViolation(
                    "SPONSORSHIPS_EXIST",
                    "This plan does not include sponsorships, but your organization has existing sponsorship packages. Remove them first, or contact support for help.",
                    "$basePath/sponsorships",
                )
        }

        if (!planEntitlementService.familyCreditsAllowedForPlan(targetPlanCode)) {
            val settings = creditSettingsRepository.getOrCreateDefault(organizationId)
            if (settings.defaultCreditPercent > 0) {
                violations +=
                    PlanChangeViolation(
                        "FAMILY_CREDITS_ENABLED",
                        "This plan does not include family credits, but your organization's credit program is still enabled. Turn it off first, or contact support for help.",
                        "$basePath/financial-operations",
                    )
            }
        }

        planEntitlementService.gatedIntegrationProviders().forEach { provider ->
            if (!planEntitlementService.integrationAllowedForPlan(targetPlanCode, provider) &&
                integrationConnectionRepository.findActiveForOrganization(organizationId, provider) != null
            ) {
                violations +=
                    PlanChangeViolation(
                        "INTEGRATION_ACTIVE_${provider.name}",
                        "This plan does not include the ${provider.name} integration, which is still connected. Disconnect it first, or contact support for help.",
                        "$basePath/integrations",
                    )
            }
        }

        return violations
    }

    private fun requireSubscription(organizationId: UUID): OrganizationSubscription =
        subscriptionRepository.findByOrganizationIdForUpdate(organizationId)
            ?: throw NotFoundException("SUBSCRIPTION_NOT_FOUND", "No organization subscription exists yet.")

    private fun directionFor(
        currentPlanCode: String,
        targetPlanCode: String,
    ): PlanChangeDirection {
        val currentRank = PLAN_RANK[currentPlanCode] ?: throw ValidationException("Unrecognized current plan.")
        val targetRank = PLAN_RANK[targetPlanCode] ?: throw ValidationException("This plan is not a valid self-service target.")
        if (currentRank == targetRank) throw ValidationException("PLAN_UNCHANGED: the organization is already on this plan.")
        return if (targetRank > currentRank) PlanChangeDirection.UPGRADE else PlanChangeDirection.DOWNGRADE
    }
}
