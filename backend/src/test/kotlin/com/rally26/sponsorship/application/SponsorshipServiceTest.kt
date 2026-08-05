package com.rally26.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.sponsorship.domain.Sponsor
import com.rally26.sponsorship.domain.Sponsorship
import com.rally26.sponsorship.domain.SponsorshipPackage
import com.rally26.sponsorship.domain.SponsorshipPackageStatus
import com.rally26.sponsorship.domain.SponsorshipReviewStatus
import com.rally26.sponsorship.domain.SponsorshipStatus
import com.rally26.sponsorship.infra.SponsorshipCheckoutSession
import com.rally26.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.rally26.sponsorship.persistence.SponsorRepository
import com.rally26.sponsorship.persistence.SponsorshipPackageRepository
import com.rally26.sponsorship.persistence.SponsorshipRepository
import com.stripe.exception.ApiConnectionException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Duration
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
    private val outboxWriter = mockk<OutboxWriter>()
    private val service =
        SponsorshipService(
            sponsorshipRepository,
            sponsorshipPackageRepository,
            sponsorRepository,
            organizationRepository,
            stripeSponsorshipCheckoutClient,
            membershipService,
            auditService,
            ledgerService,
            mediaAssignmentService,
            mediaReadService,
            outboxWriter,
            ObjectMapper(),
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

        val result =
            service.createCheckoutSession(
                pkg.id,
                "Acme Co",
                "sponsor@acme.test",
                "https://x/success/{SPONSORSHIP_ID}",
                "https://x/cancel",
            )

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
        every {
            sponsorshipRepository.markConfirmed(confirmed.id, "pi_test_123")
        } returns 0 // already CONFIRMED, WHERE status = 'PENDING' matched nothing
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

    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `approve requires manager role, moves PENDING_REVIEW to APPROVED, and records audit`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val confirmed = pendingSponsorship(pkg, sponsor).copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = Instant.now())
        val approved = confirmed.copy(reviewStatus = SponsorshipReviewStatus.APPROVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(confirmed.id) } returns confirmed andThen approved
        every { sponsorshipRepository.updateReviewStatus(confirmed.id, SponsorshipReviewStatus.APPROVED, currentUser.userId) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        every { sponsorRepository.findById(sponsor.id) } returns sponsor
        every { sponsorshipPackageRepository.findById(pkg.id, orgId) } returns pkg
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.approve(orgId, confirmed.id, currentUser)

        assertEquals(SponsorshipReviewStatus.APPROVED, result.reviewStatus)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "sponsorship.approved", "sponsorship", confirmed.id) }
        verify(exactly = 1) {
            outboxWriter.write(
                aggregateType = "sponsorship",
                aggregateId = confirmed.id,
                organizationId = orgId,
                eventType = "sponsorship.approved",
                payloadJson = any(),
            )
        }
    }

    @Test
    fun `approve rejects a sponsorship that isn't CONFIRMED yet`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val pending = pendingSponsorship(pkg, sponsor)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(pending.id) } returns pending

        assertFailsWith<ValidationException> {
            service.approve(orgId, pending.id, currentUser)
        }
    }

    @Test
    fun `approve rejects a sponsorship that was already reviewed`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val alreadyApproved =
            pendingSponsorship(pkg, sponsor)
                .copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = Instant.now(), reviewStatus = SponsorshipReviewStatus.APPROVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(alreadyApproved.id) } returns alreadyApproved

        assertFailsWith<ValidationException> {
            service.approve(orgId, alreadyApproved.id, currentUser)
        }
    }

    @Test
    fun `approve throws NotFoundException for a sponsorship in another organization`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val confirmed = pendingSponsorship(pkg, sponsor).copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = Instant.now())
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(confirmed.id) } returns confirmed.copy(organizationId = UUID.randomUUID())

        assertFailsWith<NotFoundException> {
            service.approve(orgId, confirmed.id, currentUser)
        }
    }

    @Test
    fun `reject moves PENDING_REVIEW to REJECTED and atomically refunds via Stripe and the ledger`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val confirmed =
            pendingSponsorship(pkg, sponsor).copy(
                status = SponsorshipStatus.CONFIRMED,
                confirmedAt = Instant.now(),
                stripePaymentIntentId = "pi_test_123",
            )
        val rejectedRefunded = confirmed.copy(status = SponsorshipStatus.REFUNDED, reviewStatus = SponsorshipReviewStatus.REJECTED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(confirmed.id) } returns confirmed andThen rejectedRefunded
        every { sponsorshipRepository.updateReviewStatus(confirmed.id, SponsorshipReviewStatus.REJECTED, currentUser.userId) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        every { stripeSponsorshipCheckoutClient.createRefund("pi_test_123") } returns "re_test_123"
        every { sponsorshipRepository.markRefunded(confirmed.id) } returns 1
        every {
            ledgerService.recordRefund(
                orgId,
                LedgerSourceType.SPONSORSHIP,
                confirmed.id,
                confirmed.amountMinor,
                confirmed.currency,
                "re_test_123",
            )
        } just runs
        every { sponsorRepository.findById(sponsor.id) } returns sponsor
        every { sponsorshipPackageRepository.findById(pkg.id, orgId) } returns pkg
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val result = service.reject(orgId, confirmed.id, currentUser)

        assertEquals(SponsorshipReviewStatus.REJECTED, result.reviewStatus)
        assertEquals(SponsorshipStatus.REFUNDED, result.status)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "sponsorship.rejected", "sponsorship", confirmed.id) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "sponsorship.refunded", "sponsorship", confirmed.id) }
        verify(exactly = 1) { stripeSponsorshipCheckoutClient.createRefund("pi_test_123") }
        verify(exactly = 1) {
            outboxWriter.write(
                aggregateType = "sponsorship",
                aggregateId = confirmed.id,
                organizationId = orgId,
                eventType = "sponsorship.refunded",
                payloadJson = any(),
            )
        }
    }

    @Test
    fun `refund is rejected outside the 14-day window`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val old =
            pendingSponsorship(pkg, sponsor).copy(
                status = SponsorshipStatus.CONFIRMED,
                confirmedAt = Instant.now().minus(Duration.ofDays(15)),
                stripePaymentIntentId = "pi_test_999",
            )
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(old.id) } returns old

        assertFailsWith<ValidationException> {
            service.refund(orgId, old.id, currentUser)
        }
        verify(exactly = 0) { stripeSponsorshipCheckoutClient.createRefund(any()) }
    }

    @Test
    fun `refund rejects a sponsorship that was never confirmed`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val pending = pendingSponsorship(pkg, sponsor)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(pending.id) } returns pending

        assertFailsWith<ValidationException> {
            service.refund(orgId, pending.id, currentUser)
        }
    }

    @Test
    fun `refund translates a Stripe failure into ServiceUnavailableException`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val confirmed =
            pendingSponsorship(pkg, sponsor).copy(
                status = SponsorshipStatus.CONFIRMED,
                confirmedAt = Instant.now(),
                stripePaymentIntentId = "pi_test_123",
            )
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(confirmed.id) } returns confirmed
        every { stripeSponsorshipCheckoutClient.createRefund("pi_test_123") } throws ApiConnectionException("no network")

        assertFailsWith<ServiceUnavailableException> {
            service.refund(orgId, confirmed.id, currentUser)
        }
    }

    @Test
    fun `listPublicDirectory only reflects the repository's already-approved filter (unit-level trust boundary)`() {
        every { organizationRepository.findBySlug("riverside-fc") } returns sampleOrganization()
        every { sponsorshipRepository.findConfirmedForOrganization(orgId) } returns emptyList()

        val result = service.listPublicDirectory("riverside-fc")

        assertEquals(emptyList(), result)
        verify(exactly = 1) { sponsorshipRepository.findConfirmedForOrganization(orgId) }
    }

    @Test
    fun `getInvoice returns a receipt-style summary for a confirmed sponsorship`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val confirmed = pendingSponsorship(pkg, sponsor).copy(status = SponsorshipStatus.CONFIRMED, confirmedAt = Instant.now())
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(confirmed.id) } returns confirmed
        every { sponsorRepository.findById(sponsor.id) } returns sponsor
        every { sponsorshipPackageRepository.findById(pkg.id, orgId) } returns pkg
        every { organizationRepository.findById(orgId) } returns sampleOrganization()

        val invoice = service.getInvoice(orgId, confirmed.id, currentUser)

        assertEquals(confirmed.id, invoice.sponsorship.id)
        assertEquals(sponsor.name, invoice.sponsor.name)
        assertEquals(pkg.name, invoice.sponsorshipPackage.name)
    }

    @Test
    fun `getInvoice rejects a sponsorship that was never confirmed`() {
        val pkg = sponsorshipPackage()
        val sponsor = sampleSponsor(pkg.organizationId)
        val pending = pendingSponsorship(pkg, sponsor)
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { sponsorshipRepository.findById(pending.id) } returns pending

        assertFailsWith<ValidationException> {
            service.getInvoice(orgId, pending.id, currentUser)
        }
    }

    private fun managerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleOrganization() =
        Organization(
            id = orgId,
            name = "Riverside FC",
            slug = "riverside-fc",
            organizationType = OrganizationType.RECREATIONAL_LEAGUE,
            status = OrganizationStatus.ACTIVE,
            sports = emptyList(),
            contactEmail = null,
            contactPhone = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sponsorshipPackage(
        status: SponsorshipPackageStatus = SponsorshipPackageStatus.PUBLISHED,
        maxQuantity: Int? = null,
    ) = SponsorshipPackage(
        id = UUID.randomUUID(),
        organizationId = orgId,
        name = "Gold Sponsor",
        description = null,
        priceMinor = 50_000L,
        currency = "USD",
        maxQuantity = maxQuantity,
        exclusive = false,
        placementStartDate = null,
        placementEndDate = null,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun sampleSponsor(organizationId: UUID) =
        Sponsor(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            name = "Acme Co",
            contactEmail = "sponsor@acme.test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun pendingSponsorship(
        pkg: SponsorshipPackage,
        sponsor: Sponsor,
    ) = Sponsorship(
        id = UUID.randomUUID(),
        organizationId = pkg.organizationId,
        packageId = pkg.id,
        sponsorId = sponsor.id,
        amountMinor = pkg.priceMinor,
        currency = pkg.currency,
        status = SponsorshipStatus.PENDING,
        stripeCheckoutSessionId = "cs_test_123",
        stripePaymentIntentId = null,
        confirmedAt = null,
        createdAt = Instant.now(),
    )
}
