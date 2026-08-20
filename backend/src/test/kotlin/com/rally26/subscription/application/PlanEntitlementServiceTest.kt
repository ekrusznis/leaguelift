package com.rally26.subscription.application

import com.rally26.common.error.ForbiddenException
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
    fun `an organization with no subscription row resolves to the most restrictive paid tier, not free`() {
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
        assertFailsWith<ForbiddenException> { service.requireTeamCapacity(orgId, 3) }
        assertFailsWith<ForbiddenException> { service.requireIntegrationAllowed(orgId, IntegrationProvider.TEAMSNAP) }
    }

    @Test
    fun `free caps team count at 1 and blocks fees, sms, integrations, sponsorships, family credits, and advanced reporting`() {
        stubPlan("FREE")

        assertEquals(1, service.maxTeams(orgId))
        assertFalse(service.feesAllowed(orgId))
        assertFalse(service.smsAllowed(orgId))
        assertFalse(service.integrationAllowed(orgId, IntegrationProvider.QUICKBOOKS_ONLINE))
        assertFalse(service.sponsorshipsAllowed(orgId))
        assertFalse(service.familyCreditsAllowed(orgId))
        assertFalse(service.advancedReportingAllowed(orgId))
        assertEquals(1, service.maxConcurrentCampaigns(orgId))
        assertFailsWith<ForbiddenException> { service.requireTeamCapacity(orgId, 1) }
        assertFailsWith<ForbiddenException> { service.requireFeesAllowed(orgId) }
        assertFailsWith<ForbiddenException> { service.requireSponsorshipsAllowed(orgId) }
        assertFailsWith<ForbiddenException> { service.requireAdvancedReportingAllowed(orgId) }
        assertFailsWith<ForbiddenException> { service.requireCampaignCapacity(orgId, 1) }
    }

    @Test
    fun `free still allows dropping to zero family credits and creating the first team or campaign`() {
        stubPlan("FREE")

        service.requireTeamCapacity(orgId, 0)
        service.requireCampaignCapacity(orgId, 0)
        service.requireFamilyCreditsAllowed(orgId, 0)
    }

    @Test
    fun `starter allows fees but still blocks sponsorships, family credits, and advanced reporting`() {
        stubPlan("STARTER")

        assertTrue(service.feesAllowed(orgId))
        assertFalse(service.sponsorshipsAllowed(orgId))
        assertFalse(service.familyCreditsAllowed(orgId))
        assertFalse(service.advancedReportingAllowed(orgId))
        assertEquals(1, service.maxConcurrentCampaigns(orgId))
    }

    @Test
    fun `starter still allows team creation below the cap`() {
        stubPlan("STARTER")

        service.requireTeamCapacity(orgId, 2)
    }

    @Test
    fun `club and league have unlimited teams and campaigns and allow every gate`() {
        for (planCode in listOf("FOUNDING_CLUB", "CONTACT_RALLY26")) {
            stubPlan(planCode)

            assertEquals(null, service.maxTeams(orgId))
            assertEquals(null, service.maxConcurrentCampaigns(orgId))
            assertTrue(service.smsAllowed(orgId))
            assertTrue(service.feesAllowed(orgId))
            assertTrue(service.sponsorshipsAllowed(orgId))
            assertTrue(service.familyCreditsAllowed(orgId))
            assertTrue(service.advancedReportingAllowed(orgId))
            assertTrue(service.integrationAllowed(orgId, IntegrationProvider.QUICKBOOKS_ONLINE))
            service.requireTeamCapacity(orgId, 500)
            service.requireCampaignCapacity(orgId, 500)
            service.requireFeesAllowed(orgId)
            service.requireSponsorshipsAllowed(orgId)
            service.requireFamilyCreditsAllowed(orgId, 100)
            service.requireAdvancedReportingAllowed(orgId)
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
