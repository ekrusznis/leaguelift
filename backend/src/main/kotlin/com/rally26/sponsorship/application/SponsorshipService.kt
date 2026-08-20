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
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.membership.application.MembershipService
import com.rally26.organization.domain.Organization
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.sponsorship.domain.Sponsor
import com.rally26.sponsorship.domain.Sponsorship
import com.rally26.sponsorship.domain.SponsorshipPackage
import com.rally26.sponsorship.domain.SponsorshipPackageStatus
import com.rally26.sponsorship.domain.SponsorshipReviewStatus
import com.rally26.sponsorship.domain.SponsorshipSearchCriteria
import com.rally26.sponsorship.domain.SponsorshipSearchRow
import com.rally26.sponsorship.domain.SponsorshipStatus
import com.rally26.sponsorship.domain.effectiveMaxQuantity
import com.rally26.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.rally26.sponsorship.persistence.SponsorRepository
import com.rally26.sponsorship.persistence.SponsorshipPackageRepository
import com.rally26.sponsorship.persistence.SponsorshipRepository
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger(SponsorshipService::class.java)

/** Mirrors `ContributionService.CONTRIBUTION_ID_PLACEHOLDER` — the frontend can't know the sponsorship id until this call returns. */
const val SPONSORSHIP_ID_PLACEHOLDER = "{SPONSORSHIP_ID}"

/** Org-admin-initiated sponsorship refunds — including the refund `reject()` triggers — are only allowed within this window of confirmation (mirrors ADR-017's contribution/order refund window; Phase 6 remainder, ADR-019). */
val SPONSORSHIP_REFUND_WINDOW: Duration = Duration.ofDays(14)

/** A directory entry combining a confirmed sponsorship with its sponsor's display name/logo — the public sponsor directory's response shape. */
data class SponsorDirectoryEntry(
    val sponsorId: UUID,
    val sponsorName: String,
    val packageId: UUID,
    val packageName: String,
    val logoUrl: String?,
)

/** A downloadable/viewable receipt-style summary of one confirmed sponsorship (Phase 6 remainder, ADR-019) — no invoice numbering sequence, no formal accounting-invoice system. */
data class SponsorshipInvoice(
    val sponsorship: Sponsorship,
    val sponsor: Sponsor,
    val sponsorshipPackage: SponsorshipPackage,
    val organization: Organization,
)

/** `sponsorship.approved` outbox payload (Phase 8 slice 2) — consumed by `SponsorshipApprovedEmailHandler`. */
data class SponsorshipApprovedPayload(
    val sponsorContactEmail: String?,
    val sponsorName: String,
    val packageName: String,
    val organizationSlug: String?,
)

/** `sponsorship.refunded` outbox payload (Phase 8 slice 2) — consumed by `SponsorshipRefundedEmailHandler`; covers both a general refund and a reject-triggered refund. */
data class SponsorshipRefundedPayload(
    val sponsorContactEmail: String?,
    val sponsorName: String,
    val packageName: String,
    val amountMinor: Long,
    val currency: String,
    val organizationSlug: String?,
    val refundedAt: String,
)

