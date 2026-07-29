package com.leaguelift.fundraising.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fundraising.domain.Contribution
import com.leaguelift.fundraising.domain.ContributionLimits
import com.leaguelift.fundraising.domain.ContributionStatus
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.infra.StripeCheckoutClient
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.membership.application.MembershipService
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = LoggerFactory.getLogger(ContributionService::class.java)

/** Mirrors Stripe's own `{CHECKOUT_SESSION_ID}` success-url placeholder convention — the frontend can't know the contribution id until this call returns, so it asks for it to be filled in server-side instead. */
const val CONTRIBUTION_ID_PLACEHOLDER = "{CONTRIBUTION_ID}"

/**
 * Campaign contribution checkout (Phase 3 remainder). Confirmation happens only
 * via the Stripe webhook (`webhook/web/StripeWebhookController.kt` ->
 * [confirmFromWebhook]) — there is no "sync refresh on browser return" path here
 * the way the payout module has for Connect onboarding, because a supporter who
 * pays and closes the tab before redirecting back would otherwise leave Stripe
 * holding confirmed money LeagueLift never records.
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
	): ContributionCheckout {
		val campaign = campaignRepository.findBySlug(slug)
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
		return try {
			// Stripe's checkout-session metadata needs our contribution id, so the
			// contribution row is inserted (with a null session id) before Stripe is
			// ever called, then updated with the real session id once Stripe returns it.
			val provisional = contributionRepository.insertPending(
				campaign.organizationId, campaign.id, amountMinor, campaign.currency,
				displayName, isAnonymous, supporterEmail,
			)
			val resolvedSuccessUrl = successUrl.replace(CONTRIBUTION_ID_PLACEHOLDER, provisional.id.toString())
			val session = stripeCheckoutClient.createContributionCheckoutSession(
				provisional.id, amountMinor, campaign.currency, campaign.name, resolvedSuccessUrl, cancelUrl,
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
	fun confirmFromWebhook(stripeSessionId: String, stripePaymentStatus: String): Contribution? {
		val contribution = contributionRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
		if (stripePaymentStatus != "paid") return contribution
		val updated = contributionRepository.markConfirmed(contribution.id)
		if (updated > 0) {
			auditService.record(null, contribution.organizationId, "contribution.confirmed", "contribution", contribution.id)
		}
		return contributionRepository.findById(contribution.id)
	}

	fun getStatus(slug: String, contributionId: UUID): Contribution {
		val campaign = campaignRepository.findBySlug(slug)
			?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
		val contribution = contributionRepository.findById(contributionId)
			?.takeIf { it.campaignId == campaign.id }
			?: throw NotFoundException("CONTRIBUTION_NOT_FOUND", "The contribution could not be found.")
		return contribution
	}

	fun getConfirmedTotal(campaignId: UUID): Long = contributionRepository.sumConfirmedByCampaign(campaignId)

	fun getConfirmedCount(campaignId: UUID): Long = contributionRepository.countConfirmedForCampaign(campaignId)

	fun listConfirmed(organizationId: UUID, campaignId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Contribution> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val campaign = campaignRepository.findById(campaignId, organizationId)
			?: throw NotFoundException("CAMPAIGN_NOT_FOUND", "The campaign could not be found.")
		return contributionRepository.listConfirmedForCampaign(campaign.id, offset, limit)
	}
}

data class ContributionCheckout(val contributionId: UUID, val checkoutUrl: String)
