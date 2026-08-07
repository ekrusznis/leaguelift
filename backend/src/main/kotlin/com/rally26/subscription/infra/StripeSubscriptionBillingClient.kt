package com.rally26.subscription.infra

import com.rally26.subscription.domain.SubscriptionPlan
import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PriceCreateParams
import com.stripe.param.ProductCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class StripePlanAssets(
    val productId: String,
    val priceId: String,
)

data class StripeSubscriptionCheckout(
    val sessionId: String,
    val checkoutUrl: String,
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
