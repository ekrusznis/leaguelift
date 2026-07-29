package com.leaguelift.payout.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.payout.domain.OrganizationPayoutAccount
import com.leaguelift.payout.infra.StripeAccountStatus
import com.leaguelift.payout.infra.StripeConnectClient
import com.leaguelift.payout.persistence.OrganizationPayoutAccountRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PayoutAccountServiceTest {

	private val payoutAccountRepository = mockk<OrganizationPayoutAccountRepository>()
	private val stripeConnectClient = mockk<StripeConnectClient>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val service = PayoutAccountService(payoutAccountRepository, stripeConnectClient, membershipService, auditService)

	private val orgId = UUID.randomUUID()
	private val owner = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
	private val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

	@Test
	fun `getStatus returns null when onboarding has never been started`() {
		every { membershipService.requireActiveMembership(orgId, owner) } returns ownerMembership()
		every { payoutAccountRepository.findByOrganizationId(orgId) } returns null

		val result = service.getStatus(orgId, owner)

		assertEquals(null, result)
	}

	@Test
	fun `startOnboarding requires owner role, not just manager`() {
		every { membershipService.requireOwnerRole(orgId, manager) } throws ForbiddenException("OWNER_ACTION_DENIED", "Only the organization owner can perform this action.")

		assertFailsWith<ForbiddenException> {
			service.startOnboarding(orgId, "https://app.leaguelift.local/refresh", "https://app.leaguelift.local/return", manager)
		}
		verify(exactly = 0) { stripeConnectClient.createExpressAccount() }
	}

	@Test
	fun `startOnboarding creates a Stripe account on first call and records audit`() {
		every { membershipService.requireOwnerRole(orgId, owner) } returns ownerMembership()
		every { payoutAccountRepository.findByOrganizationId(orgId) } returns null
		every { stripeConnectClient.createExpressAccount() } returns "acct_123"
		every { payoutAccountRepository.insert(orgId, "acct_123") } returns samplePayoutAccount()
		every { stripeConnectClient.createOnboardingLink("acct_123", any(), any()) } returns "https://connect.stripe.com/setup/abc"
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val url = service.startOnboarding(orgId, "https://app.leaguelift.local/refresh", "https://app.leaguelift.local/return", owner)

		assertEquals("https://connect.stripe.com/setup/abc", url)
		verify(exactly = 1) { stripeConnectClient.createExpressAccount() }
		verify(exactly = 1) { auditService.record(owner.userId, orgId, "payout.onboarding_started", "organization_payout_account", any(), any()) }
	}

	@Test
	fun `startOnboarding reuses the existing Stripe account on subsequent calls`() {
		val existing = samplePayoutAccount()
		every { membershipService.requireOwnerRole(orgId, owner) } returns ownerMembership()
		every { payoutAccountRepository.findByOrganizationId(orgId) } returns existing
		every { stripeConnectClient.createOnboardingLink(existing.stripeAccountId, any(), any()) } returns "https://connect.stripe.com/setup/refresh"
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		service.startOnboarding(orgId, "https://app.leaguelift.local/refresh", "https://app.leaguelift.local/return", owner)

		verify(exactly = 0) { stripeConnectClient.createExpressAccount() }
	}

	@Test
	fun `refreshStatus throws NotFoundException when onboarding was never started`() {
		every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
		every { payoutAccountRepository.findByOrganizationId(orgId) } returns null

		assertFailsWith<NotFoundException> {
			service.refreshStatus(orgId, owner)
		}
	}

	@Test
	fun `refreshStatus syncs booleans from Stripe`() {
		val existing = samplePayoutAccount()
		val updated = existing.copy(detailsSubmitted = true, chargesEnabled = true, payoutsEnabled = true)
		every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
		every { payoutAccountRepository.findByOrganizationId(orgId) } returnsMany listOf(existing, updated)
		every { stripeConnectClient.retrieveAccountStatus(existing.stripeAccountId) } returns StripeAccountStatus(true, true, true)
		every { payoutAccountRepository.updateStatus(orgId, true, true, true) } returns 1

		val result = service.refreshStatus(orgId, owner)

		assertEquals(true, result.isFullyConnected)
	}

	private fun samplePayoutAccount() = OrganizationPayoutAccount(
		id = UUID.randomUUID(),
		organizationId = orgId,
		stripeAccountId = "acct_123",
		detailsSubmitted = false,
		chargesEnabled = false,
		payoutsEnabled = false,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun ownerMembership() = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = orgId,
		userId = owner.userId,
		role = MembershipRole.OWNER,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
