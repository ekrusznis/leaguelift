package com.rally26.fundraising.integration

import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.infra.CheckoutSession
import com.rally26.fundraising.infra.StripeCheckoutClient
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Exercises the real checkout-session -> webhook-confirmation -> real-raised-total
 * flow against Postgres (DESIGN-DOC.md section 22.3 critical scenarios). Stripe
 * itself is mocked (@MockkBean) — same pattern as PayoutAccountIntegrationTest —
 * this is a DB/idempotency test, not a real-Stripe-API test. The webhook HTTP
 * layer (signature verification) is covered separately by
 * `webhook/web/StripeWebhookControllerTest`; here we call
 * [ContributionService.confirmFromWebhook] directly, which is exactly what that
 * controller does once a signature is verified.
 */
class ContributionIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var campaignService: CampaignService

	@Autowired
	lateinit var contributionService: ContributionService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@MockkBean
	lateinit var stripeCheckoutClient: StripeCheckoutClient

	@Test
	fun `a confirmed contribution raises the campaign total, and a replayed webhook doesn't double-count it`() {
		val fixedSessionId = "cs_test_${System.nanoTime()}"
		every { stripeCheckoutClient.createContributionCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			CheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")

		val owner = registerUser("contribution-owner")
		val organization = organizationService.create(
			"Riverside Soccer", "riverside-soccer-contribution-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val campaign = campaignService.create(
			organization.id, null, "Spring Trip Fund", "spring-trip-fund-${System.nanoTime()}", null,
			CampaignType.TRAVEL, 100_000L, "USD", null, null, owner,
		)
		campaignService.publish(organization.id, campaign.id, owner)

		contributionService.createCheckoutSession(
			campaign.slug, 5_000L, "Jane Doe", false, "jane@example.com", "https://app.local/success", "https://app.local/cancel",
		)
		assertEquals(0L, contributionService.getConfirmedTotal(campaign.id), "not confirmed yet — still PENDING")

		val confirmed = contributionService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")
		assertEquals("CONFIRMED", confirmed?.status?.name)
		assertEquals(5_000L, contributionService.getConfirmedTotal(campaign.id))

		// A replayed webhook for the same session must not double-count the contribution.
		val replayed = contributionService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")
		assertEquals("CONFIRMED", replayed?.status?.name)
		assertEquals(5_000L, contributionService.getConfirmedTotal(campaign.id), "replaying confirmation must not double-count")

		val publicCampaign = campaignService.getPublic(campaign.slug)
		assertEquals(campaign.id, publicCampaign.id)
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}
}
