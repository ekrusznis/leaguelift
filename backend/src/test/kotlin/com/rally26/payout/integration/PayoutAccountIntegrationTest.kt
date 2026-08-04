package com.rally26.payout.integration

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.payout.application.PayoutAccountService
import com.rally26.payout.infra.StripeConnectClient
import com.rally26.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises payout-account org isolation (DESIGN-DOC.md section 22.3 critical
 * scenario 1) and owner-only enforcement for Stripe Connect onboarding. StripeConnectClient
 * is mocked (@MockkBean) — this is a DB/authorization test, not a real-Stripe-API test
 * (no test-mode keys are available this session).
 */
class PayoutAccountIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var membershipService: MembershipService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var payoutAccountService: PayoutAccountService

	@MockkBean
	lateinit var stripeConnectClient: StripeConnectClient

	@Test
	fun `an administrator cannot start onboarding, only the owner can, and outsiders are denied everything`() {
		every { stripeConnectClient.createExpressAccount() } returns "acct_test_123"
		every { stripeConnectClient.createOnboardingLink(any(), any(), any()) } returns "https://connect.stripe.com/setup/test"

		val owner = registerUser("payout-owner")
		val organization = organizationService.create(
			"Riverside Soccer", "riverside-soccer-payout-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val administrator = registerUser("payout-admin")
		membershipService.grantMembership(organization.id, administrator.userId, MembershipRole.ADMINISTRATOR)
		val outsider = registerUser("payout-outsider")

		// Administrator (manager role, not owner) is denied.
		assertFailsWith<ForbiddenException> {
			payoutAccountService.startOnboarding(organization.id, "https://app.local/refresh", "https://app.local/return", administrator)
		}

		// Owner succeeds.
		val url = payoutAccountService.startOnboarding(organization.id, "https://app.local/refresh", "https://app.local/return", owner)
		assertEquals("https://connect.stripe.com/setup/test", url)

		// An administrator (still an org member) CAN read status.
		val statusForAdmin = payoutAccountService.getStatus(organization.id, administrator)
		assertEquals("acct_test_123", statusForAdmin?.stripeAccountId)

		// A total outsider is denied both read and write.
		assertFailsWith<ForbiddenException> {
			payoutAccountService.getStatus(organization.id, outsider)
		}
		assertFailsWith<ForbiddenException> {
			payoutAccountService.startOnboarding(organization.id, "https://app.local/refresh", "https://app.local/return", outsider)
		}
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}
}
