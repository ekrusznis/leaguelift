package com.rally26.boxpool.application

import com.rally26.audit.application.AuditService
import com.rally26.boxpool.domain.BoxPool
import com.rally26.boxpool.domain.BoxPoolBox
import com.rally26.boxpool.persistence.BoxPoolBoxRepository
import com.rally26.boxpool.persistence.BoxPoolRepository
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.ContributionCheckout
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.FundraiserTemplateKey
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A newly-reserved box holds its spot for this long before another claimant can take it if checkout is never completed — checked lazily at the next reserve/read, no scheduled sweep job. */
val BOX_RESERVATION_WINDOW: Duration = Duration.ofMinutes(15)

/**
 * Fundraising Templates (Phase 42, DESIGN-DOC.md §14.1Q) — the sports box pool
 * template. A box purchase is deliberately modeled as an ordinary [ContributionService]
 * checkout (same Stripe flow, same ledger entries every other contribution posts) with
 * a `box_pool_box.contribution_id` link, not a new payment/ledger concept. A box flips
 * OPEN → CLAIMED via a direct synchronous call inside
 * [com.rally26.fundraising.application.ContributionService.confirmFromWebhook] — not an
 * outbox event, since `OutboxWorker` supports exactly one handler per event type and
 * `contribution.confirmed` already belongs to `ContributionThankYouEmailHandler` — same
 * pattern this codebase already uses for granting family credit on a confirmed,
 * attributed contribution.
 */
@Service
class BoxPoolService(
    private val boxPoolRepository: BoxPoolRepository,
    private val boxPoolBoxRepository: BoxPoolBoxRepository,
    private val campaignRepository: CampaignRepository,
    private val contributionService: ContributionService,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    @Transactional
    fun create(
        organizationId: UUID,
        campaignId: UUID,
        sport: String,
        rows: Int,
        cols: Int,
        pricePerBoxMinor: Long,
        rowAxisLabel: String?,
        colAxisLabel: String?,
        prizeDescription: String?,
        currentUser: CurrentUser,
    ): BoxPool {
        membershipService.requireManagerRole(organizationId, currentUser)
        val campaign =
            campaignRepository.findById(campaignId, organizationId)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        if (campaign.templateKey != FundraiserTemplateKey.BOX_POOL) {
            throw ValidationException("A box pool can only be set up for a campaign using the box pool template.")
        }
        if (boxPoolRepository.findByCampaignId(campaignId) != null) {
            throw ConflictException("BOX_POOL_ALREADY_EXISTS", "This campaign already has a box pool.")
        }
        if (rows !in 1..26 || cols !in 1..26) {
            throw ValidationException("Rows and columns must each be between 1 and 26.")
        }
        if (pricePerBoxMinor <= 0) {
            throw ValidationException("Price per box must be greater than zero.")
        }
        val pool =
            boxPoolRepository.insert(
                campaignId,
                organizationId,
                sport,
                rows,
                cols,
                pricePerBoxMinor,
                rowAxisLabel,
                colAxisLabel,
                prizeDescription,
            )
        boxPoolBoxRepository.insertGrid(pool.id, rows, cols)
        auditService.record(currentUser.userId, organizationId, "box_pool.created", "box_pool", pool.id)
        return pool
    }

    fun get(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): Pair<BoxPool, List<BoxPoolBox>> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val pool = requirePoolForCampaign(organizationId, campaignId)
        return pool to boxPoolBoxRepository.listByPool(pool.id)
    }

    /** Public — the pool plus every box, including claimant *names* (never emails) for already-claimed/reserved boxes, so a visitor sees which squares are taken by whom. */
    fun getPublic(campaignSlug: String): Pair<BoxPool, List<BoxPoolBox>> {
        val campaign = campaignRepository.findBySlug(campaignSlug) ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        val pool =
            boxPoolRepository.findByCampaignId(campaign.id)
                ?: throw NotFoundException("BOX_POOL_NOT_FOUND", "This campaign has no box pool.")
        return pool to boxPoolBoxRepository.listByPool(pool.id)
    }

    /**
     * Public/unauthenticated claim — reuses [ContributionService.createCheckoutSession]
     * unchanged (same Stripe session, same eventual ledger entries as any other
     * contribution to this campaign), then reserves the specific box against the new
     * pending contribution. No box-pool-specific Stripe or ledger code exists anywhere
     * in this module by design.
     *
     * Known, documented limitation: the claimable-check and the reservation are two
     * separate statements, not one atomic operation (this codebase has no established
     * `SELECT ... FOR UPDATE` pattern anywhere to model this on). Two people claiming
     * the exact same box within the same instant could both pass the check and both
     * create a real Stripe checkout session; only one `reserve()` write wins, and the
     * loser's [ConflictException] fires before Stripe is reached in the normal case —
     * but if a loser somehow still completes their own Stripe payment afterward, the
     * payment is captured and ledgered normally (money is never lost or double-spent),
     * it just isn't linked to any box for them. Acceptable for a first slice given how
     * rare simultaneous same-box claims are in practice; revisit only if real usage
     * shows otherwise.
     */
    @Transactional
    fun reserveBox(
        campaignSlug: String,
        rowIndex: Int,
        colIndex: Int,
        claimantName: String,
        claimantEmail: String?,
        successUrl: String,
        cancelUrl: String,
    ): ContributionCheckout {
        val campaign = campaignRepository.findBySlug(campaignSlug) ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        val pool =
            boxPoolRepository.findByCampaignId(campaign.id)
                ?: throw NotFoundException("BOX_POOL_NOT_FOUND", "This campaign has no box pool.")
        val box =
            boxPoolBoxRepository.findClaimableByPosition(pool.id, rowIndex, colIndex)
                ?: throw ConflictException("BOX_NOT_AVAILABLE", "This box has already been claimed.")
        val checkout =
            contributionService.createCheckoutSession(
                campaignSlug,
                pool.pricePerBoxMinor,
                claimantName,
                false,
                claimantEmail,
                successUrl,
                cancelUrl,
            )
        val reserved = boxPoolBoxRepository.reserve(box.id, claimantName, claimantEmail, checkout.contributionId, Instant.now().plus(BOX_RESERVATION_WINDOW))
        if (reserved == 0) {
            throw ConflictException("BOX_NOT_AVAILABLE", "This box has already been claimed.")
        }
        return checkout
    }

    private fun requirePoolForCampaign(
        organizationId: UUID,
        campaignId: UUID,
    ): BoxPool {
        campaignRepository.findById(campaignId, organizationId)
            ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        return boxPoolRepository.findByCampaignId(campaignId)
            ?: throw NotFoundException("BOX_POOL_NOT_FOUND", "This campaign has no box pool.")
    }
}
