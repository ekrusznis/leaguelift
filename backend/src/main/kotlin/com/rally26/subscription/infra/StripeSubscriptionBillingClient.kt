package com.rally26.subscription.infra

import com.rally26.subscription.domain.SubscriptionPlan
import com.stripe.StripeClient
import com.stripe.model.Subscription
import com.stripe.net.RequestOptions
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PriceCreateParams
import com.stripe.param.ProductCreateParams
import com.stripe.param.SubscriptionUpdateParams
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class StripePlanAssets(
    val productId: String,
    val priceId: String,
)

data class StripeSubscriptionCheckout(
    val sessionId: String,
    val checkoutUrl: String,
)

data class StripeSubscriptionUpdateResult(
    val subscriptionId: String,
    val status: String,
    val currentPeriodEnd: Instant?,
)

@Component
class StripeSubscriptionBillingClient(
    private val stripeClient: StripeClient,
) {
    fun ensurePlanAssets(plan: SubscriptionPlan): StripePlanAssets {
        require(!plan.contactOnly) { "Contact-only plans do not have Stripe billing assets." }
        val amountMinor = requireNotNull(plan.amountMinor)
        val currency = requireNotNull(plan.currency)
        require(plan.billingInterval == "MONTHLY") { "Unsupported billing interval: ${plan.billingInterval}" }

        val productId =
            plan.stripeProductId
                ?: stripeClient
                    .products()
                    .create(
                        ProductCreateParams
                            .builder()
                            .setName("Rally26 ${plan.name}")
                            .setDescription(plan.description)
                            .putMetadata("rally26PlanCode", plan.code)
                            .build(),
                        RequestOptions
                            .builder()
                            .setIdempotencyKey("rally26-plan-product-${plan.code.lowercase()}-v1")
                            .build(),
                    ).id

        val priceId =
            plan.stripePriceId
                ?: stripeClient
                    .prices()
                    .create(
                        PriceCreateParams
                            .builder()
                            .setCurrency(currency.lowercase())
                            .setUnitAmount(amountMinor)
                            .setProduct(productId)
                            .setLookupKey("rally26_${plan.code.lowercase()}_${currency.lowercase()}_${amountMinor}_monthly")
                            .setRecurring(
                                PriceCreateParams.Recurring
                                    .builder()
                                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                    .build(),
                            ).putMetadata("rally26PlanCode", plan.code)
                            .build(),
                        RequestOptions
                            .builder()
                            .setIdempotencyKey("rally26-plan-price-${plan.code.lowercase()}-$amountMinor-${currency.lowercase()}-v1")
                            .build(),
                    ).id

        return StripePlanAssets(productId = productId, priceId = priceId)
    }

    fun createCustomer(
        organizationId: UUID,
        organizationName: String,
        ownerEmail: String,
    ): String =
        stripeClient
            .customers()
            .create(
                CustomerCreateParams
                    .builder()
                    .setName(organizationName)
                    .setEmail(ownerEmail)
                    .putMetadata("rally26OrganizationId", organizationId.toString())
                    .build(),
                RequestOptions
                    .builder()
                    .setIdempotencyKey("rally26-subscription-customer-$organizationId")
                    .build(),
            ).id

    fun retrieveOpenCheckout(sessionId: String): StripeSubscriptionCheckout? {
        val session = stripeClient.checkout().sessions().retrieve(sessionId)
        if (session.status != "open" || session.url.isNullOrBlank()) return null
        return StripeSubscriptionCheckout(sessionId = session.id, checkoutUrl = session.url)
    }

    fun createSubscriptionCheckout(
        localSubscriptionId: UUID,
        organizationId: UUID,
        customerId: String,
        priceId: String,
        planCode: String,
        successUrl: String,
        cancelUrl: String,
        generation: Int,
    ): StripeSubscriptionCheckout {
        val params =
            SessionCreateParams
                .builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setClientReferenceId(organizationId.toString())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("organizationSubscriptionId", localSubscriptionId.toString())
                .putMetadata("organizationId", organizationId.toString())
                .putMetadata("planCode", planCode)
                .setSubscriptionData(
                    SessionCreateParams.SubscriptionData
                        .builder()
                        .putMetadata("organizationSubscriptionId", localSubscriptionId.toString())
                        .putMetadata("organizationId", organizationId.toString())
                        .putMetadata("planCode", planCode)
                        .build(),
                ).addLineItem(
                    SessionCreateParams.LineItem
                        .builder()
                        .setQuantity(1L)
                        .setPrice(priceId)
                        .build(),
                ).build()
        val session =
            stripeClient
                .checkout()
                .sessions()
                .create(
                    params,
                    RequestOptions
                        .builder()
                        .setIdempotencyKey("rally26-subscription-checkout-$localSubscriptionId-$generation")
                        .build(),
                )
        return StripeSubscriptionCheckout(sessionId = session.id, checkoutUrl = session.url)
    }

    /** Real-time paid-tier price change (e.g. Starter&lt;-&gt;Club), prorated immediately — no new Checkout session, unlike [createSubscriptionCheckout]. Single-line-item subscriptions only, matching [createSubscriptionCheckout]'s `addLineItem(quantity=1)`. */
    fun updateSubscriptionPrice(
        subscriptionId: String,
        newPriceId: String,
        planCode: String,
        generation: Int,
    ): StripeSubscriptionUpdateResult {
        val existing = stripeClient.subscriptions().retrieve(subscriptionId)
        val itemId =
            existing.items.data
                .first()
                .id
        val updated =
            stripeClient
                .subscriptions()
                .update(
                    subscriptionId,
                    SubscriptionUpdateParams
                        .builder()
                        .addItem(
                            SubscriptionUpdateParams.Item
                                .builder()
                                .setId(itemId)
                                .setPrice(newPriceId)
                                .build(),
                        ).setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                        .putMetadata("planCode", planCode)
                        .build(),
                    RequestOptions
                        .builder()
                        .setIdempotencyKey("rally26-subscription-planchange-$subscriptionId-$generation")
                        .build(),
                )
        return updated.toResult()
    }

    /** Downgrade-to-FREE completes when Stripe's current billing period actually ends, not immediately — the owner keeps paid-tier access through what they already paid for. Not an immediate [cancelSubscription]. */
    fun scheduleCancelAtPeriodEnd(
        subscriptionId: String,
        generation: Int,
    ): StripeSubscriptionUpdateResult {
        val updated =
            stripeClient
                .subscriptions()
                .update(
                    subscriptionId,
                    SubscriptionUpdateParams
                        .builder()
                        .setCancelAtPeriodEnd(true)
                        .build(),
                    RequestOptions
                        .builder()
                        .setIdempotencyKey("rally26-subscription-scheduledowngrade-$subscriptionId-$generation")
                        .build(),
                )
        return updated.toResult()
    }

    /** Real immediate cancel — kept for completeness/future use (e.g. a platform-admin override); not called by the downgrade-to-FREE path, which uses [scheduleCancelAtPeriodEnd] instead. */
    fun cancelSubscription(subscriptionId: String): StripeSubscriptionUpdateResult =
        stripeClient.subscriptions().cancel(subscriptionId).toResult()

    private fun Subscription.toResult() =
        StripeSubscriptionUpdateResult(
            subscriptionId = id,
            status = status,
            currentPeriodEnd =
                items.data
                    .firstOrNull()
                    ?.currentPeriodEnd
                    ?.let(Instant::ofEpochSecond),
        )

    fun createBillingPortalSession(
        customerId: String,
        returnUrl: String,
    ): String {
        val params =
            com.stripe.param.billingportal.SessionCreateParams
                .builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build()
        return stripeClient
            .billingPortal()
            .sessions()
            .create(params)
            .url
    }
}
