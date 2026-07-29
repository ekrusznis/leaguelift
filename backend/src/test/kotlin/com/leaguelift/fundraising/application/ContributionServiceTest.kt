package com.leaguelift.fundraising.application

import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.fundraising.domain.Campaign
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.CampaignType
import com.leaguelift.fundraising.domain.Contribution
import com.leaguelift.fundraising.domain.ContributionStatus
import com.leaguelift.fundraising.infra.CheckoutSession
import com.leaguelift.fundraising.infra.StripeCheckoutClient
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.audit.application.AuditService
import com.stripe.exception.ApiConnectionException
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

class ContributionServiceTest {

	private val contributionRepository = mockk<ContributionRepository>()
	private val campaignRepository = mockk<CampaignRepository>()
	private val stripeCheckoutClient = mockk<StripeCheckoutClient>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val service = ContributionService(contributionRepository, campaignRepository, stripeCheckoutClient, membershipService, auditService)

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
		every { contributionRepository.insertPending(any(), any(), any(), any(), any(), any(), any()) } returns pendingContribution(campaign)
		every { stripeCheckoutClient.createContributionCheckoutSession(any(), any(), any(), any(), any(), any()) } throws
			ApiConnectionException("no network")

		assertFailsWith<ServiceUnavailableException> {
			service.createCheckoutSession("spring-fund", 5000L, "Jane Doe", false, null, "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `confirmFromWebhook is a no-op when Stripe reports the session as unpaid`() {
		val campaign = campaign()
		val contribution = pendingContribution(campaign)
		every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns contribution

		val result = service.confirmFromWebhook("cs_test_123", "unpaid")

		assertEquals(ContributionStatus.PENDING, result?.status)
		verify(exactly = 0) { contributionRepository.markConfirmed(any()) }
	}

	@Test
	fun `confirmFromWebhook confirms a paid session and records an audit event`() {
		val campaign = campaign()
		val contribution = pendingContribution(campaign)
		val confirmed = contribution.copy(status = ContributionStatus.CONFIRMED, confirmedAt = Instant.now())
		every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns contribution
		every { contributionRepository.markConfirmed(contribution.id) } returns 1
		every { contributionRepository.findById(contribution.id) } returns confirmed
		every { auditService.record(null, campaign.organizationId, "contribution.confirmed", "contribution", contribution.id) } just runs

		val result = service.confirmFromWebhook("cs_test_123", "paid")

		assertEquals(ContributionStatus.CONFIRMED, result?.status)
		verify(exactly = 1) { auditService.record(null, campaign.organizationId, "contribution.confirmed", "contribution", contribution.id) }
	}

	@Test
	fun `confirmFromWebhook is idempotent — re-confirming an already-confirmed contribution doesn't re-audit`() {
		val campaign = campaign()
		val confirmed = pendingContribution(campaign).copy(status = ContributionStatus.CONFIRMED, confirmedAt = Instant.now())
		every { contributionRepository.findByStripeCheckoutSessionId("cs_test_123") } returns confirmed
		every { contributionRepository.markConfirmed(confirmed.id) } returns 0 // already CONFIRMED, WHERE status = 'PENDING' matched nothing
		every { contributionRepository.findById(confirmed.id) } returns confirmed

		val result = service.confirmFromWebhook("cs_test_123", "paid")

		assertEquals(ContributionStatus.CONFIRMED, result?.status)
		verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `confirmFromWebhook returns null when no contribution matches the session id`() {
		every { contributionRepository.findByStripeCheckoutSessionId("cs_unknown") } returns null

		val result = service.confirmFromWebhook("cs_unknown", "paid")

		assertEquals(null, result)
	}

	private fun campaign(status: CampaignStatus = CampaignStatus.ACTIVE) = Campaign(
		id = UUID.randomUUID(), organizationId = orgId, teamId = null, name = "Spring Trip Fund",
		slug = "spring-fund", description = null, campaignType = CampaignType.TRAVEL,
		goalAmountMinor = 100_000L, currency = "USD", startDate = null, endDate = null,
		status = status, publishedAt = Instant.now(), createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun pendingContribution(campaign: Campaign) = Contribution(
		id = UUID.randomUUID(), organizationId = campaign.organizationId, campaignId = campaign.id,
		amountMinor = 5000L, currency = "USD", supporterName = null, isAnonymous = false, supporterEmail = null,
		status = ContributionStatus.PENDING, stripeCheckoutSessionId = "cs_test_123", confirmedAt = null, createdAt = Instant.now(),
	)
}