/**
 * Sponsorship checkout (Phase 6 slice 1, ADR-018) plus the Phase 6 remainder additions
 * (ADR-019): an org-admin approval workflow gating public directory visibility, refunds
 * (including a rejection-triggered refund), and invoices. Confirmation still happens
 * only via the Stripe webhook — see [confirmFromWebhook] — but confirmation alone no
 * longer grants public visibility; an org admin must separately [approve] a confirmed
 * sponsorship first.
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
    private val outboxWriter: OutboxWriter,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun createCheckoutSession(
        packageId: UUID,
        sponsorName: String,
        sponsorContactEmail: String?,
        successUrl: String,
        cancelUrl: String,
    ): SponsorshipCheckout {
        val sponsorshipPackage =
            sponsorshipPackageRepository.findById(packageId)
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
            val provisional =
                sponsorshipRepository.insertPending(
                    sponsorshipPackage.organizationId,
                    sponsorshipPackage.id,
                    sponsor.id,
                    sponsorshipPackage.priceMinor,
                    sponsorshipPackage.currency,
                )
            val resolvedSuccessUrl = successUrl.replace(SPONSORSHIP_ID_PLACEHOLDER, provisional.id.toString())
            val session =
                stripeSponsorshipCheckoutClient.createSponsorshipCheckoutSession(
                    provisional.id,
                    sponsorshipPackage.priceMinor,
                    sponsorshipPackage.currency,
                    sponsorshipPackage.name,
                    resolvedSuccessUrl,
                    cancelUrl,
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

    /** Idempotent: a duplicate webhook delivery or an already-confirmed sponsorship is a safe no-op. Confirmation leaves `reviewStatus` at its PENDING_REVIEW default — an org admin must separately [approve] before this sponsor appears on the public directory (Phase 6 remainder approval workflow, ADR-019). */
    @Transactional
    fun confirmFromWebhook(
        stripeSessionId: String,
        stripePaymentStatus: String,
        stripePaymentIntentId: String?,
    ): Sponsorship? {
        val sponsorship = sponsorshipRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
        if (stripePaymentStatus != "paid") return sponsorship
        val updated = sponsorshipRepository.markConfirmed(sponsorship.id, stripePaymentIntentId)
        if (updated > 0) {
            auditService.record(null, sponsorship.organizationId, "sponsorship.confirmed", "sponsorship", sponsorship.id)
            ledgerService.recordConfirmedSponsorship(sponsorship.copy(status = SponsorshipStatus.CONFIRMED))
            ledgerService.recordStripeProcessingFee(
                sponsorship.organizationId,
                LedgerSourceType.SPONSORSHIP,
                sponsorship.id,
                sponsorship.currency,
                stripePaymentIntentId,
            )
        }
        return sponsorshipRepository.findById(sponsorship.id)
    }

    fun getStatus(
        packageId: UUID,
        sponsorshipId: UUID,
    ): Sponsorship {
        sponsorshipPackageRepository.findById(packageId)
            ?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
        return sponsorshipRepository
            .findById(sponsorshipId)
            ?.takeIf { it.packageId == packageId }
            ?: throw NotFoundException("SPONSORSHIP_NOT_FOUND", "The sponsorship could not be found.")
    }

    fun getConfirmedCount(packageId: UUID): Long = sponsorshipRepository.countConfirmedForPackage(packageId)

    fun listConfirmed(
        organizationId: UUID,
        packageId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<SponsorshipWithSponsor> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val sponsorshipPackage =
            sponsorshipPackageRepository.findById(packageId, organizationId)
                ?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")
        return sponsorshipRepository.listConfirmedForPackage(sponsorshipPackage.id, offset, limit).map(::withSponsor)
    }

    /** The org-admin approval queue (Phase 6 remainder, ADR-019) — every confirmed sponsorship across the organization still awaiting a review decision, oldest first (first paid, first reviewed). */
    fun listPendingReview(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<SponsorshipWithSponsor> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return sponsorshipRepository.listPendingReview(organizationId, offset, limit).map(::withSponsor)
    }

    fun countPendingReview(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return sponsorshipRepository.countPendingReview(organizationId)
    }

    fun search(
        organizationId: UUID,
        criteria: SponsorshipSearchCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<SponsorshipSearchRow> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return sponsorshipRepository.search(organizationId, criteria, offset, limit)
    }

    fun countSearch(
        organizationId: UUID,
        criteria: SponsorshipSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return sponsorshipRepository.countSearch(organizationId, criteria)
    }

    /** Public sponsor directory (DESIGN-DOC.md section 14.1 scope) — every confirmed AND approved sponsor for an organization, with logo if one has been assigned. */
    fun listPublicDirectory(organizationSlug: String): List<SponsorDirectoryEntry> {
        val organization =
            organizationRepository.findBySlug(organizationSlug)
                ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
        return sponsorshipRepository.findConfirmedForOrganization(organization.id).mapNotNull { sponsorship ->
            val sponsor = sponsorRepository.findById(sponsorship.sponsorId) ?: return@mapNotNull null
            val sponsorshipPackage = sponsorshipPackageRepository.findById(sponsorship.packageId) ?: return@mapNotNull null
            val logoUrl =
                mediaAssignmentService
                    .getPublicAssignment(MediaEntityType.SPONSOR, sponsor.id, MediaUsageSlot.SPONSOR_LOGO)
                    ?.let { mediaReadService.describe(it)?.url }
            SponsorDirectoryEntry(sponsor.id, sponsor.name, sponsorshipPackage.id, sponsorshipPackage.name, logoUrl)
        }
    }

    /** Org-admin approves a confirmed, still-pending-review sponsorship — the only action that makes it appear on the public directory (Phase 6 remainder approval workflow, ADR-019). */
    @Transactional
    fun approve(
        organizationId: UUID,
        sponsorshipId: UUID,
        currentUser: CurrentUser,
    ): Sponsorship {
        membershipService.requireManagerRole(organizationId, currentUser)
        val sponsorship = findOwnedSponsorship(organizationId, sponsorshipId)
        requireReviewable(sponsorship)
        sponsorshipRepository.updateReviewStatus(sponsorship.id, SponsorshipReviewStatus.APPROVED, currentUser.userId)
        auditService.record(currentUser.userId, organizationId, "sponsorship.approved", "sponsorship", sponsorship.id)
        val sponsor = sponsorRepository.findById(sponsorship.sponsorId)
        val sponsorshipPackage = sponsorshipPackageRepository.findById(sponsorship.packageId, organizationId)
        if (sponsor != null && sponsorshipPackage != null) {
            val organization = organizationRepository.findById(organizationId)
            outboxWriter.write(
                aggregateType = "sponsorship",
                aggregateId = sponsorship.id,
                organizationId = organizationId,
                eventType = "sponsorship.approved",
                payloadJson =
                    objectMapper.writeValueAsString(
                        SponsorshipApprovedPayload(sponsor.contactEmail, sponsor.name, sponsorshipPackage.name, organization?.slug),
                    ),
            )
        }
        return sponsorshipRepository.findById(sponsorship.id)!!
    }

    /**
     * Org-admin rejects a confirmed, still-pending-review sponsorship. Per the founder's
     * decision, rejection is never "charged but hidden" — it atomically (same
     * transaction) triggers the same Stripe refund + ledger reversal [refund] performs.
     * If the Stripe refund call fails, the whole rejection rolls back (including the
     * review-status change) rather than leaving a rejected-but-still-charged sponsorship.
     */
    @Transactional
    fun reject(
        organizationId: UUID,
        sponsorshipId: UUID,
        currentUser: CurrentUser,
    ): Sponsorship {
        membershipService.requireManagerRole(organizationId, currentUser)
        val sponsorship = findOwnedSponsorship(organizationId, sponsorshipId)
        requireReviewable(sponsorship)
        sponsorshipRepository.updateReviewStatus(sponsorship.id, SponsorshipReviewStatus.REJECTED, currentUser.userId)
        auditService.record(currentUser.userId, organizationId, "sponsorship.rejected", "sponsorship", sponsorship.id)
        return performRefund(organizationId, sponsorship, currentUser, auditAction = "sponsorship.refunded")
    }

    /** A general org-admin-initiated refund (Phase 6 remainder, ADR-019) — independent of the review workflow; a sponsorship can be refunded whether or not it was ever approved. */
    @Transactional
    fun refund(
        organizationId: UUID,
        sponsorshipId: UUID,
        currentUser: CurrentUser,
    ): Sponsorship {
        membershipService.requireManagerRole(organizationId, currentUser)
        val sponsorship = findOwnedSponsorship(organizationId, sponsorshipId)
        return performRefund(organizationId, sponsorship, currentUser, auditAction = "sponsorship.refunded")
    }

    /** A receipt-style summary of one confirmed (or since-refunded) sponsorship (Phase 6 remainder, ADR-019). */
    fun getInvoice(
        organizationId: UUID,
        sponsorshipId: UUID,
        currentUser: CurrentUser,
    ): SponsorshipInvoice {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val sponsorship = findOwnedSponsorship(organizationId, sponsorshipId)
        if (sponsorship.status == SponsorshipStatus.PENDING) {
            throw ValidationException("An invoice is only available once a sponsorship has been confirmed.")
        }
        val sponsor =
            sponsorRepository.findById(sponsorship.sponsorId)
                ?: error("sponsorship ${sponsorship.id} references a missing sponsor")
        val sponsorshipPackage =
            sponsorshipPackageRepository.findById(sponsorship.packageId, organizationId)
                ?: error("sponsorship ${sponsorship.id} references a missing package")
        val organization =
            organizationRepository.findById(organizationId)
                ?: error("sponsorship ${sponsorship.id} references a missing organization")
        return SponsorshipInvoice(sponsorship, sponsor, sponsorshipPackage, organization)
    }

    private fun performRefund(
        organizationId: UUID,
        sponsorship: Sponsorship,
        currentUser: CurrentUser,
        auditAction: String,
    ): Sponsorship {
        if (sponsorship.status != SponsorshipStatus.CONFIRMED || sponsorship.stripePaymentIntentId == null) {
            throw ValidationException("Only a confirmed sponsorship with a recorded payment can be refunded.")
        }
        val confirmedAt = sponsorship.confirmedAt ?: throw ValidationException("This sponsorship has no confirmation date on record.")
        if (Duration.between(confirmedAt, Instant.now()) > SPONSORSHIP_REFUND_WINDOW) {
            throw ValidationException(
                "This sponsorship can no longer be refunded — it was confirmed more than ${SPONSORSHIP_REFUND_WINDOW.toDays()} days ago.",
            )
        }
        val stripeRefundId =
            try {
                stripeSponsorshipCheckoutClient.createRefund(sponsorship.stripePaymentIntentId)
            } catch (e: StripeException) {
                log.warn("Stripe refund failed for sponsorship {}: {}", sponsorship.id, e.message, e)
                throw ServiceUnavailableException(
                    "SPONSORSHIP_PROVIDER_UNAVAILABLE",
                    "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
                )
            }
        sponsorshipRepository.markRefunded(sponsorship.id)
        ledgerService.recordRefund(
            organizationId,
            LedgerSourceType.SPONSORSHIP,
            sponsorship.id,
            sponsorship.amountMinor,
            sponsorship.currency,
            stripeRefundId,
        )
        auditService.record(currentUser.userId, organizationId, auditAction, "sponsorship", sponsorship.id)
        val sponsor = sponsorRepository.findById(sponsorship.sponsorId)
        val sponsorshipPackage = sponsorshipPackageRepository.findById(sponsorship.packageId, organizationId)
        if (sponsor != null && sponsorshipPackage != null) {
            val organization = organizationRepository.findById(organizationId)
            outboxWriter.write(
                aggregateType = "sponsorship",
                aggregateId = sponsorship.id,
                organizationId = organizationId,
                eventType = "sponsorship.refunded",
                payloadJson =
                    objectMapper.writeValueAsString(
                        SponsorshipRefundedPayload(
                            sponsor.contactEmail,
                            sponsor.name,
                            sponsorshipPackage.name,
                            sponsorship.amountMinor,
                            sponsorship.currency,
                            organization?.slug,
                            Instant.now().toString(),
                        ),
                    ),
            )
        }
        return sponsorshipRepository.findById(sponsorship.id)!!
    }

    private fun requireReviewable(sponsorship: Sponsorship) {
        if (sponsorship.status != SponsorshipStatus.CONFIRMED) {
            throw ValidationException("Only a confirmed sponsorship can be reviewed.")
        }
        if (sponsorship.reviewStatus != SponsorshipReviewStatus.PENDING_REVIEW) {
            throw ValidationException("This sponsorship has already been reviewed.")
        }
    }

    private fun findOwnedSponsorship(
        organizationId: UUID,
        sponsorshipId: UUID,
    ): Sponsorship =
        sponsorshipRepository
            .findById(sponsorshipId)
            ?.takeIf { it.organizationId == organizationId }
            ?: throw NotFoundException("SPONSORSHIP_NOT_FOUND", "The sponsorship could not be found.")

    private fun withSponsor(sponsorship: Sponsorship): SponsorshipWithSponsor {
        val sponsor =
            sponsorRepository.findById(sponsorship.sponsorId)
                ?: error("sponsorship ${sponsorship.id} references a missing sponsor")
        return SponsorshipWithSponsor(sponsorship, sponsor)
    }
}

data class SponsorshipCheckout(
    val sponsorshipId: UUID,
    val sponsorId: UUID,
    val checkoutUrl: String,
)

data class SponsorshipWithSponsor(
    val sponsorship: Sponsorship,
    val sponsor: Sponsor,
)
