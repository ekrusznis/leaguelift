package com.rally26.payout.integration

import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.infra.CheckoutSession
import com.rally26.fundraising.infra.StripeCheckoutClient
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.payout.application.PayoutAccountService
import com.rally26.payout.infra.StripeAccountStatus
import com.rally26.payout.infra.StripeConnectClient
import com.rally26.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the confirm -> transfer -> refund -> negative-balance -> next-transfer-nets-it-out
 * flow (DESIGN-DOC.md section 22.3, ADR-017 negative-balance decision) against real
 * Postgres. Stripe is mocked (@MockkBean) — this is a DB/ledger-math test, not a
 * real-Stripe-API test, same pattern as `PayoutAccountIntegrationTest` and
 * `ContributionIntegrationTest`. The holding period is overridden to 0 days so a
 * confirmed contribution's earning is immediately transfer-eligible within the test,
 * rather than waiting on the real 7-day default.
 */
class PayoutTransferIntegrationTest : AbstractIntegrationTest() {

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun holdingPeriod(registry: DynamicPropertyRegistry) {
			registry.add("rally26.payout.holding-period-days") { "0" }
		}
	}

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var campaignService: CampaignService

	@Autowired
	lateinit var contributionService: ContributionService

	@Autowired
	lateinit var payoutAccountService: PayoutAccountService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@MockkBean
	lateinit var stripeCheckoutClient: StripeCheckoutClient

	@MockkBean
	lateinit var stripeConnectClient: StripeConnectClient

	@Test
	fun `confirm, transfer, refund produces a negative balance that nets against the next transfer`() {
		val owner = registerUser("payout-transfer-owner")
		val organization = organizationService.create(
			"Riverside Soccer", "riverside-soccer-transfer-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		every { stripeConnectClient.createExpressAccount() } returns "acct_test_${System.nanoTime()}"
		every { stripeConnectClient.createOnboardingLink(any(), any(), any()) } returns "https://connect.stripe.com/setup/test"
		payoutAccountService.startOnboarding(organization.id, "https://app.local/refresh", "https://app.local/return", owner)
		every { stripeConnectClient.retrieveAccountStatus(any()) } returns StripeAccountStatus(detailsSubmitted = true, chargesEnabled = true, payoutsEnabled = true)
		payoutAccountService.refreshStatus(organization.id, owner)

		val campaign = campaignService.create(
			organization.id, null, "Spring Trip Fund", "spring-trip-fund-${System.nanoTime()}", null,
			CampaignType.TRAVEL, 100_000L, "USD", null, null, owner,
		)
		campaignService.publish(organization.id, campaign.id, owner)

		// Confirm a 10,000 minor-unit contribution: 5% fee (500) -> 9,500 net earning.
		val firstSessionId = "cs_test_${System.nanoTime()}"
		every { stripeCheckoutClient.createContributionCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			CheckoutSession(firstSessionId, "https://checkout.stripe.com/test")
		contributionService.createCheckoutSession(campaign.slug, 10_000L, "Jane Doe", false, "jane@example.com", "https://app.local/success", "https://app.local/cancel")
		val confirmed = contributionService.confirmFromWebhook(firstSessionId, "paid", "pi_test_${System.nanoTime()}")!!

		val summaryBeforeTransfer = payoutAccountService.getPayoutSummary(organization.id, owner)
		assertEquals(9_500L, summaryBeforeTransfer.eligibleMinor)
		assertEquals(9_500L, summaryBeforeTransfer.netAvailableMinor)

		every { stripeConnectClient.createTransfer(any(), 9_500L, "usd") } returns "tr_test_${System.nanoTime()}"
		val afterFirstTransfer = payoutAccountService.triggerTransfer(organization.id, owner)
		assertEquals(0L, afterFirstTransfer.netAvailableMinor, "the full eligible earning was just transferred out")

		// Refund the contribution: reverses 9,500 of organization earning as a DEBIT,
		// even though it was already transferred out — this is the negative balance.
		every { stripeCheckoutClient.createRefund(confirmed.stripePaymentIntentId!!) } returns "re_test_${System.nanoTime()}"
		contributionService.refund(organization.id, confirmed.id, owner)

		val summaryAfterRefund = payoutAccountService.getPayoutSummary(organization.id, owner)
		assertTrue(summaryAfterRefund.netAvailableMinor < 0, "a refund after transfer must show as a negative balance, not be hidden")
		assertEquals(-9_500L, summaryAfterRefund.netAvailableMinor)

		// A second, larger contribution confirms — its transfer must be net of the carried negative balance.
		val secondSessionId = "cs_test_${System.nanoTime()}"
		every { stripeCheckoutClient.createContributionCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			CheckoutSession(secondSessionId, "https://checkout.stripe.com/test")
		contributionService.createCheckoutSession(campaign.slug, 40_000L, "John Doe", false, "john@example.com", "https://app.local/success", "https://app.local/cancel")
		contributionService.confirmFromWebhook(secondSessionId, "paid", "pi_test_${System.nanoTime()}")

		// 40,000 gross -> 2,000 fee -> 38,000 net earning, minus the 9,500 carried negative balance = 28,500.
		val summaryBeforeSecondTransfer = payoutAccountService.getPayoutSummary(organization.id, owner)
		assertEquals(28_500L, summaryBeforeSecondTransfer.netAvailableMinor)

		every { stripeConnectClient.createTransfer(any(), 28_500L, "usd") } returns "tr_test_${System.nanoTime()}"
		val afterSecondTransfer = payoutAccountService.triggerTransfer(organization.id, owner)
		assertEquals(0L, afterSecondTransfer.netAvailableMinor)
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}
}
