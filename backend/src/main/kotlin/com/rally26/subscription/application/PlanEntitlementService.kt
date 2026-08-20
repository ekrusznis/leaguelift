package com.rally26.subscription.application

import com.rally26.common.error.ForbiddenException
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val FREE = "FREE"
private const val STARTER = "STARTER"

/** FREE and STARTER share every restriction below except the team cap — this pairing recurs enough to name once. */
private val BASIC_TIERS = setOf(FREE, STARTER)

private val GATED_INTEGRATION_PROVIDERS =
    setOf(IntegrationProvider.SPORTSENGINE, IntegrationProvider.TEAMSNAP, IntegrationProvider.QUICKBOOKS_ONLINE)

/**
 * Real subscription-tier feature gating (DESIGN-DOC.md section 19.3 item 12,
 * Free/Starter/Club/League — Phase 26/§14.1I, extended to FREE by Phase 45/46
 * §14.1T/§14.1U). `plan_code` was previously stored and round-tripped to
 * Stripe/the UI but never read to gate anything until this service's first
 * call site — it follows this codebase's own established convention: explicit
 * imperative checks in the application-service layer, called alongside
 * `membershipService.require*Role(...)`/`authorizationService.require*Capability(...)`
 * — not a Spring `@PreAuthorize`-style annotation, since none exists anywhere in
 * this codebase. A small hardcoded per-plan-code `when` block, not a database
 * table — seven features across four tiers still doesn't warrant one.
 *
 * Every gate is exposed twice: a `...ForPlan(planCode)` function taking an explicit
 * plan code, and a thin `organizationId`-based wrapper over it. The explicit-code form
 * exists so [com.rally26.subscription.application.OrganizationPlanChangeService]'s
 * downgrade violation-scanner can ask "would tier X allow this" for a target tier the
 * organization isn't on yet — without it, that scanner would have to duplicate every
 * rule here against a hypothetical plan code instead of reusing the real one.
 *
 * An organization with no `organization_subscription` row yet (mid-onboarding,
 * or platform-seeded data) resolves to [STARTER], the most restrictive tier with
 * real Stripe billing — deny-by-default, matching
 * [com.rally26.authorization.application.AuthorizationService]'s own philosophy,
 * never the most permissive as a fallback. Note this is never FREE: a FREE
 * organization always has a real `organization_subscription` row (see
 * `OrganizationSubscriptionService.activateFreeSubscription`), so this fallback
 * is purely for organizations that have started onboarding but never finished
 * choosing/paying for a plan.
 */
