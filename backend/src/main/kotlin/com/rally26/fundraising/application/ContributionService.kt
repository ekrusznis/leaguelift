package com.rally26.fundraising.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.boxpool.persistence.BoxPoolBoxRepository
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.application.FamilyCreditService
import com.rally26.credit.application.HouseholdAttributionService
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionLimits
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.fundraising.infra.StripeCheckoutClient
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger(ContributionService::class.java)

/** Mirrors Stripe's own `{CHECKOUT_SESSION_ID}` success-url placeholder convention — the frontend can't know the contribution id until this call returns, so it asks for it to be filled in server-side instead. */
const val CONTRIBUTION_ID_PLACEHOLDER = "{CONTRIBUTION_ID}"

/** Org-admin-initiated refunds are only allowed within this window of confirmation (ADR-017, 2026-07-29 founder decision). */
val REFUND_WINDOW = Duration.ofDays(14)

/** `contribution.confirmed` outbox payload (Phase 8 slice 2) — consumed by `ContributionThankYouEmailHandler`. */
data class ContributionConfirmedPayload(
    val supporterEmail: String,
    val supporterName: String?,
    val amountMinor: Long,
    val currency: String,
    val campaignName: String,
    val campaignSlug: String?,
    val confirmedAt: String,
)

/**
 * Campaign contribution checkout (Phase 3 remainder). Confirmation happens only
 * via the Stripe webhook (`webhook/web/StripeWebhookController.kt` ->
 * [confirmFromWebhook]) — there is no "sync refresh on browser return" path here
 * the way the payout module has for Connect onboarding, because a supporter who
 * pays and closes the tab before redirecting back would otherwise leave Stripe
 * holding confirmed money Rally26 never records.
 *
 * Family credits, refunds, and disputes are explicitly out of scope for this
 * slice — see DESIGN-DOC.md section 19.3 open questions #6/#16/#17 (credit
 * percentages/cross-season/expiry) and section 16 (refunds are Phase 5).
 */
