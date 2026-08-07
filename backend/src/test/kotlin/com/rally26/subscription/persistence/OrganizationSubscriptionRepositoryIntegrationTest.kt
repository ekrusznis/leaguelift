package com.rally26.subscription.persistence

import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.subscription.domain.OrganizationSubscriptionStatus
import com.rally26.testsupport.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrganizationSubscriptionRepositoryIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var subscriptionRepository: OrganizationSubscriptionRepository

    @Test
    fun `payment recovery timestamps and platform visibility persist against real postgres`() {
        val token = UUID.randomUUID().toString().take(8)
        val organization =
            organizationRepository.insert(
                name = "Billing Integration $token",
                slug = "billing-$token",
                organizationType = OrganizationType.TRAVEL_CLUB,
            )
        val subscription = subscriptionRepository.insert(organization.id, "FOUNDING_CLUB")
        subscriptionRepository.saveCustomerId(subscription.id, "cus_$token")
        subscriptionRepository.syncExternalState(
            id = subscription.id,
            status = OrganizationSubscriptionStatus.ACTIVE,
            customerId = "cus_$token",
            subscriptionId = "sub_$token",
            cancelAtPeriodEnd = true,
        )

        subscriptionRepository.markPaymentFailure(subscription.id)
        val failed = assertNotNull(subscriptionRepository.findById(subscription.id))
        assertEquals(OrganizationSubscriptionStatus.PAST_DUE, failed.status)
        assertNotNull(failed.lastPaymentFailureAt)
        assertTrue(failed.cancelAtPeriodEnd)

        subscriptionRepository.markPaymentSuccess(subscription.id)
        val recoveredPayment = assertNotNull(subscriptionRepository.findById(subscription.id))
        assertNotNull(recoveredPayment.lastPaymentSuccessAt)
        // Payment success is observable, but subscription status remains webhook-authoritative.
        assertEquals(OrganizationSubscriptionStatus.PAST_DUE, recoveredPayment.status)

        val rows = subscriptionRepository.listForPlatformAdmin(organization.name, "PAST_DUE", 0, 25)
        assertEquals(1, rows.size)
        assertEquals(organization.id, rows.single().organizationId)
        assertEquals("FOUNDING_CLUB", rows.single().planCode)
        assertTrue(rows.single().hasStripeCustomer)
        assertTrue(rows.single().hasStripeSubscription)
        assertTrue(rows.single().cancelAtPeriodEnd)
        assertEquals(1L, subscriptionRepository.countForPlatformAdmin(organization.name, "PAST_DUE"))
    }
}
