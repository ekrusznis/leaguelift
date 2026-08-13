package com.rally26.fundraising.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.boxpool.persistence.BoxPoolBoxRepository
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.application.FamilyCreditService
import com.rally26.credit.application.HouseholdAttributionService
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.fundraising.infra.CheckoutSession
import com.rally26.fundraising.infra.StripeCheckoutClient
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
import com.stripe.exception.ApiConnectionException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContributionServiceTest {
    private val contributionRepository = mockk<ContributionRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val stripeCheckoutClient = mockk<StripeCheckoutClient>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val ledgerService = mockk<LedgerService>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val householdAttributionService = mockk<HouseholdAttributionService>()
    private val familyCreditService = mockk<FamilyCreditService>()
    private val boxPoolBoxRepository = mockk<BoxPoolBoxRepository>(relaxed = true)
    private val service =
        ContributionService(
            contributionRepository,
            campaignRepository,
            stripeCheckoutClient,
            membershipService,
            auditService,
            ledgerService,
            outboxWriter,
            ObjectMapper(),
            householdAttributionService,
            familyCreditService,
            boxPoolBoxRepository,
        )

    private val orgId = UUID.randomUUID()

    @Test
    fun `createCheckoutSession rejects a campaign that isn't ACTIVE`() {
        every { campaignRepository.findBySlug("draft-fund") } returns campaign(status = CampaignStatus.DRAFT)

        assertFailsWith<ValidationException> {
            service.createCheckoutSession("draft-fund", 5000L, "Jane Doe", false, null, "https://x/success", "https://x/cancel")
        }
    }

    @Test
    fun `createCheckoutSession rejects an amount below the minimum`() {
        every { campaignRepository.findBySlug("spring-fund") } returns campaign()

        assertFailsWith<ValidationException> {
            service.createCheckoutSession("spring-fund", 50L, "Jane Doe", false, null, "https://x/success", "https://x/cancel")
        }
    }

    @Test
    fun `createCheckoutSession rejects an amount above the maximum`() {
        every { campaignRepository.findBySlug("spring-fund") } returns campaign()

        assertFailsWith<ValidationException> {
            service.createCheckoutSession("spring-fund", 10_000_000L, "Jane Doe", false, null, "https://x/success", "https://x/cancel")
        }
    }

    @Test
    fun `createCheckoutSession throws NotFoundException for an unknown slug`() {
        every { campaignRepository.findBySlug("nope") } returns null

        assertFailsWith<NotFoundException> {
            service.createCheckoutSession("nope", 5000L, "Jane Doe", false, null, "https://x/success", "https://x/cancel")
        }
    }

    @Test
    fun `createCheckoutSession drops the supporter name when anonymous`() {
        val campaign = campaign()
        every { campaignRepository.findBySlug("spring-fund") } returns campaign
        every {
            contributionRepository.insertPending(campaign.organizationId, campaign.id, 5000L, "USD", null, true, null)
        } returns pendingContribution(campaign)
        every { stripeCheckoutClient.createContributionCheckoutSession(any(), 5000L, "USD", campaign.name, any(), any()) } returns
            CheckoutSession("cs_test_123", "https://checkout.stripe.com/cs_test_123")
        every { contributionRepository.attachStripeSession(any(), "cs_test_123") } returns 1

        val result = service.createCheckoutSession("spring-fund", 5000L, "Jane Doe", true, null, "https://x/success", "https://x/cancel")

        assertEquals("https://checkout.stripe.com/cs_test_123", result.checkoutUrl)
        verify(exactly = 1) { contributionRepository.insertPending(campaign.organizationId, campaign.id, 5000L, "USD", null, true, null) }
    }

    @Test
    fun `createCheckoutSession translates a Stripe failure into ServiceUnavailableException`() {
        val campaign = campaign()
        every { campaignRepository.findBySlug("spring-fund") } returns campaign
        every { contributionRepository.insertPending(any(), any(), any(), any(), any(), any(), any()) } returns
            pendingContribution(campaign)
        every { stripeCheckoutClient.createContributionCheckoutSession(any(), any(), any(), any(), any(), any()) } throws
            ApiConnectionException("no network")

        assertFailsWith<ServiceUnavailableException> {
            service.createCheckoutSession("spring-fund", 5000L, "Jane Doe", false, null, "https://x/success", "https://x/cancel")
        }
    }

    @Test
    fun `createCheckoutSession resolves an attribution code and stores it on the contribution`() {
        val campaign = campaign()
        val attributedHouseholdId = UUID.randomUUID()
        every { campaignRepository.findBySlug("spring-fund") } returns campaign
        every { householdAttributionService.resolveByCode(campaign.organizationId, campaign.id, "ABC12345") } returns attributedHouseholdId
        every {
            contributionRepository.insertPending(
                campaign.organizationId,
                campaign.id,
                5000L,
                "USD",
                "Jane Doe",
                false,
                null,
                attributedHouseholdId,
            )
        } returns pendingContribution(campaign).copy(attributedHouseholdId = attributedHouseholdId)
        every { stripeCheckoutClient.createContributionCheckoutSession(any(), 5000L, "USD", campaign.name, any(), any()) } returns
            CheckoutSession("cs_test_123", "https://checkout.stripe.com/cs_test_123")
        every { contributionRepository.attachStripeSession(any(), "cs_test_123") } returns 1

        service.createCheckoutSession("spring-fund", 5000L, "Jane Doe", false, null, "https://x/success", "https://x/cancel", "ABC12345")

        verify(exactly = 1) {
            contributionRepository.insertPending(
                campaign.organizationId,
                campaign.id,
                5000L,
                "USD",
                "Jane Doe",
                false,
                null,
                attributedHouseholdId,
            )
        }
    }

    @Test
    fun `confirmFromWebhook grants family credit when the contribution has an attributed household`() {
        val campaign = campaign()
        val attributedHouseholdId = UUID.randomUUID()
        val contribution = pendingContribution(campaign).copy(attributedHouseholdId = attributedHouseholdId)
        val confirmed = contribution.copy(status = ContributionStatus.CONFIRMED, confirmedAt = Instant.now())
        every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns contribution
        every { contributionRepository.markConfirmed(contribution.id, "pi_test_123") } returns 1
        every { contributionRepository.findById(contribution.id) } returns confirmed
        every { auditService.record(null, campaign.organizationId, "contribution.confirmed", "contribution", contribution.id) } just runs
        every { ledgerService.recordConfirmedContribution(any()) } just runs
        every { ledgerService.recordStripeProcessingFee(any(), any(), any(), any(), any()) } just runs
        every {
            familyCreditService.grantForContribution(campaign.organizationId, attributedHouseholdId, contribution.id, 5000L, "USD")
        } returns mockk()

        service.confirmFromWebhook("cs_test_123", "paid", "pi_test_123")

        verify(exactly = 1) {
            familyCreditService.grantForContribution(campaign.organizationId, attributedHouseholdId, contribution.id, 5000L, "USD")
        }
    }

    @Test
    fun `confirmFromWebhook is a no-op when Stripe reports the session as unpaid`() {
        val campaign = campaign()
        val contribution = pendingContribution(campaign)
        every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns contribution

        val result = service.confirmFromWebhook("cs_test_123", "unpaid", "pi_test_123")

        assertEquals(ContributionStatus.PENDING, result?.status)
        verify(exactly = 0) { contributionRepository.markConfirmed(any(), any()) }
    }

    @Test
    fun `confirmFromWebhook confirms a paid session and records an audit event`() {
        val campaign = campaign()
        val contribution = pendingContribution(campaign)
        val confirmed = contribution.copy(status = ContributionStatus.CONFIRMED, confirmedAt = Instant.now())
        every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns contribution
        every { contributionRepository.markConfirmed(contribution.id, "pi_test_123") } returns 1
        every { contributionRepository.findById(contribution.id) } returns confirmed
        every { auditService.record(null, campaign.organizationId, "contribution.confirmed", "contribution", contribution.id) } just runs
        every { ledgerService.recordConfirmedContribution(any()) } just runs
        every { ledgerService.recordStripeProcessingFee(any(), any(), any(), any(), any()) } just runs

        val result = service.confirmFromWebhook("cs_test_123", "paid", "pi_test_123")

        assertEquals(ContributionStatus.CONFIRMED, result?.status)
        verify(
            exactly = 1,
        ) { auditService.record(null, campaign.organizationId, "contribution.confirmed", "contribution", contribution.id) }
        verify(exactly = 1) { ledgerService.recordConfirmedContribution(any()) }
        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) } // no supporterEmail on this fixture contribution
    }

    @Test
    fun `confirmFromWebhook writes a contribution_confirmed outbox event when the contribution has a supporter email`() {
        val campaign = campaign()
        val contribution = pendingContribution(campaign).copy(supporterEmail = "supporter@example.com", supporterName = "Jane Doe")
        val confirmed = contribution.copy(status = ContributionStatus.CONFIRMED, confirmedAt = Instant.now())
        every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns contribution
        every { contributionRepository.markConfirmed(contribution.id, "pi_test_123") } returns 1
        every { contributionRepository.findById(contribution.id) } returns confirmed
        every { auditService.record(null, campaign.organizationId, "contribution.confirmed", "contribution", contribution.id) } just runs
        every { ledgerService.recordConfirmedContribution(any()) } just runs
        every { ledgerService.recordStripeProcessingFee(any(), any(), any(), any(), any()) } just runs
        every { campaignRepository.findById(campaign.id, campaign.organizationId) } returns campaign
        val payloadSlot = slot<String>()
        every {
            outboxWriter.write(
                aggregateType = "contribution",
                aggregateId = contribution.id,
                organizationId = campaign.organizationId,
                eventType = "contribution.confirmed",
                payloadJson = capture(payloadSlot),
            )
        } just runs

        service.confirmFromWebhook("cs_test_123", "paid", "pi_test_123")

        verify(exactly = 1) { outboxWriter.write(any(), any(), any(), any(), any()) }
        assertEquals(true, payloadSlot.captured.contains("supporter@example.com"))
    }

    @Test
    fun `confirmFromWebhook is idempotent — re-confirming an already-confirmed contribution doesn't re-audit`() {
        val campaign = campaign()
        val confirmed = pendingContribution(campaign).copy(status = ContributionStatus.CONFIRMED, confirmedAt = Instant.now())
        every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns confirmed
        every {
            contributionRepository.markConfirmed(confirmed.id, "pi_test_123")
        } returns 0 // already CONFIRMED, WHERE status = 'PENDING' matched nothing
        every { contributionRepository.findById(confirmed.id) } returns confirmed

        val result = service.confirmFromWebhook("cs_test_123", "paid", "pi_test_123")

        assertEquals(ContributionStatus.CONFIRMED, result?.status)
        verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { ledgerService.recordConfirmedContribution(any()) }
    }

    @Test
    fun `confirmFromWebhook returns null when no contribution matches the session id`() {
        every { contributionRepository.findByStripeCheckoutSessionId("cs_unknown") } returns null

        val result = service.confirmFromWebhook("cs_unknown", "paid", "pi_test_123")

        assertEquals(null, result)
    }

    @Test
    fun `refund calls Stripe, marks REFUNDED, and records a ledger reversal`() {
        val campaign = campaign()
        val confirmed =
            pendingContribution(
                campaign,
            ).copy(status = ContributionStatus.CONFIRMED, stripePaymentIntentId = "pi_test_123", confirmedAt = Instant.now())
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { contributionRepository.findById(confirmed.id) } returns confirmed
        every { stripeCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
        every { contributionRepository.markRefunded(confirmed.id) } returns 1
        every {
            ledgerService.recordRefund(
                orgId,
                LedgerSourceType.CONTRIBUTION,
                confirmed.id,
                confirmed.amountMinor,
                confirmed.currency,
                "re_test_123",
            )
        } just runs
        every { auditService.record(manager.userId, orgId, "contribution.refunded", "contribution", confirmed.id) } just runs

        service.refund(orgId, confirmed.id, manager)

        verify(exactly = 1) { stripeCheckoutClient.createRefund("pi_test_123") }
        verify(exactly = 1) {
            ledgerService.recordRefund(
                orgId,
                LedgerSourceType.CONTRIBUTION,
                confirmed.id,
                confirmed.amountMinor,
                confirmed.currency,
                "re_test_123",
            )
        }
    }

    @Test
    fun `refund reverses remaining family credit when the contribution was attributed to a household`() {
        val campaign = campaign()
        val attributedHouseholdId = UUID.randomUUID()
        val confirmed =
            pendingContribution(campaign)
                .copy(
                    status = ContributionStatus.CONFIRMED,
                    stripePaymentIntentId = "pi_test_123",
                    confirmedAt = Instant.now(),
                    attributedHouseholdId = attributedHouseholdId,
                )
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { contributionRepository.findById(confirmed.id) } returns confirmed
        every { stripeCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
        every { contributionRepository.markRefunded(confirmed.id) } returns 1
        every {
            ledgerService.recordRefund(
                orgId,
                LedgerSourceType.CONTRIBUTION,
                confirmed.id,
                confirmed.amountMinor,
                confirmed.currency,
                "re_test_123",
            )
        } just runs
        every { auditService.record(manager.userId, orgId, "contribution.refunded", "contribution", confirmed.id) } just runs
        every { familyCreditService.reverseForRefundedContribution(orgId, confirmed.id) } just runs

        service.refund(orgId, confirmed.id, manager)

        verify(exactly = 1) { familyCreditService.reverseForRefundedContribution(orgId, confirmed.id) }
    }

    @Test
    fun `refund rejects a contribution that was never confirmed`() {
        val campaign = campaign()
        val pending = pendingContribution(campaign)
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { contributionRepository.findById(pending.id) } returns pending

        assertFailsWith<ValidationException> {
            service.refund(orgId, pending.id, manager)
        }
        verify(exactly = 0) { stripeCheckoutClient.createRefund(any()) }
    }

    @Test
    fun `refund rejects a contribution confirmed more than 14 days ago`() {
        val campaign = campaign()
        val stale =
            pendingContribution(campaign).copy(
                status = ContributionStatus.CONFIRMED,
                stripePaymentIntentId = "pi_test_123",
                confirmedAt = Instant.now().minus(Duration.ofDays(15)),
            )
        val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
        every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership(manager)
        every { contributionRepository.findById(stale.id) } returns stale

        assertFailsWith<ValidationException> {
            service.refund(orgId, stale.id, manager)
        }
        verify(exactly = 0) { stripeCheckoutClient.createRefund(any()) }
    }

    private fun managerMembership(manager: CurrentUser) =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = manager.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun campaign(status: CampaignStatus = CampaignStatus.ACTIVE) =
        Campaign(
            id = UUID.randomUUID(),
            organizationId = orgId,
            teamId = null,
            name = "Spring Trip Fund",
            slug = "spring-fund",
            description = null,
            campaignType = CampaignType.TRAVEL,
            goalAmountMinor = 100_000L,
            currency = "USD",
            startDate = null,
            endDate = null,
            status = status,
            publishedAt = Instant.now(),
            createdByUserId = null,
            templateKey = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun pendingContribution(campaign: Campaign) =
        Contribution(
            id = UUID.randomUUID(),
            organizationId = campaign.organizationId,
            campaignId = campaign.id,
            amountMinor = 5000L,
            currency = "USD",
            supporterName = null,
            isAnonymous = false,
            supporterEmail = null,
            status = ContributionStatus.PENDING,
            stripeCheckoutSessionId = "cs_test_123",
            stripePaymentIntentId = null,
            confirmedAt = null,
            refundedAt = null,
            createdAt = Instant.now(),
        )
}
