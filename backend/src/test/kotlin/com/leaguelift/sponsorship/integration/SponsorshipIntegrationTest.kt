package com.leaguelift.sponsorship.integration

import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.application.PasswordAuthenticationService
import com.leaguelift.organization.application.OrganizationService
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.sponsorship.application.SponsorshipPackageService
import com.leaguelift.sponsorship.application.SponsorshipService
import com.leaguelift.sponsorship.infra.SponsorshipCheckoutSession
import com.leaguelift.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.leaguelift.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the real checkout-session -> webhook-confirmation -> ledger flow against
 * Postgres (DESIGN-DOC.md section 22.3 critical scenarios), mirroring
 * `fundraising/integration/ContributionIntegrationTest.kt`. Stripe itself is mocked
 * (@MockkBean) — this is a DB/idempotency/org-isolation test, not a real-Stripe-API
 * test. The webhook HTTP layer (signature verification) is covered separately by
 * `webhook/web/StripeWebhookControllerTest`.
 */
class SponsorshipIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var sponsorshipPackageService: SponsorshipPackageService

	@Autowired
	lateinit var sponsorshipService: SponsorshipService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@MockkBean
	lateinit var stripeSponsorshipCheckoutClient: StripeSponsorshipCheckoutClient

	@Test
	fun `a confirmed sponsorship shows CONFIRMED with the package's confirmed count updated, and a replayed webhook doesn't double-count it`() {
		val fixedSessionId = "cs_test_${System.nanoTime()}"
		every { stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			SponsorshipCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")

		val owner = registerUser("sponsorship-owner")
		val organization = organizationService.create(
			"Riverside Soccer", "riverside-soccer-sponsorship-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val sponsorshipPackage = sponsorshipPackageService.create(
			organization.id, "Gold Sponsor", "Top-tier sponsorship", 50_000L, "USD", 5, false, null, null, owner,
		)
		sponsorshipPackageService.publish(organization.id, sponsorshipPackage.id, owner)

		val checkout = sponsorshipService.createCheckoutSession(
			sponsorshipPackage.id, "Acme Co", "sponsor@acme.test", "https://app.local/success", "https://app.local/cancel",
		)
		assertEquals(0L, sponsorshipService.getConfirmedCount(sponsorshipPackage.id), "not confirmed yet — still PENDING")

		val confirmed = sponsorshipService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")
		assertEquals("CONFIRMED", confirmed?.status?.name)
		assertEquals(1L, sponsorshipService.getConfirmedCount(sponsorshipPackage.id))

		// A replayed webhook for the same session must not double-count the sponsorship.
		val replayed = sponsorshipService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")
		assertEquals("CONFIRMED", replayed?.status?.name)
		assertEquals(1L, sponsorshipService.getConfirmedCount(sponsorshipPackage.id), "replaying confirmation must not double-count")

		val publicPackages = sponsorshipPackageService.listPublic(organization.slug)
		assertEquals(sponsorshipPackage.id, publicPackages.single().id)

		val directory = sponsorshipService.listPublicDirectory(organization.slug)
		assertEquals(1, directory.size)
		assertEquals("Acme Co", directory.single().sponsorName)
		assertEquals(checkout.sponsorId, directory.single().sponsorId)
	}

	@Test
	fun `a sold-out package rejects a further checkout attempt`() {
		val fixedSessionId = "cs_test_${System.nanoTime()}"
		every { stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			SponsorshipCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")

		val owner = registerUser("sponsorship-soldout-owner")
		val organization = organizationService.create(
			"Lakeside Hockey", "lakeside-hockey-sponsorship-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val exclusivePackage = sponsorshipPackageService.create(
			organization.id, "Title Sponsor", null, 100_000L, "USD", null, true, null, null, owner,
		)
		sponsorshipPackageService.publish(organization.id, exclusivePackage.id, owner)

		sponsorshipService.createCheckoutSession(exclusivePackage.id, "First Sponsor", null, "https://app.local/success", "https://app.local/cancel")
		sponsorshipService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")

		assertFailsWith<com.leaguelift.common.error.ValidationException> {
			sponsorshipService.createCheckoutSession(exclusivePackage.id, "Second Sponsor", null, "https://app.local/success", "https://app.local/cancel")
		}
	}

	@Test
	fun `a second organization cannot see or manage the first organization's sponsorship packages`() {
		val ownerA = registerUser("sponsorship-org-a-owner")
		val organizationA = organizationService.create(
			"Org A", "org-a-sponsorship-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, ownerA,
		)
		val packageA = sponsorshipPackageService.create(
			organizationA.id, "Org A Gold Sponsor", null, 25_000L, "USD", null, false, null, null, ownerA,
		)

		val ownerB = registerUser("sponsorship-org-b-owner")
		val organizationB = organizationService.create(
			"Org B", "org-b-sponsorship-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, ownerB,
		)

		// Org B's owner has no membership in org A at all — reading org A's package
		// through org A's id is denied outright.
		assertFailsWith<ForbiddenException> {
			sponsorshipPackageService.get(organizationA.id, packageA.id, ownerB)
		}

		// Even scoped under org B's own id (the id org B's owner *is* a manager of),
		// org A's package doesn't exist for org B — every repository query is
		// organization-scoped, so a cross-org id substitution finds nothing rather
		// than leaking org A's row.
		assertFailsWith<NotFoundException> {
			sponsorshipPackageService.get(organizationB.id, packageA.id, ownerB)
		}
		assertFailsWith<NotFoundException> {
			sponsorshipPackageService.update(organizationB.id, packageA.id, "Hijacked name", null, null, null, null, null, null, ownerB)
		}

		// Org A's own owner can still see it fine — isolation isn't a general lockout.
		val stillVisible = sponsorshipPackageService.get(organizationA.id, packageA.id, ownerA)
		assertTrue(stillVisible.name == "Org A Gold Sponsor")
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}
}
