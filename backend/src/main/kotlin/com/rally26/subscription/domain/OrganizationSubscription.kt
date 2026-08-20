package com.rally26.subscription.domain

import java.time.Instant
import java.util.UUID

enum class OrganizationSubscriptionStatus {
    CHECKOUT_PENDING,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    INCOMPLETE,
}

enum class OrganizationAccessDecision {
    ACTIVATE,
    KEEP_CURRENT,
    SUSPEND,
}

enum class BillingRecoveryState {
    CHECKOUT_REQUIRED,
    CURRENT,
    PAYMENT_ACTION_REQUIRED,
    ENDED,
}

fun organizationAccessDecision(status: OrganizationSubscriptionStatus): OrganizationAccessDecision =
    when (status) {
        OrganizationSubscriptionStatus.TRIALING,
        OrganizationSubscriptionStatus.ACTIVE,
        -> OrganizationAccessDecision.ACTIVATE
        OrganizationSubscriptionStatus.CANCELED,
        OrganizationSubscriptionStatus.INCOMPLETE,
        -> OrganizationAccessDecision.SUSPEND
        OrganizationSubscriptionStatus.PAST_DUE,
        OrganizationSubscriptionStatus.CHECKOUT_PENDING,
        -> OrganizationAccessDecision.KEEP_CURRENT
    }

fun billingRecoveryState(status: OrganizationSubscriptionStatus): BillingRecoveryState =
    when (status) {
        OrganizationSubscriptionStatus.TRIALING,
        OrganizationSubscriptionStatus.ACTIVE,
        -> BillingRecoveryState.CURRENT
        OrganizationSubscriptionStatus.PAST_DUE -> BillingRecoveryState.PAYMENT_ACTION_REQUIRED
        OrganizationSubscriptionStatus.CANCELED -> BillingRecoveryState.ENDED
        OrganizationSubscriptionStatus.CHECKOUT_PENDING,
        OrganizationSubscriptionStatus.INCOMPLETE,
        -> BillingRecoveryState.CHECKOUT_REQUIRED
    }

data class OrganizationSubscription(
    val id: UUID,
    val organizationId: UUID,
    val planCode: String,
    val status: OrganizationSubscriptionStatus,
    val stripeCustomerId: String?,
    val stripeSubscriptionId: String?,
    val stripeCheckoutSessionId: String?,
    val checkoutGeneration: Int,
    val lastPaymentFailureAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    // Appended with a default so older positional test fixtures remain source-compatible.
    val lastPaymentSuccessAt: Instant? = null,
    val cancelAtPeriodEnd: Boolean = false,
    // Phase 45/46 additions (FREE tier + in-app upgrade/downgrade) — defaulted so
    // older positional test fixtures remain source-compatible.
    val planChangeGeneration: Int = 0,
    /** Set only while a downgrade-to-this-plan is scheduled to take effect when the current Stripe period ends (owner-initiated, via [com.rally26.subscription.application.OrganizationPlanChangeService]) — distinct from a genuine full cancellation through the Stripe Billing Portal, which never sets this. */
    val downgradeToPlanCode: String? = null,
    val currentPeriodEnd: Instant? = null,
)

data class SubscriptionPlan(
    val code: String,
    val name: String,
    val description: String,
    val amountMinor: Long?,
    val currency: String?,
    val billingInterval: String?,
    val contactOnly: Boolean,
    val active: Boolean,
    val stripeProductId: String?,
    val stripePriceId: String?,
    // Appended with a default so older positional test fixtures remain source-compatible.
    // The stable, non-price identifier for "does this plan need Stripe Checkout" — see
    // V94__free_subscription_tier.sql. Never infer this from amount_minor == 0.
    val requiresCheckout: Boolean = true,
)

data class PlatformOrganizationSubscription(
    val organizationId: UUID,
    val organizationName: String,
    val organizationStatus: String,
    val planCode: String?,
    val planName: String?,
    val amountMinor: Long?,
    val currency: String?,
    val status: OrganizationSubscriptionStatus?,
    val lastPaymentFailureAt: Instant?,
    val lastPaymentSuccessAt: Instant?,
    val hasStripeCustomer: Boolean,
    val hasStripeSubscription: Boolean,
    val cancelAtPeriodEnd: Boolean,
    val updatedAt: Instant,
)

fun stripeSubscriptionStatus(status: String?): OrganizationSubscriptionStatus =
    when (status?.lowercase()) {
        "trialing" -> OrganizationSubscriptionStatus.TRIALING
        "active" -> OrganizationSubscriptionStatus.ACTIVE
        "past_due", "unpaid", "paused" -> OrganizationSubscriptionStatus.PAST_DUE
        "canceled" -> OrganizationSubscriptionStatus.CANCELED
        "incomplete", "incomplete_expired" -> OrganizationSubscriptionStatus.INCOMPLETE
        else -> OrganizationSubscriptionStatus.INCOMPLETE
    }
