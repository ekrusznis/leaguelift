package com.leaguelift.sponsorship.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.ledger.application.LedgerService
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.sponsorship.domain.Sponsor
import com.leaguelift.sponsorship.domain.SponsorshipPackageStatus
import com.leaguelift.sponsorship.domain.Sponsorship
import com.leaguelift.sponsorship.domain.SponsorshipStatus
import com.leaguelift.sponsorship.domain.effectiveMaxQuantity
import com.leaguelift.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.leaguelift.sponsorship.persistence.SponsorRepository
import com.leaguelift.sponsorship.persistence.SponsorshipPackageRepository
import com.leaguelift.sponsorship.persistence.SponsorshipRepository
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = LoggerFactory.getLogger(SponsorshipService::class.java)

/** Mirrors `ContributionService.CONTRIBUTION_ID_PLACEHOLDER` — the frontend can't know the sponsorship id until this call returns. */
const val SPONSORSHIP_ID_PLACEHOLDER = "{SPONSORSHIP_ID}"

/** A directory entry combining a confirmed sponsorship with its sponsor's display name/logo — the public sponsor directory's response shape. */
data class SponsorDirectoryEntry(
	val sponsorId: UUID,
	val sponsorName: String,
	val packageId: UUID,
	val packageName: String,
	val logoUrl: String?,
)

/**
 * Sponsorship checkout (Phase 6 slice 1) — mirrors
 * `fundraising/application/ContributionService.kt` closely: confirmation happens only
 * via the Stripe webhook, never a sync refresh-on-return, for the same reason (a sponsor
 * who pays and closes the tab shouldn't leave Stripe holding confirmed money LeagueLift
 * never records). Refunds, approval workflow, and renewal reminders are explicitly out
 * of scope this slice (ADR-018, DESIGN-DOC.md section 14.1).
 */
