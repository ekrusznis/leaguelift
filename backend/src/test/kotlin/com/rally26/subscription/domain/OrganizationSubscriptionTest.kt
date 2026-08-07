package com.rally26.subscription.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OrganizationSubscriptionTest {
    @Test
    fun `stripe statuses map to durable local states`() {
        assertEquals(OrganizationSubscriptionStatus.TRIALING, stripeSubscriptionStatus("trialing"))
        assertEquals(OrganizationSubscriptionStatus.ACTIVE, stripeSubscriptionStatus("active"))
        assertEquals(OrganizationSubscriptionStatus.PAST_DUE, stripeSubscriptionStatus("past_due"))
        assertEquals(OrganizationSubscriptionStatus.PAST_DUE, stripeSubscriptionStatus("unpaid"))
        assertEquals(OrganizationSubscriptionStatus.CANCELED, stripeSubscriptionStatus("canceled"))
        assertEquals(OrganizationSubscriptionStatus.INCOMPLETE, stripeSubscriptionStatus("incomplete_expired"))
    }

    @Test
    fun `past due records billing recovery without inventing an immediate lockout`() {
        assertEquals(
            OrganizationAccessDecision.KEEP_CURRENT,
            organizationAccessDecision(OrganizationSubscriptionStatus.PAST_DUE),
        )
        assertEquals(
            BillingRecoveryState.PAYMENT_ACTION_REQUIRED,
            billingRecoveryState(OrganizationSubscriptionStatus.PAST_DUE),
        )
    }

    @Test
    fun `active and trialing are current while definitive terminal states suspend`() {
        assertEquals(OrganizationAccessDecision.ACTIVATE, organizationAccessDecision(OrganizationSubscriptionStatus.ACTIVE))
        assertEquals(OrganizationAccessDecision.ACTIVATE, organizationAccessDecision(OrganizationSubscriptionStatus.TRIALING))
        assertEquals(BillingRecoveryState.CURRENT, billingRecoveryState(OrganizationSubscriptionStatus.ACTIVE))
        assertEquals(BillingRecoveryState.CURRENT, billingRecoveryState(OrganizationSubscriptionStatus.TRIALING))
        assertEquals(OrganizationAccessDecision.SUSPEND, organizationAccessDecision(OrganizationSubscriptionStatus.CANCELED))
        assertEquals(OrganizationAccessDecision.SUSPEND, organizationAccessDecision(OrganizationSubscriptionStatus.INCOMPLETE))
        assertEquals(BillingRecoveryState.ENDED, billingRecoveryState(OrganizationSubscriptionStatus.CANCELED))
        assertEquals(BillingRecoveryState.CHECKOUT_REQUIRED, billingRecoveryState(OrganizationSubscriptionStatus.INCOMPLETE))
    }

    @Test
    fun `checkout pending is not treated as paid or active`() {
        assertEquals(
            BillingRecoveryState.CHECKOUT_REQUIRED,
            billingRecoveryState(OrganizationSubscriptionStatus.CHECKOUT_PENDING),
        )
        assertEquals(
            OrganizationAccessDecision.KEEP_CURRENT,
            organizationAccessDecision(OrganizationSubscriptionStatus.CHECKOUT_PENDING),
        )
    }
}
