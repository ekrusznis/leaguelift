package com.rally26.subscription.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val STARTER = "STARTER"

private val GATED_INTEGRATION_PROVIDERS =
    setOf(IntegrationProvider.SPORTSENGINE, IntegrationProvider.TEAMSNAP, IntegrationProvider.QUICKBOOKS_ONLINE)

/**
 * Real subscription-tier feature gating (DESIGN-DOC.md section 19.3 item 12,
 * Starter/Club/League — Phase 26/§14.1I). `plan_code` was previously stored and
 * round-tripped to Stripe/the UI but never read to gate anything — this is the
 * first real call site. Follows this codebase's own established convention:
 * explicit imperative checks in the application-service layer, called alongside
 * `membershipService.require*Role(...)`/`authorizationService.require*Capability(...)`
 * — not a Spring `@PreAuthorize`-style annotation, since none exists anywhere in
 * this codebase. A small hardcoded per-plan-code `when` block, not a database
 * table — four features across three tiers doesn't warrant one.
 *
 * An organization with no `organization_subscription` row yet (mid-onboarding,
 * or platform-seeded data) resolves to [STARTER], the most restrictive tier —
 * deny-by-default, matching [com.rally26.authorization.application.AuthorizationService]'s
 * own philosophy, never the most permissive as a fallback.
 */
@Service
class PlanEntitlementService(
    private val organizationSubscriptionRepository: OrganizationSubscriptionRepository,
) {
    fun planCodeFor(organizationId: UUID): String =
        organizationSubscriptionRepository.findByOrganizationId(organizationId)?.planCode ?: STARTER

    /** Null means unlimited (Club and League both allow unlimited teams today — only Starter caps). */
    fun maxTeams(organizationId: UUID): Int? =
        when (planCodeFor(organizationId)) {
            STARTER -> 3
            else -> null
        }

    fun requireTeamCapacity(
        organizationId: UUID,
        currentTeamCount: Long,
    ) {
        val max = maxTeams(organizationId) ?: return
        if (currentTeamCount >= max) {
            throw ValidationException(
                "Your plan allows up to $max teams. Upgrade your plan to add more.",
            )
        }
    }

    fun smsAllowed(organizationId: UUID): Boolean = planCodeFor(organizationId) != STARTER

    fun integrationAllowed(
        organizationId: UUID,
        provider: IntegrationProvider,
    ): Boolean {
        if (provider !in GATED_INTEGRATION_PROVIDERS) return true
        return planCodeFor(organizationId) != STARTER
    }

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
}
