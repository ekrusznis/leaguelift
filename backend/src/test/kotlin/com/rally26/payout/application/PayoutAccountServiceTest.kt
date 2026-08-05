package com.rally26.payout.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.application.PayoutSummary
import com.rally26.ledger.domain.LedgerDirection
import com.rally26.ledger.domain.LedgerEntry
import com.rally26.ledger.domain.LedgerEntryType
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.payout.domain.OrganizationPayoutAccount
import com.rally26.payout.infra.StripeAccountStatus
import com.rally26.payout.infra.StripeConnectClient
import com.rally26.payout.persistence.OrganizationPayoutAccountRepository
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
    private val ledgerService = mockk<LedgerService>()
    private val service = PayoutAccountService(payoutAccountRepository, stripeConnectClient, membershipService, auditService, ledgerService)

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
        every { membershipService.requireOwnerRole(orgId, manager) } throws
            ForbiddenException("OWNER_ACTION_DENIED", "Only the organization owner can perform this action.")

        assertFailsWith<ForbiddenException> {
            service.startOnboarding(orgId, "https://app.rally26.local/refresh", "https://app.rally26.local/return", manager)
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

        val url = service.startOnboarding(orgId, "https://app.rally26.local/refresh", "https://app.rally26.local/return", owner)

        assertEquals("https://connect.stripe.com/setup/abc", url)
        verify(exactly = 1) { stripeConnectClient.createExpressAccount() }
        verify(
            exactly = 1,
        ) { auditService.record(owner.userId, orgId, "payout.onboarding_started", "organization_payout_account", any(), any()) }
    }

    @Test
    fun `startOnboarding reuses the existing Stripe account on subsequent calls`() {
        val existing = samplePayoutAccount()
        every { membershipService.requireOwnerRole(orgId, owner) } returns ownerMembership()
        every { payoutAccountRepository.findByOrganizationId(orgId) } returns existing
        every { stripeConnectClient.createOnboardingLink(existing.stripeAccountId, any(), any()) } returns
            "https://connect.stripe.com/setup/refresh"
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.startOnboarding(orgId, "https://app.rally26.local/refresh", "https://app.rally26.local/return", owner)

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

    @Test
    fun `triggerTransfer requires manager role, not just active membership`() {
        every { membershipService.requireManagerRole(orgId, manager) } throws
            ForbiddenException("MEMBERSHIP_MANAGEMENT_DENIED", "Only organization owners and administrators can manage members.")

        assertFailsWith<ForbiddenException> {
            service.triggerTransfer(orgId, manager)
        }
        verify(exactly = 0) { stripeConnectClient.createTransfer(any(), any(), any()) }
    }

    @Test
    fun `triggerTransfer throws NotFoundException when onboarding was never started`() {
        every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
        every { payoutAccountRepository.findByOrganizationId(orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.triggerTransfer(orgId, owner)
        }
    }

    @Test
    fun `triggerTransfer rejects an account not yet enabled for payouts`() {
        every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
        every { payoutAccountRepository.findByOrganizationId(orgId) } returns samplePayoutAccount().copy(payoutsEnabled = false)

        assertFailsWith<ValidationException> {
            service.triggerTransfer(orgId, owner)
        }
        verify(exactly = 0) { stripeConnectClient.createTransfer(any(), any(), any()) }
    }

    @Test
    fun `triggerTransfer no-ops when nothing is eligible to transfer`() {
        every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
        every { payoutAccountRepository.findByOrganizationId(orgId) } returns samplePayoutAccount().copy(payoutsEnabled = true)
        every { ledgerService.getTransferableEntries(orgId) } returns emptyList()
        every { ledgerService.getPayoutSummary(orgId) } returns PayoutSummary(0, 0, 0, 0)

        val result = service.triggerTransfer(orgId, owner)

        assertEquals(0L, result.netAvailableMinor)
        verify(exactly = 0) { stripeConnectClient.createTransfer(any(), any(), any()) }
    }

    @Test
    fun `triggerTransfer no-ops when pending debits exceed eligible earnings`() {
        every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
        every { payoutAccountRepository.findByOrganizationId(orgId) } returns samplePayoutAccount().copy(payoutsEnabled = true)
        every { ledgerService.getTransferableEntries(orgId) } returns
            listOf(
                earningEntry(LedgerDirection.CREDIT, 1_000L),
                earningEntry(LedgerDirection.DEBIT, 5_000L),
            )
        every { ledgerService.getPayoutSummary(orgId) } returns PayoutSummary(1_000, 0, 5_000, -4_000)

        val result = service.triggerTransfer(orgId, owner)

        assertEquals(-4_000L, result.netAvailableMinor)
        verify(exactly = 0) { stripeConnectClient.createTransfer(any(), any(), any()) }
    }

    @Test
    fun `triggerTransfer calls Stripe and records the transfer when net available is positive`() {
        val account = samplePayoutAccount().copy(payoutsEnabled = true)
        val eligible = earningEntry(LedgerDirection.CREDIT, 9_500L)
        every { membershipService.requireManagerRole(orgId, owner) } returns ownerMembership()
        every { payoutAccountRepository.findByOrganizationId(orgId) } returns account
        every { ledgerService.getTransferableEntries(orgId) } returns listOf(eligible)
        every { stripeConnectClient.createTransfer(account.stripeAccountId, 9_500L, "usd") } returns "tr_test_123"
        every { ledgerService.recordTransfer(orgId, 9_500L, "usd", "tr_test_123", listOf(eligible.id)) } returns eligible
        every { auditService.record(any(), any(), any(), any(), any()) } just runs
        every { ledgerService.getPayoutSummary(orgId) } returns PayoutSummary(0, 0, 0, 0)

        service.triggerTransfer(orgId, owner)

        verify(exactly = 1) { stripeConnectClient.createTransfer(account.stripeAccountId, 9_500L, "usd") }
        verify(exactly = 1) { ledgerService.recordTransfer(orgId, 9_500L, "usd", "tr_test_123", listOf(eligible.id)) }
        verify(
            exactly = 1,
        ) { auditService.record(owner.userId, orgId, "payout.transfer_triggered", "organization_payout_account", account.id) }
    }

    private fun earningEntry(
        direction: LedgerDirection,
        amountMinor: Long,
    ) = LedgerEntry(
        id = UUID.randomUUID(),
        organizationId = orgId,
        accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
        entryType = LedgerEntryType.ORGANIZATION_EARNING,
        direction = direction,
        amountMinor = amountMinor,
        currency = "usd",
        sourceType = LedgerSourceType.CONTRIBUTION,
        sourceId = UUID.randomUUID(),
        externalReference = null,
        description = null,
        includedInTransferEntryId = null,
        effectiveAt = Instant.now(),
        createdAt = Instant.now(),
    )

    private fun samplePayoutAccount() =
        OrganizationPayoutAccount(
            id = UUID.randomUUID(),
            organizationId = orgId,
            stripeAccountId = "acct_123",
            detailsSubmitted = false,
            chargesEnabled = false,
            payoutsEnabled = false,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun ownerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = owner.userId,
            role = MembershipRole.OWNER,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
