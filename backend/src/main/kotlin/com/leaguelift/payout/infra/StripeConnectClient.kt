package com.leaguelift.payout.infra

import com.stripe.StripeClient
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import org.springframework.stereotype.Component

data class StripeAccountStatus(val detailsSubmitted: Boolean, val chargesEnabled: Boolean, val payoutsEnabled: Boolean)

/**
 * Thin seam over the Stripe SDK so no other file in the payout module imports Stripe
 * types directly (mirrors `media/infra/SpacesClient.kt`'s AWS SDK seam). Express
 * accounts only, onboarding only — no charge/transfer/payout-execution calls exist
 * here, matching this slice's scope (ADR-005).
 */
@Component
class StripeConnectClient(private val stripeClient: StripeClient) {

	fun createExpressAccount(): String {
		val account = stripeClient.accounts().create(
			AccountCreateParams.builder()
				.setType(AccountCreateParams.Type.EXPRESS)
				.build(),
		)
		return account.id
	}

	fun createOnboardingLink(stripeAccountId: String, refreshUrl: String, returnUrl: String): String {
		val link = stripeClient.accountLinks().create(
			AccountLinkCreateParams.builder()
				.setAccount(stripeAccountId)
				.setRefreshUrl(refreshUrl)
				.setReturnUrl(returnUrl)
				.setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
				.build(),
		)
		return link.url
	}

	fun retrieveAccountStatus(stripeAccountId: String): StripeAccountStatus {
		val account = stripeClient.accounts().retrieve(stripeAccountId)
		return StripeAccountStatus(
			detailsSubmitted = account.detailsSubmitted ?: false,
			chargesEnabled = account.chargesEnabled ?: false,
			payoutsEnabled = account.payoutsEnabled ?: false,
		)
	}
}