@Service
class ContributionService(
    private val contributionRepository: ContributionRepository,
    private val campaignRepository: CampaignRepository,
    private val stripeCheckoutClient: StripeCheckoutClient,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val ledgerService: LedgerService,
    private val outboxWriter: OutboxWriter,
    private val objectMapper: ObjectMapper,
    private val householdAttributionService: HouseholdAttributionService,
    private val familyCreditService: FamilyCreditService,
    private val boxPoolBoxRepository: BoxPoolBoxRepository,
) {
    @Transactional
    fun createCheckoutSession(
        slug: String,
        amountMinor: Long,
        supporterName: String?,
        isAnonymous: Boolean,
        supporterEmail: String?,
        successUrl: String,
        cancelUrl: String,
        attributionCode: String? = null,
    ): ContributionCheckout {
        val campaign =
            campaignRepository.findBySlug(slug)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        if (campaign.status != CampaignStatus.ACTIVE) {
            throw ValidationException("This campaign isn't currently accepting contributions.")
        }
        if (!ContributionLimits.isAmountAllowed(amountMinor)) {
            throw ValidationException(
                "Contribution amount must be between " +
                    "${ContributionLimits.MIN_AMOUNT_MINOR} and ${ContributionLimits.MAX_AMOUNT_MINOR} minor units.",
            )
        }
        val displayName = if (isAnonymous) null else supporterName?.take(120)
        // Phase 23 (DESIGN-DOC.md section 13/14.1): resolved here, before Stripe is
        // ever called, and stored directly on the contribution row — not
        // round-tripped through Stripe metadata (see HouseholdAttributionService).
        // An unrecognized/missing code silently resolves to no attribution rather
        // than failing checkout — a stale or mistyped link should never block a
        // supporter from giving.
        val attributedHouseholdId =
            attributionCode?.let { householdAttributionService.resolveByCode(campaign.organizationId, campaign.id, it) }
        return try {
            // Stripe's checkout-session metadata needs our contribution id, so the
            // contribution row is inserted (with a null session id) before Stripe is
            // ever called, then updated with the real session id once Stripe returns it.
            val provisional =
                contributionRepository.insertPending(
                    campaign.organizationId,
                    campaign.id,
                    amountMinor,
                    campaign.currency,
                    displayName,
                    isAnonymous,
                    supporterEmail,
                    attributedHouseholdId,
                )
            val resolvedSuccessUrl = successUrl.replace(CONTRIBUTION_ID_PLACEHOLDER, provisional.id.toString())
            val session =
                stripeCheckoutClient.createContributionCheckoutSession(
                    provisional.id,
                    amountMinor,
                    campaign.currency,
                    campaign.name,
                    resolvedSuccessUrl,
                    cancelUrl,
                )
            contributionRepository.attachStripeSession(provisional.id, session.sessionId)
            ContributionCheckout(contributionId = provisional.id, checkoutUrl = session.checkoutUrl)
        } catch (e: StripeException) {
            log.warn("Stripe checkout session creation failed: {}", e.message, e)
            throw ServiceUnavailableException(
                "CONTRIBUTION_PROVIDER_UNAVAILABLE",
                "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
            )
        }
    }

    /** Idempotent: a duplicate webhook delivery or an already-confirmed contribution is a safe no-op. */
    @Transactional
    fun confirmFromWebhook(
        stripeSessionId: String,
        stripePaymentStatus: String,
        stripePaymentIntentId: String?,
    ): Contribution? {
        val contribution = contributionRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
        if (stripePaymentStatus != "paid") return contribution
        val updated = contributionRepository.markConfirmed(contribution.id, stripePaymentIntentId)
        if (updated > 0) {
            auditService.record(null, contribution.organizationId, "contribution.confirmed", "contribution", contribution.id)
            ledgerService.recordConfirmedContribution(contribution.copy(status = ContributionStatus.CONFIRMED))
            ledgerService.recordStripeProcessingFee(
                contribution.organizationId,
                LedgerSourceType.CONTRIBUTION,
                contribution.id,
                contribution.currency,
                stripePaymentIntentId,
            )
            if (contribution.attributedHouseholdId != null) {
                familyCreditService.grantForContribution(
                    contribution.organizationId,
                    contribution.attributedHouseholdId,
                    contribution.id,
                    contribution.amountMinor,
                    contribution.currency,
                )
            }
            // Fundraising Templates (Phase 42): a box-pool purchase is an ordinary
            // contribution with a box_pool_box.contribution_id link — this no-ops for
            // every other contribution (findByContributionId returns null). Direct
            // synchronous call, same pattern as familyCreditService above, not an
            // outbox event: the outbox worker supports exactly one handler per event
            // type (OutboxWorker.handlersByEventType is a 1:1 map), and
            // ContributionThankYouEmailHandler already owns "contribution.confirmed" —
            // a second listener for the same type would silently replace it. This also
            // keeps the box-claim state change atomic with the confirmation itself,
            // same reasoning as family-credit granting.
            boxPoolBoxRepository.findByContributionId(contribution.id)?.let { boxPoolBoxRepository.claim(it.id) }
            if (contribution.supporterEmail != null) {
                val campaign = campaignRepository.findById(contribution.campaignId, contribution.organizationId)
                outboxWriter.write(
                    aggregateType = "contribution",
                    aggregateId = contribution.id,
                    organizationId = contribution.organizationId,
                    eventType = "contribution.confirmed",
                    payloadJson =
                        objectMapper.writeValueAsString(
                            ContributionConfirmedPayload(
                                contribution.supporterEmail,
                                contribution.supporterName,
                                contribution.amountMinor,
                                contribution.currency,
                                campaign?.name ?: "your campaign",
                                campaign?.slug,
                                Instant.now().toString(),
                            ),
                        ),
                )
            }
        }
        return contributionRepository.findById(contribution.id)
    }

    fun getStatus(
        slug: String,
        contributionId: UUID,
    ): Contribution {
        val campaign =
            campaignRepository.findBySlug(slug)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        val contribution =
            contributionRepository
                .findById(contributionId)
                ?.takeIf { it.campaignId == campaign.id }
                ?: throw NotFoundException("CONTRIBUTION_NOT_FOUND", "The contribution could not be found.")
        return contribution
    }

    fun getConfirmedTotal(campaignId: UUID): Long = contributionRepository.sumConfirmedByCampaign(campaignId)

    fun getConfirmedCount(campaignId: UUID): Long = contributionRepository.countConfirmedForCampaign(campaignId)

    fun listConfirmed(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Contribution> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val campaign =
            campaignRepository.findById(campaignId, organizationId)
                ?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
        return contributionRepository.listConfirmedForCampaign(campaign.id, offset, limit)
    }

    @Transactional
    fun refund(
        organizationId: UUID,
        contributionId: UUID,
        currentUser: CurrentUser,
    ): Contribution {
        membershipService.requireManagerRole(organizationId, currentUser)
        val contribution =
            contributionRepository
                .findById(contributionId)
                ?.takeIf { it.organizationId == organizationId }
                ?: throw NotFoundException("CONTRIBUTION_NOT_FOUND", "The contribution could not be found.")
        if (contribution.status != ContributionStatus.CONFIRMED || contribution.stripePaymentIntentId == null) {
            throw ValidationException("Only a confirmed contribution with a recorded payment can be refunded.")
        }
        val confirmedAt = contribution.confirmedAt ?: throw ValidationException("This contribution has no confirmation date on record.")
        if (Duration.between(confirmedAt, Instant.now()) > REFUND_WINDOW) {
            throw ValidationException(
                "This contribution can no longer be refunded — it was confirmed more than ${REFUND_WINDOW.toDays()} days ago.",
            )
        }
        val stripeRefundId =
            try {
                stripeCheckoutClient.createRefund(contribution.stripePaymentIntentId)
            } catch (e: StripeException) {
                log.warn("Stripe refund failed for contribution {}: {}", contributionId, e.message, e)
                throw ServiceUnavailableException(
                    "CONTRIBUTION_PROVIDER_UNAVAILABLE",
                    "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
                )
            }
        contributionRepository.markRefunded(contribution.id)
        if (contribution.attributedHouseholdId != null) {
            familyCreditService.reverseForRefundedContribution(organizationId, contribution.id)
        }
        ledgerService.recordRefund(
            organizationId,
            LedgerSourceType.CONTRIBUTION,
            contribution.id,
            contribution.amountMinor,
            contribution.currency,
            stripeRefundId,
        )
        auditService.record(currentUser.userId, organizationId, "contribution.refunded", "contribution", contribution.id)
        return contributionRepository.findById(contribution.id)!!
    }
}

data class ContributionCheckout(
    val contributionId: UUID,
    val checkoutUrl: String,
)
