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
