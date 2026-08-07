package com.rally26.subscription.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.application.MembershipService
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.subscription.infra.StripeSubscriptionBillingClient
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.subscription.persistence.SubscriptionPlanRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
}
