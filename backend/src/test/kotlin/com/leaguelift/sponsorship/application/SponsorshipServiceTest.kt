package com.leaguelift.sponsorship.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.ledger.application.LedgerService
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.sponsorship.domain.Sponsor
import com.leaguelift.sponsorship.domain.Sponsorship
import com.leaguelift.sponsorship.domain.SponsorshipPackage
import com.leaguelift.sponsorship.domain.SponsorshipPackageStatus
import com.leaguelift.sponsorship.domain.SponsorshipStatus
import com.leaguelift.sponsorship.infra.SponsorshipCheckoutSession
import com.leaguelift.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.leaguelift.sponsorship.persistence.SponsorRepository
import com.leaguelift.sponsorship.persistence.SponsorshipPackageRepository
import com.leaguelift.sponsorship.persistence.SponsorshipRepository
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

class SponsorshipServiceTest {

	private val sponsorshipRepository = mockk<SponsorshipRepository>()
	private val sponsorshipPackageRepository = mockk<SponsorshipPackageRepository>()
	private val sponsorRepository = mockk<SponsorRepository>()
	private val organizationRepository = mockk<OrganizationRepository>()
	private val stripeSponsorshipCheckoutClient = mockk<StripeSponsorshipCheckoutClient>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val ledgerService = mockk<LedgerService>()
	private val mediaAssignmentService = mockk<MediaAssignmentService>()
	private val mediaReadService = mockk<MediaReadService>()
	private val service = SponsorshipService(
		sponsorshipRepository, sponsorshipPackageRepository, sponsorRepository, organizationRepository,
		stripeSponsorshipCheckoutClient, membershipService, auditService, ledgerService, mediaAssignmentService, mediaReadService,
	)

	private val orgId = UUID.randomUUID()

