package com.rally26.sponsorship.integration

import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.sponsorship.application.SponsorshipPackageService
import com.rally26.sponsorship.application.SponsorshipService
import com.rally26.sponsorship.infra.SponsorshipCheckoutSession
import com.rally26.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.rally26.testsupport.AbstractIntegrationTest
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

		// A newly confirmed sponsorship is NOT yet on the public directory — it's
		// awaiting org-admin review (Phase 6 remainder approval workflow, ADR-019).
		assertEquals(0, sponsorshipService.listPublicDirectory(organization.slug).size)
		assertEquals(1L, sponsorshipService.countPendingReview(organization.id, owner))

		sponsorshipService.approve(organization.id, confirmed!!.id, owner)

		val directory = sponsorshipService.listPublicDirectory(organization.slug)
		assertEquals(1, directory.size)
		assertEquals("Acme Co", directory.single().sponsorName)
		assertEquals(checkout.sponsorId, directory.single().sponsorId)
		assertEquals(0L, sponsorshipService.countPendingReview(organization.id, owner))
	}

	@Test
	fun `rejecting a confirmed sponsorship atomically refunds it via Stripe and the ledger, and it never reaches the public directory`() {
		val fixedSessionId = "cs_test_${System.nanoTime()}"
		every { stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			SponsorshipCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")
		every { stripeSponsorshipCheckoutClient.createRefund(any()) } returns "re_test_${System.nanoTime()}"

		val owner = registerUser("sponsorship-reject-owner")
		val organization = organizationService.create(
			"Eastside Baseball", "eastside-baseball-sponsorship-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val sponsorshipPackage = sponsorshipPackageService.create(
			organization.id, "Bronze Sponsor", null, 10_000L, "USD", null, false, null, null, owner,
		)
		sponsorshipPackageService.publish(organization.id, sponsorshipPackage.id, owner)
		sponsorshipService.createCheckoutSession(sponsorshipPackage.id, "Shady Co", "shady@example.test", "https://app.local/success", "https://app.local/cancel")
		val confirmed = sponsorshipService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")!!

		val rejected = sponsorshipService.reject(organization.id, confirmed.id, owner)

		assertEquals("REJECTED", rejected.reviewStatus.name)
		assertEquals("REFUNDED", rejected.status.name)
		assertEquals(0, sponsorshipService.listPublicDirectory(organization.slug).size)

		// A rejected (and thus refunded) sponsorship cannot be reviewed again.
		assertFailsWith<com.rally26.common.error.ValidationException> {
			sponsorshipService.approve(organization.id, confirmed.id, owner)
		}
	}

	@Test
	fun `a general refund of an approved sponsorship removes it from the public directory's future listings but preserves its own record`() {
		val fixedSessionId = "cs_test_${System.nanoTime()}"
		every { stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
			SponsorshipCheckoutSession(fixedSessionId, "https://checkout.stripe.com/test")
		every { stripeSponsorshipCheckoutClient.createRefund(any()) } returns "re_test_${System.nanoTime()}"

		val owner = registerUser("sponsorship-refund-owner")
		val organization = organizationService.create(
			"Northshore Swim", "northshore-swim-sponsorship-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner,
		)
		val sponsorshipPackage = sponsorshipPackageService.create(
			organization.id, "Silver Sponsor", null, 20_000L, "USD", null, false, null, null, owner,
		)
		sponsorshipPackageService.publish(organization.id, sponsorshipPackage.id, owner)
		sponsorshipService.createCheckoutSession(sponsorshipPackage.id, "Good Co", "good@example.test", "https://app.local/success", "https://app.local/cancel")
		val confirmed = sponsorshipService.confirmFromWebhook(fixedSessionId, "paid", "pi_test_${System.nanoTime()}")!!
		sponsorshipService.approve(organization.id, confirmed.id, owner)
		assertEquals(1, sponsorshipService.listPublicDirectory(organization.slug).size)

		val invoice = sponsorshipService.getInvoice(organization.id, confirmed.id, owner)
		assertEquals("Good Co", invoice.sponsor.name)
		assertEquals(sponsorshipPackage.name, invoice.sponsorshipPackage.name)

		val refunded = sponsorshipService.refund(organization.id, confirmed.id, owner)

		assertEquals("REFUNDED", refunded.status.name)
		assertEquals("APPROVED", refunded.reviewStatus.name, "a refund does not itself revoke a prior approval decision")
		assertEquals(0, sponsorshipService.listPublicDirectory(organization.slug).size, "a refunded sponsorship is no longer CONFIRMED, so it drops off the directory")
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

		assertFailsWith<com.rally26.common.error.ValidationException> {
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