@Service
class SponsorshipService(
	private val sponsorshipRepository: SponsorshipRepository,
	private val sponsorshipPackageRepository: SponsorshipPackageRepository,
	private val sponsorRepository: SponsorRepository,
	private val organizationRepository: OrganizationRepository,
	private val stripeSponsorshipCheckoutClient: StripeSponsorshipCheckoutClient,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val ledgerService: LedgerService,
	private val mediaAssignmentService: MediaAssignmentService,
	private val mediaReadService: MediaReadService,
) {

	@Transactional
	fun createCheckoutSession(
		packageId: UUID,
		sponsorName: String,
		sponsorContactEmail: String?,
		successUrl: String,
		cancelUrl: String,
	): SponsorshipCheckout {
		val sponsorshipPackage = sponsorshipPackageRepository.findById(packageId)
			?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
		if (sponsorshipPackage.status != SponsorshipPackageStatus.PUBLISHED) {
			throw ValidationException("This sponsorship package isn't currently accepting sponsors.")
		}
		val effectiveMax = sponsorshipPackage.effectiveMaxQuantity()
		if (effectiveMax != null) {
			// Counts CONFIRMED sponsorships only, the same as ContributionService's/
			// OrderService's own limits — a small oversell race against concurrently
			// PENDING checkouts is an accepted gap for this proof-of-concept slice, not a
			// silent bug (matches how a campaign has no hard concurrency guard against
			// exceeding its goal either).
			val confirmedCount = sponsorshipRepository.countConfirmedForPackage(packageId)
			if (confirmedCount >= effectiveMax) {
				throw ValidationException("This sponsorship package is sold out.")
			}
		}

		return try {
			val sponsor = sponsorRepository.insert(sponsorshipPackage.organizationId, sponsorName, sponsorContactEmail)
			val provisional = sponsorshipRepository.insertPending(
				sponsorshipPackage.organizationId, sponsorshipPackage.id, sponsor.id, sponsorshipPackage.priceMinor, sponsorshipPackage.currency,
			)
			val resolvedSuccessUrl = successUrl.replace(SPONSORSHIP_ID_PLACEHOLDER, provisional.id.toString())
			val session = stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(
				provisional.id, sponsorshipPackage.priceMinor, sponsorshipPackage.currency, sponsorshipPackage.name, resolvedSuccessUrl, cancelUrl,
			)
			sponsorshipRepository.attachStripeSession(provisional.id, session.sessionId)
			SponsorshipCheckout(sponsorshipId = provisional.id, sponsorId = sponsor.id, checkoutUrl = session.checkoutUrl)
		} catch (e: StripeException) {
			log.warn("Stripe sponsorship checkout session creation failed: {}", e.message, e)
			throw ServiceUnavailableException(
				"SPONSORSHIP_PROVIDER_UNAVAILABLE",
				"Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
			)
		}
	}

	/** Idempotent: a duplicate webhook delivery or an already-confirmed sponsorship is a safe no-op. */
	@Transactional
	fun confirmFromWebhook(stripeSessionId: String, stripePaymentStatus: String, stripePaymentIntentId: String?): Sponsorship? {
		val sponsorship = sponsorshipRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
		if (stripePaymentStatus != "paid") return sponsorship
		val updated = sponsorshipRepository.markConfirmed(sponsorship.id, stripePaymentIntentId)
		if (updated > 0) {
			auditService.record(null, sponsorship.organizationId, "sponsorship.confirmed", "sponsorship", sponsorship.id)
			ledgerService.recordConfirmedSponsorship(sponsorship.copy(status = SponsorshipStatus.CONFIRMED))
		}
		return sponsorshipRepository.findById(sponsorship.id)
	}

	fun getStatus(packageId: UUID, sponsorshipId: UUID): Sponsorship {
		sponsorshipPackageRepository.findById(packageId)
			?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
		return sponsorshipRepository.findById(sponsorshipId)
			?.takeIf { it.packageId == packageId }
			?: throw NotFoundException("SPONSORSHIP_NOT_FOUND", "The sponsorship could not be found.")
	}

	fun getConfirmedCount(packageId: UUID): Long = sponsorshipRepository.countConfirmedForPackage(packageId)

	fun listConfirmed(organizationId: UUID, packageId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<SponsorshipWithSponsor> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val sponsorshipPackage = sponsorshipPackageRepository.findById(packageId, organizationId)
			?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
		return sponsorshipRepository.listConfirmedForPackage(sponsorshipPackage.id, offset, limit).map { sponsorship ->
			val sponsor = sponsorRepository.findById(sponsorship.sponsorId)
				?: error("sponsorship ${sponsorship.id} references a missing sponsor")
			SponsorshipWithSponsor(sponsorship, sponsor)
		}
	}

	/** Public sponsor directory (DESIGN-DOC.md section 14.1 scope) — every confirmed sponsor for an organization, with logo if one has been assigned. */
	fun listPublicDirectory(organizationSlug: String): List<SponsorDirectoryEntry> {
		val organization = organizationRepository.findBySlug(organizationSlug)
			?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
		return sponsorshipRepository.findConfirmedForOrganization(organization.id).mapNotNull { sponsorship ->
			val sponsor = sponsorRepository.findById(sponsorship.sponsorId) ?: return@mapNotNull null
			val sponsorshipPackage = sponsorshipPackageRepository.findById(sponsorship.packageId) ?: return@mapNotNull null
			val logoUrl = mediaAssignmentService.getPublicAssignment(MediaEntityType.SPONSOR, sponsor.id, MediaUsageSlot.SPONSOR_LOGO)
				?.let { mediaReadService.describe(it)?.url }
			SponsorDirectoryEntry(sponsor.id, sponsor.name, sponsorshipPackage.id, sponsorshipPackage.name, logoUrl)
		}
	}
}

data class SponsorshipCheckout(val sponsorshipId: UUID, val sponsorId: UUID, val checkoutUrl: String)

data class SponsorshipWithSponsor(val sponsorship: Sponsorship, val sponsor: Sponsor)
