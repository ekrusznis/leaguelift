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
    }

    @Test
    fun `active activates and definitive terminal states suspend`() {
        assertEquals(OrganizationAccessDecision.ACTIVATE, organizationAccessDecision(OrganizationSubscriptionStatus.ACTIVE))
        assertEquals(OrganizationAccessDecision.ACTIVATE, organizationAccessDecision(OrganizationSubscriptionStatus.TRIALING))
        assertEquals(OrganizationAccessDecision.SUSPEND, organizationAccessDecision(OrganizationSubscriptionStatus.CANCELED))
        assertEquals(OrganizationAccessDecision.SUSPEND, organizationAccessDecision(OrganizationSubscriptionStatus.INCOMPLETE))
    }
}
