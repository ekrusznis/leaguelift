package com.rally26.subscription.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.subscription.domain.OrganizationSubscription
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import com.rally26.subscription.domain.SubscriptionPlan
import com.rally26.subscription.infra.StripeSubscriptionBillingClient
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import com.rally26.subscription.persistence.SubscriptionPlanRepository
import com.stripe.exception.ApiConnectionException
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrganizationSubscriptionServiceTest {
    private val planRepository = mockk<SubscriptionPlanRepository>()
    private val subscriptionRepository = mockk<OrganizationSubscriptionRepository>()
    private val stripeBillingClient = mockk<StripeSubscriptionBillingClient>()
    private val organizationRepository = mockk<OrganizationRepository>()
    private val appUserRepository = mockk<AppUserRepository>()
    private val membershipService = mockk<MembershipService>()
    private val onboardingRepository = mockk<OwnerOnboardingRepository>()
    private val frontendProperties = mockk<FrontendProperties>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val membershipRepository = mockk<MembershipRepository>()
    private val outboxWriter = mockk<OutboxWriter>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()
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
            membershipRepository,
            outboxWriter,
            objectMapper,
        )

    private fun subscription(
        organizationId: UUID,
        status: OrganizationSubscriptionStatus,
        stripeSubscriptionId: String? = "sub_123",
        stripeCustomerId: String? = "cus_123",
    ) = OrganizationSubscription(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        planCode = "FOUNDING_CLUB",
        status = status,
        stripeCustomerId = stripeCustomerId,
        stripeSubscriptionId = stripeSubscriptionId,
        stripeCheckoutSessionId = null,
        checkoutGeneration = 1,
        lastPaymentFailureAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun organization(id: UUID) =
        Organization(
            id = id,
            name = "Riverside Youth Sports",
            slug = "riverside-youth-sports",
            organizationType = OrganizationType.TRAVEL_CLUB,
            status = OrganizationStatus.ACTIVE,
            sports = listOf("Soccer"),
            contactEmail = "owner@example.test",
            contactPhone = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun ownerMembership(
        organizationId: UUID,
        userId: UUID,
    ) = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        userId = userId,
        role = MembershipRole.OWNER,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun appUser(
        id: UUID,
        email: String,
    ) = AppUser(id, email, "Owner", AppUserStatus.ACTIVE, "hash", Instant.now(), Instant.now())

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

    @Test
    fun `a payment failure enqueues a lifecycle email to every active owner and administrator`() {
        val organizationId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.ACTIVE)
        every { subscriptionRepository.findByStripeCustomerId("cus_123") } returns local
        every { subscriptionRepository.markPaymentFailure(local.id) } just runs
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        every { membershipRepository.listActiveManagers(organizationId) } returns listOf(ownerMembership(organizationId, ownerId))
        every { appUserRepository.findById(ownerId) } returns appUser(ownerId, "owner@example.test")
        val payloadSlot = slot<String>()
        every {
            outboxWriter.write(
                "organization_subscription",
                local.id,
                organizationId,
                "organization_subscription.payment_failed",
                capture(payloadSlot),
            )
        } just runs

        service.handleInvoicePaymentFailed(Invoice().apply { customer = "cus_123" })

        verify(exactly = 1) {
            outboxWriter.write("organization_subscription", local.id, organizationId, "organization_subscription.payment_failed", any())
        }
        assertTrue(payloadSlot.captured.contains("owner@example.test"))
        assertTrue(payloadSlot.captured.contains("Riverside Youth Sports"))
    }

    @Test
    fun `no owner or administrator with an email means no lifecycle email is enqueued`() {
        val organizationId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.ACTIVE)
        every { subscriptionRepository.findByStripeCustomerId("cus_123") } returns local
        every { subscriptionRepository.markPaymentFailure(local.id) } just runs
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        every { membershipRepository.listActiveManagers(organizationId) } returns emptyList()

        service.handleInvoicePaymentFailed(Invoice().apply { customer = "cus_123" })

        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a routine successful renewal while already active does not send a recovery email`() {
        val organizationId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.ACTIVE)
        every { subscriptionRepository.findByStripeCustomerId("cus_123") } returns local
        every { subscriptionRepository.markPaymentSuccess(local.id) } just runs

        service.handleInvoicePaid(Invoice().apply { customer = "cus_123" })

        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a successful payment while past due enqueues a real billing-recovered email`() {
        val organizationId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.PAST_DUE)
        every { subscriptionRepository.findByStripeCustomerId("cus_123") } returns local
        every { subscriptionRepository.markPaymentSuccess(local.id) } just runs
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        every { membershipRepository.listActiveManagers(organizationId) } returns listOf(ownerMembership(organizationId, ownerId))
        every { appUserRepository.findById(ownerId) } returns appUser(ownerId, "owner@example.test")

        service.handleInvoicePaid(Invoice().apply { customer = "cus_123" })

        verify(exactly = 1) {
            outboxWriter.write("organization_subscription", local.id, organizationId, "organization_subscription.payment_recovered", any())
        }
    }

    @Test
    fun `a subscription that stays canceled on a redelivered webhook does not resend the cancellation email`() {
        val organizationId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.CANCELED)
        every { subscriptionRepository.findByStripeSubscriptionId("sub_123") } returns local
        every { subscriptionRepository.syncExternalState(any(), any(), any(), any(), any()) } just runs
        every { onboardingRepository.suspendActivatedOrganization(organizationId) } just runs

        service.handleSubscriptionChanged(
            Subscription().apply {
                id = "sub_123"
                customer = "cus_123"
                status = "canceled"
            },
        )

        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), eq("organization_subscription.canceled"), any()) }
    }

    @Test
    fun `a subscription transitioning into canceled for the first time enqueues the cancellation email`() {
        val organizationId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.ACTIVE)
        every { subscriptionRepository.findByStripeSubscriptionId("sub_123") } returns local
        every { subscriptionRepository.syncExternalState(any(), any(), any(), any(), any()) } just runs
        every { onboardingRepository.suspendActivatedOrganization(organizationId) } just runs
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        every { membershipRepository.listActiveManagers(organizationId) } returns listOf(ownerMembership(organizationId, ownerId))
        every { appUserRepository.findById(ownerId) } returns appUser(ownerId, "owner@example.test")

        service.handleSubscriptionChanged(
            Subscription().apply {
                id = "sub_123"
                customer = "cus_123"
                status = "canceled"
            },
        )

        verify(exactly = 1) {
            outboxWriter.write("organization_subscription", local.id, organizationId, "organization_subscription.canceled", any())
        }
    }

    @Test
    fun `trial-will-end enqueues a real formatted trial end date`() {
        val organizationId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val local = subscription(organizationId, OrganizationSubscriptionStatus.TRIALING)
        every { subscriptionRepository.findByStripeSubscriptionId("sub_123") } returns local
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        every { membershipRepository.listActiveManagers(organizationId) } returns listOf(ownerMembership(organizationId, ownerId))
        every { appUserRepository.findById(ownerId) } returns appUser(ownerId, "owner@example.test")
        val payloadSlot = slot<String>()
        every {
            outboxWriter.write(
                "organization_subscription",
                local.id,
                organizationId,
                "organization_subscription.trial_ending",
                capture(payloadSlot),
            )
        } just runs

        // 2026-08-25T00:00:00Z
        service.handleTrialWillEnd(
            Subscription().apply {
                id = "sub_123"
                customer = "cus_123"
                trialEnd = 1787616000L
            },
        )

        verify(exactly = 1) {
            outboxWriter.write("organization_subscription", local.id, organizationId, "organization_subscription.trial_ending", any())
        }
        assertTrue(payloadSlot.captured.contains("2026-08-25"))
    }
}
