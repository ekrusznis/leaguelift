package com.rally26.subscription.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.application.MembershipService
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.subscription.domain.SubscriptionPlan
import com.rally26.subscription.infra.StripeSubscriptionBillingClient
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.subscription.persistence.SubscriptionPlanRepository
import com.stripe.exception.ApiConnectionException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrganizationSubscriptionServiceTest {
    private val planRepository = mockk<SubscriptionPlanRepository>()
    private val subscriptionRepository = mockk<OrganizationSubscriptionRepository>()
    private val stripeBillingClient = mockk<StripeSubscriptionBillingClient>()
    private val organizationRepository = mockk<OrganizationRepository>()
    private val appUserRepository = mockk<AppUserRepository>()
    private val membershipService = mockk<MembershipService>()
    private val onboardingRepository = mockk<OwnerOnboardingRepository>()
    private val frontendProperties = mockk<FrontendProperties>()
    private val auditService = mockk<AuditService>()
    private val service =
        OrganizationSubscriptionService(
            planRepository,
            subscriptionRepository,
            stripeBillingClient,
            organizationRepository,
            appUserRepository,
            membershipService,
            onboardingRepository,
            frontendProperties,
            auditService,
        )

    @Test
    fun `platform subscription visibility rejects a normal organization user before querying data`() {
        val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

        assertFailsWith<ForbiddenException> {
            service.listForPlatformAdmin(null, null, 0, 25, user)
        }

        verify(exactly = 0) { subscriptionRepository.listForPlatformAdmin(any(), any(), any(), any()) }
        verify(exactly = 0) { subscriptionRepository.countForPlatformAdmin(any(), any()) }
    }

    @Test
    fun `platform subscription visibility normalizes filters and returns standard pagination`() {
        val admin = CurrentUser(UUID.randomUUID(), "admin@rally26.com", "Platform Admin", platformAdministrator = true)
        every { subscriptionRepository.listForPlatformAdmin("club", "PAST_DUE", 25, 25) } returns emptyList()
        every { subscriptionRepository.countForPlatformAdmin("club", "PAST_DUE") } returns 0L

        val result = service.listForPlatformAdmin(" club ", "past_due", 1, 25, admin)

        assertEquals(1, result.page)
        assertEquals(25, result.size)
        assertEquals(0L, result.totalElements)
        verify(exactly = 1) { subscriptionRepository.listForPlatformAdmin("club", "PAST_DUE", 25, 25) }
    }

    @Test
    fun `createCheckout wraps a StripeException as ServiceUnavailableException instead of leaking it`() {
        val admin = CurrentUser(UUID.randomUUID(), "admin@rally26.com", "Platform Admin", platformAdministrator = true)
        val organizationId = UUID.randomUUID()
        val organization =
            Organization(
                id = organizationId,
                name = "Test Club",
                slug = "test-club",
                organizationType = OrganizationType.TRAVEL_CLUB,
                status = OrganizationStatus.DRAFT,
                sports = listOf("Soccer"),
                contactEmail = "owner@example.com",
                contactPhone = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val plan =
            SubscriptionPlan(
                code = "FOUNDING_CLUB",
                name = "Founding Club",
                description = "Rally26 organization subscription.",
                amountMinor = 14900,
                currency = "usd",
                billingInterval = "MONTHLY",
                contactOnly = false,
                active = true,
                stripeProductId = null,
                stripePriceId = null,
            )
        every { organizationRepository.findById(organizationId) } returns organization
        every { planRepository.findByCodeForUpdate("FOUNDING_CLUB") } returns plan
        every { stripeBillingClient.ensurePlanAssets(plan) } throws ApiConnectionException("Simulated Stripe outage")

        assertFailsWith<ServiceUnavailableException> {
            service.createCheckout(organizationId, "FOUNDING_CLUB", admin)
        }
    }
}