@Service
class PlanEntitlementService(
    private val organizationSubscriptionRepository: OrganizationSubscriptionRepository,
) {
    fun planCodeFor(organizationId: UUID): String =
        organizationSubscriptionRepository.findByOrganizationId(organizationId)?.planCode ?: STARTER

    // --- Team capacity ---

    /** Null means unlimited (Club and League both allow unlimited teams today). */
    fun maxTeamsForPlan(planCode: String): Int? =
        when (planCode) {
            FREE -> 1
            STARTER -> 3
            else -> null
        }

    fun maxTeams(organizationId: UUID): Int? = maxTeamsForPlan(planCodeFor(organizationId))

    fun requireTeamCapacity(
        organizationId: UUID,
        currentTeamCount: Long,
    ) {
        val max = maxTeams(organizationId) ?: return
        if (currentTeamCount >= max) {
            throw ForbiddenException(
                "PLAN_UPGRADE_REQUIRED",
                "Your plan allows up to $max teams. Upgrade your plan to add more.",
            )
        }
    }

    // --- Dues & fee collection ---

    fun feesAllowedForPlan(planCode: String): Boolean = planCode != FREE

    fun feesAllowed(organizationId: UUID): Boolean = feesAllowedForPlan(planCodeFor(organizationId))

    fun requireFeesAllowed(organizationId: UUID) {
        if (!feesAllowed(organizationId)) {
            throw ForbiddenException("PLAN_UPGRADE_REQUIRED", "Upgrade your plan to collect dues and fees.")
        }
    }

    // --- SMS reminders ---

    fun smsAllowedForPlan(planCode: String): Boolean = planCode !in BASIC_TIERS

    fun smsAllowed(organizationId: UUID): Boolean = smsAllowedForPlan(planCodeFor(organizationId))

    // --- Named integrations (SportsEngine/TeamSnap/QuickBooks) ---

    fun integrationAllowedForPlan(
        planCode: String,
        provider: IntegrationProvider,
    ): Boolean {
        if (provider !in GATED_INTEGRATION_PROVIDERS) return true
        return planCode !in BASIC_TIERS
    }

    fun integrationAllowed(
        organizationId: UUID,
        provider: IntegrationProvider,
    ): Boolean = integrationAllowedForPlan(planCodeFor(organizationId), provider)

    fun requireIntegrationAllowed(
        organizationId: UUID,
        provider: IntegrationProvider,
    ) {
        if (!integrationAllowed(organizationId, provider)) {
            throw ForbiddenException(
                "PLAN_UPGRADE_REQUIRED",
                "Upgrade your plan to connect this integration.",
            )
        }
    }

    /** Exposed for `OrganizationPlanChangeService`'s downgrade violation-scanner, which needs to check every gated provider against a target tier. */
    fun gatedIntegrationProviders(): Set<IntegrationProvider> = GATED_INTEGRATION_PROVIDERS

    // --- Fundraising campaign capacity ---

    /** Null means unlimited. FREE/STARTER are "basic fundraising" — one concurrent campaign at a time. */
    fun maxConcurrentCampaignsForPlan(planCode: String): Int? =
        when (planCode) {
            in BASIC_TIERS -> 1
            else -> null
        }

    fun maxConcurrentCampaigns(organizationId: UUID): Int? = maxConcurrentCampaignsForPlan(planCodeFor(organizationId))

    fun requireCampaignCapacity(
        organizationId: UUID,
        currentActiveCampaignCount: Long,
    ) {
        val max = maxConcurrentCampaigns(organizationId) ?: return
        if (currentActiveCampaignCount >= max) {
            throw ForbiddenException(
                "PLAN_UPGRADE_REQUIRED",
                "Your plan allows $max active fundraising campaign(s) at a time. Upgrade your plan or close an existing campaign first.",
            )
        }
    }

    // --- Sponsorships ---

    fun sponsorshipsAllowedForPlan(planCode: String): Boolean = planCode !in BASIC_TIERS

    fun sponsorshipsAllowed(organizationId: UUID): Boolean = sponsorshipsAllowedForPlan(planCodeFor(organizationId))

    fun requireSponsorshipsAllowed(organizationId: UUID) {
        if (!sponsorshipsAllowed(organizationId)) {
            throw ForbiddenException("PLAN_UPGRADE_REQUIRED", "Upgrade your plan to create sponsorship packages.")
        }
    }

    // --- Family credits ---

    fun familyCreditsAllowedForPlan(planCode: String): Boolean = planCode !in BASIC_TIERS

    fun familyCreditsAllowed(organizationId: UUID): Boolean = familyCreditsAllowedForPlan(planCodeFor(organizationId))

    /** Lower tiers may still call the settings endpoint to confirm the program is off — only turning it on (a positive credit percent) is gated. */
    fun requireFamilyCreditsAllowed(
        organizationId: UUID,
        defaultCreditPercent: Int,
    ) {
        if (defaultCreditPercent > 0 && !familyCreditsAllowed(organizationId)) {
            throw ForbiddenException("PLAN_UPGRADE_REQUIRED", "Upgrade your plan to enable family credits.")
        }
    }

    // --- Advanced reporting ---

    fun advancedReportingAllowedForPlan(planCode: String): Boolean = planCode !in BASIC_TIERS

    fun advancedReportingAllowed(organizationId: UUID): Boolean = advancedReportingAllowedForPlan(planCodeFor(organizationId))

    fun requireAdvancedReportingAllowed(organizationId: UUID) {
        if (!advancedReportingAllowed(organizationId)) {
            throw ForbiddenException("PLAN_UPGRADE_REQUIRED", "Upgrade your plan for this report.")
        }
    }
}
