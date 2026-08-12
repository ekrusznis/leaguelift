package com.rally26.subscription.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanEntitlementServiceTest {
    private val organizationSubscriptionRepository = mockk<OrganizationSubscriptionRepository>()
    private val service = PlanEntitlementService(organizationSubscriptionRepository)
    private val orgId = UUID.randomUUID()

    private fun stubPlan(planCode: String?) {
        every { organizationSubscriptionRepository.findByOrganizationId(orgId) } returns
            planCode?.let { subscription(it) }
    }

    private fun subscription(planCode: String) =
        OrganizationSubscription(
            id = UUID.randomUUID(),
            organizationId = orgId,
            planCode = planCode,
            status = OrganizationSubscriptionStatus.ACTIVE,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_123",
            stripeCheckoutSessionId = null,
            checkoutGeneration = 1,
            lastPaymentFailureAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `an organization with no subscription row resolves to the most restrictive tier`() {
        stubPlan(null)

        assertEquals("STARTER", service.planCodeFor(orgId))
        assertEquals(3, service.maxTeams(orgId))
        assertFalse(service.smsAllowed(orgId))
    }

    @Test
    fun `starter caps team count at 3 and blocks SMS and gated integrations`() {
        stubPlan("STARTER")

        assertEquals(3, service.maxTeams(orgId))
        assertFalse(service.smsAllowed(orgId))
        assertFalse(service.integrationAllowed(orgId, IntegrationProvider.SPORTSENGINE))
        assertFailsWith<ValidationException> { service.requireTeamCapacity(orgId, 3) }
        assertFailsWith<ForbiddenException> { service.requireIntegrationAllowed(orgId, IntegrationProvider.TEAMSNAP) }
    }

    @Test
    fun `starter still allows team creation below the cap`() {
        stubPlan("STARTER")

        service.requireTeamCapacity(orgId, 2)
    }

    @Test
    fun `club and league have unlimited teams and allow SMS and gated integrations`() {
        for (planCode in listOf("FOUNDING_CLUB", "CONTACT_RALLY26")) {
            stubPlan(planCode)

            assertEquals(null, service.maxTeams(orgId))
            assertTrue(service.smsAllowed(orgId))
            assertTrue(service.integrationAllowed(orgId, IntegrationProvider.QUICKBOOKS_ONLINE))
            service.requireTeamCapacity(orgId, 500)
            service.requireIntegrationAllowed(orgId, IntegrationProvider.SPORTSENGINE)
        }
    }

    @Test
    fun `ungated providers are never blocked regardless of plan`() {
        stubPlan("STARTER")

        assertTrue(service.integrationAllowed(orgId, IntegrationProvider.ICS_FEED))
        assertTrue(service.integrationAllowed(orgId, IntegrationProvider.CSV_IMPORT))
    }
}
