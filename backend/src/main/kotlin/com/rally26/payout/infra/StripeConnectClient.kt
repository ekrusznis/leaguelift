package com.rally26.payout.infra

import com.stripe.StripeClient
import com.stripe.model.Account
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import com.stripe.param.TransferCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class StripeAccountStatus(
    val detailsSubmitted: Boolean,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val disabledReason: String? = null,
)

/**
 * Thin seam over the Stripe SDK so no other file in the payout module imports Stripe
 * types directly (mirrors `media/infra/SpacesClient.kt`'s AWS SDK seam). Express
 * account onboarding (ADR-005) plus, as of Phase 5 slice 1 (ADR-017), the
 * "separate charges and transfers" model's other half — moving money already sitting
 * in Rally26's Stripe balance to an org's connected account. No `sourceTransaction`
 * is set: these are aggregate, multi-source transfers, and test-mode balance is
 * instantly available, so precise per-charge availability linkage is deliberately
 * deferred to the Live Payments Launch phase (DESIGN-DOC.md section 14.4).
 */
@Component
class StripeConnectClient(
    private val stripeClient: StripeClient,
) {
    fun createExpressAccount(): String {
        val account =
            stripeClient.accounts().create(
                AccountCreateParams
                    .builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .build(),
            )
        return account.id
    }

    fun createOnboardingLink(
        stripeAccountId: String,
        refreshUrl: String,
        returnUrl: String,
    ): String {
        val link =
            stripeClient.accountLinks().create(
                AccountLinkCreateParams
                    .builder()
                    .setAccount(stripeAccountId)
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build(),
            )
        return link.url
    }

    fun retrieveAccountStatus(stripeAccountId: String): StripeAccountStatus = statusFrom(stripeClient.accounts().retrieve(stripeAccountId))

    /**
     * Same field mapping the manual [retrieveAccountStatus] pull uses, exposed so the
     * `account.updated` webhook (Phase 37.8, ADR-117) can map its already-deserialized
     * [Account] payload the identical way — keeps Stripe's `Account` model read in
     * exactly one place, matching this file's own "no other file interprets Stripe
     * fields directly" seam.
     */
    fun statusFrom(account: Account): StripeAccountStatus =
        StripeAccountStatus(
            detailsSubmitted = account.detailsSubmitted ?: false,
            chargesEnabled = account.chargesEnabled ?: false,
            payoutsEnabled = account.payoutsEnabled ?: false,
            disabledReason = account.requirements?.disabledReason,
        )

    fun createTransfer(
        destinationStripeAccountId: String,
        amountMinor: Long,
        currency: String,
    ): String {
        val transfer =
            stripeClient.transfers().create(
                TransferCreateParams
                    .builder()
                    .setAmount(amountMinor)
                    .setCurrency(currency.lowercase())
                    .setDestination(destinationStripeAccountId)
                    .setTransferGroup(UUID.randomUUID().toString())
                    .build(),
            )
        return transfer.id
    }
}