	@Test
	fun `createCheckoutSession rejects a package that isn't PUBLISHED`() {
		every { sponsorshipPackageRepository.findById(any<UUID>()) } returns sponsorshipPackage(status = SponsorshipPackageStatus.DRAFT)

		assertFailsWith<ValidationException> {
			service.createCheckoutSession(UUID.randomUUID(), "Acme Co", "sponsor@acme.test", "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession throws NotFoundException for an unknown package`() {
		every { sponsorshipPackageRepository.findById(any<UUID>()) } returns null

		assertFailsWith<NotFoundException> {
			service.createCheckoutSession(UUID.randomUUID(), "Acme Co", "sponsor@acme.test", "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession rejects a sold-out package`() {
		val pkg = sponsorshipPackage(maxQuantity = 1)
		every { sponsorshipPackageRepository.findById(pkg.id) } returns pkg
		every { sponsorshipRepository.countConfirmedForPackage(pkg.id) } returns 1L

		assertFailsWith<ValidationException> {
			service.createCheckoutSession(pkg.id, "Acme Co", "sponsor@acme.test", "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession rejects a sold-out exclusive package even with maxQuantity unset`() {
		val pkg = sponsorshipPackage(maxQuantity = null).copy(exclusive = true)
		every { sponsorshipPackageRepository.findById(pkg.id) } returns pkg
		every { sponsorshipRepository.countConfirmedForPackage(pkg.id) } returns 1L

		assertFailsWith<ValidationException> {
			service.createCheckoutSession(pkg.id, "Acme Co", "sponsor@acme.test", "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `createCheckoutSession inserts a sponsor and a pending sponsorship, then attaches the Stripe session`() {
		val pkg = sponsorshipPackage(maxQuantity = 5)
		val sponsor = sampleSponsor(pkg.organizationId)
		every { sponsorshipPackageRepository.findById(pkg.id) } returns pkg
		every { sponsorshipRepository.countConfirmedForPackage(pkg.id) } returns 0L
		every { sponsorRepository.insert(pkg.organizationId, "Acme Co", "sponsor@acme.test") } returns sponsor
		every {
			sponsorshipRepository.insertPending(pkg.organizationId, pkg.id, sponsor.id, pkg.priceMinor, pkg.currency)
		} returns pendingSponsorship(pkg, sponsor)
		every {
			stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(any(), pkg.priceMinor, pkg.currency, pkg.name, any(), any())
		} returns SponsorshipCheckoutSession("cs_test_123", "https://checkout.stripe.com/cs_test_123")
		every { sponsorshipRepository.attachStripeSession(any(), "cs_test_123") } returns 1

		val result = service.createCheckoutSession(pkg.id, "Acme Co", "sponsor@acme.test", "https://x/success/{SPONSORSHIP_ID}", "https://x/cancel")

		assertEquals("https://checkout.stripe.com/cs_test_123", result.checkoutUrl)
		assertEquals(sponsor.id, result.sponsorId)
	}

	@Test
	fun `createCheckoutSession translates a Stripe failure into ServiceUnavailableException`() {
		val pkg = sponsorshipPackage(maxQuantity = 5)
		val sponsor = sampleSponsor(pkg.organizationId)
		every { sponsorshipPackageRepository.findById(pkg.id) } returns pkg
		every { sponsorshipRepository.countConfirmedForPackage(pkg.id) } returns 0L
		every { sponsorRepository.insert(any(), any(), any()) } returns sponsor
		every { sponsorshipRepository.insertPending(any(), any(), any(), any(), any()) } returns pendingSponsorship(pkg, sponsor)
		every { stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(any(), any(), any(), any(), any(), any()) } throws
			ApiConnectionException("no network")

		assertFailsWith<ServiceUnavailableException> {
			service.createCheckoutSession(pkg.id, "Acme Co", "sponsor@acme.test", "https://x/success", "https://x/cancel")
		}
	}

	@Test
	fun `confirmFromWebhook is a no-op when Stripe reports the session as unpaid`() {
		val pkg = sponsorshipPackage()
		val sponsor = sampleSponsor(pkg.organizationId)
		val sponsorship = pendingSponsorship(pkg, sponsor)
		every { sponsorshipRepository.findByStripeCheckoutSessionId("cs_test_123") } returns sponsorship

		val result = service.confirmFromWebhook("cs_test_123", "unpaid", "pi_test_123")

		assertEquals(SponsorshipStatus.PENDING, result?.status)
		verify(exactly = 0) { sponsorshipRepository.markConfirmed(any(), any()) }
	}

	@Test
	fun `confirmFromWebhook confirms a paid session, records audit, and calls the ledger`() {
		val pkg = sponsorshipPackage()
		val sponsor = sampleSponsor(pkg.organizationId)
		val sponsorship = pendingSponsorship(pkg, sponsor)
		val confirmed = sponsorship.copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = Instant.now())
		every { sponsorshipRepository.findByStripeCheckoutSessionId("cs_test_123") } returns sponsorship
		every { sponsorshipRepository.markConfirmed(sponsorship.id, "pi_test_123") } returns 1
		every { sponsorshipRepository.findById(sponsorship.id) } returns confirmed
		every { auditService.record(null, pkg.organizationId, "sponsorship.confirmed", "sponsorship", sponsorship.id) } just runs
		every { ledgerService.recordConfirmedSponsorship(any()) } just runs

		val result = service.confirmFromWebhook("cs_test_123", "paid", "pi_test_123")

		assertEquals(SponsorshipStatus.CONFIRMED, result?.status)
		verify(exactly = 1) { auditService.record(null, pkg.organizationId, "sponsorship.confirmed", "sponsorship", sponsorship.id) }
		verify(exactly = 1) { ledgerService.recordConfirmedSponsorship(any()) }
	}

	@Test
	fun `confirmFromWebhook is idempotent — re-confirming an already-confirmed sponsorship doesn't re-audit or re-ledger`() {
		val pkg = sponsorshipPackage()
		val sponsor = sampleSponsor(pkg.organizationId)
		val confirmed = pendingSponsorship(pkg, sponsor).copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = Instant.now())
		every { sponsorshipRepository.findByStripeCheckoutSessionId("cs_test_123") } returns confirmed
		every { sponsorshipRepository.markConfirmed(confirmed.id, "pi_test_123") } returns 0 // already CONFIRMED, WHERE status = 'PENDING' matched nothing
		every { sponsorshipRepository.findById(confirmed.id) } returns confirmed

		val result = service.confirmFromWebhook("cs_test_123", "paid", "pi_test_123")

		assertEquals(SponsorshipStatus.CONFIRMED, result?.status)
		verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any()) }
		verify(exactly = 0) { ledgerService.recordConfirmedSponsorship(any()) }
	}

	@Test
	fun `confirmFromWebhook returns null when no sponsorship matches the session id`() {
		every { sponsorshipRepository.findByStripeCheckoutSessionId("cs_unknown") } returns null

		val result = service.confirmFromWebhook("cs_unknown", "paid", "pi_test_123")

		assertEquals(null, result)
	}

	private fun sponsorshipPackage(status: SponsorshipPackageStatus = SponsorshipPackageStatus.PUBLISHED, maxQuantity: Int? = null) = SponsorshipPackage(
		id = UUID.randomUUID(), organizationId = orgId, name = "Gold Sponsor", description = null,
		priceMinor = 50_000L, currency = "USD", maxQuantity = maxQuantity, exclusive = false,
		placementStartDate = null, placementEndDate = null, status = status,
		createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun sampleSponsor(organizationId: UUID) = Sponsor(
		id = UUID.randomUUID(), organizationId = organizationId, name = "Acme Co", contactEmail = "sponsor@acme.test",
		createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun pendingSponsorship(pkg: SponsorshipPackage, sponsor: Sponsor) = Sponsorship(
		id = UUID.randomUUID(), organizationId = pkg.organizationId, packageId = pkg.id, sponsorId = sponsor.id,
		amountMinor = pkg.priceMinor, currency = pkg.currency, status = SponsorshipStatus.PENDING,
		stripeCheckoutSessionId = "cs_test_123", stripePaymentIntentId = null, confirmedAt = null, createdAt = Instant.now(),
	)
}
