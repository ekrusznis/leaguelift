package com.rally26.subscription.integration

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.fee.application.FeeService
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.onboarding.owner.application.OwnerOnboardingService
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.team.application.TeamService
import com.rally26.team.domain.Sport
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Phase 45/46 (DESIGN-DOC.md §14.1T/§14.1U) — the FREE plan bypasses Stripe Checkout
 * entirely and its entitlement gates must be backend-authoritative, not UI-only. Real
 * Postgres/real service calls throughout, matching this codebase's established
 * integration-test convention (see `AbstractIntegrationTest`).
 */
class FreeSubscriptionIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var ownerOnboardingService: OwnerOnboardingService

    @Autowired
    lateinit var ownerOnboardingRepository: OwnerOnboardingRepository

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Autowired
    lateinit var organizationSubscriptionRepository: OrganizationSubscriptionRepository

    @Autowired
    lateinit var teamService: TeamService

    @Autowired
    lateinit var feeService: FeeService

    private fun registerAndDraftOrganization(): Pair<CurrentUser, UUID> {
        val appUser =
            passwordAuthenticationService.register(
                "free-owner-${System.nanoTime()}@example.com",
                "password1234",
                "Free Owner",
            )
        val owner = passwordAuthenticationService.toCurrentUser(appUser)
        // register() (unlike the public registerOwner() endpoint flow) doesn't create the
        // resumable owner_onboarding row itself — mirroring what registerOwner() does,
        // without also requiring this test to simulate email verification.
        ownerOnboardingRepository.createForOwner(appUser.id)
        val snapshot =
            ownerOnboardingService.saveOrganization(
                name = "Free Org",
                slug = "free-org-${System.nanoTime()}",
                organizationType = OrganizationType.RECREATIONAL_LEAGUE,
                sports = listOf("Soccer"),
                contactEmail = "contact-${System.nanoTime()}@example.com",
                contactPhone = null,
                addressLine1 = "1 Main St",
                addressLine2 = null,
                addressCity = "Anytown",
                addressState = "CA",
                addressPostalCode = "90210",
                addressCountry = "US",
                timezone = "America/Los_Angeles",
                currentUser = owner,
            )
        return owner to snapshot.onboarding.organizationId!!
    }

    @Test
    fun `registering with the Free plan activates the organization without any Stripe checkout`() {
        val (owner, organizationId) = registerAndDraftOrganization()

        val snapshot = ownerOnboardingService.activateFreePlan(owner)

        assertEquals("COMPLETE", snapshot.onboarding.currentStep.name)
        assertEquals("FREE", snapshot.onboarding.selectedPlanCode)

        val organization = organizationRepository.findById(organizationId)!!
        assertEquals(OrganizationStatus.ACTIVE, organization.status)

        val subscription = organizationSubscriptionRepository.findByOrganizationId(organizationId)!!
        assertEquals("FREE", subscription.planCode)
        assertEquals(OrganizationSubscriptionStatus.ACTIVE, subscription.status)
        assertNull(subscription.stripeCustomerId)
        assertNull(subscription.stripeSubscriptionId)
    }

    @Test
    fun `a Free organization can create exactly one team and is blocked from a second, directly at the service layer`() {
        val (owner, organizationId) = registerAndDraftOrganization()
        ownerOnboardingService.activateFreePlan(owner)

        teamService.create(organizationId, "Team A", Sport.SOCCER, null, null, null, null, null, owner)

        val denied =
            assertFailsWith<ForbiddenException> {
                teamService.create(organizationId, "Team B", Sport.SOCCER, null, null, null, null, null, owner)
            }
        assertEquals("PLAN_UPGRADE_REQUIRED", denied.code)
    }

    @Test
    fun `a Free organization is blocked from creating a fee template, directly at the service layer`() {
        val (owner, organizationId) = registerAndDraftOrganization()
        ownerOnboardingService.activateFreePlan(owner)

        val denied =
            assertFailsWith<ForbiddenException> {
                feeService.createTemplate(organizationId, "Dues", null, 5_000L, "USD", owner)
            }
        assertEquals("PLAN_UPGRADE_REQUIRED", denied.code)
    }
}
