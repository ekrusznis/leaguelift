package com.rally26.webhook.web

import com.rally26.config.StripeProperties
import com.rally26.fee.application.FeeService
import com.rally26.fundraising.application.ContributionService
import com.rally26.order.application.OrderService
import com.rally26.sponsorship.application.SponsorshipService
import com.rally26.subscription.application.OrganizationSubscriptionService
import com.rally26.webhook.domain.WebhookProcessingStatus
import com.rally26.webhook.persistence.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class StripeSubscriptionWebhookControllerTest {
    private val webhookSecret = "whsec_subscription_test"
    private val webhookRepository = mockk<WebhookEventRepository>(relaxed = true)
    private val contributionService = mockk<ContributionService>(relaxed = true)
    private val orderService = mockk<OrderService>(relaxed = true)
    private val sponsorshipService = mockk<SponsorshipService>(relaxed = true)
    private val feeService = mockk<FeeService>(relaxed = true)
    private val subscriptionService = mockk<OrganizationSubscriptionService>(relaxed = true)
    private val controller =
        StripeWebhookController(
            StripeProperties(secretKey = "sk_test_unused", webhookSecret = webhookSecret),
            webhookRepository,
            contributionService,
            orderService,
            sponsorshipService,
            feeService,
            organizationSubscriptionService = subscriptionService,
        )

    @Test
    fun `invoice paid dispatches to organization subscription billing and is recorded processed`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val customerId = "cus_${UUID.randomUUID().toString().take(8)}"
        val subscriptionId = UUID.randomUUID()
        val payload =
            """{"id":"$eventId","object":"event","api_version":"2025-03-31.basil","type":"invoice.paid","data":{"object":{"id":"in_test","object":"invoice","customer":"$customerId"}}}"""
        every { webhookRepository.findExisting("stripe", eventId) } returns null
        every { subscriptionService.handleInvoicePaid(any()) } returns subscriptionId

        val response = controller.receive(payload, sign(payload))

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { subscriptionService.handleInvoicePaid(match { it.customer == customerId }) }
        verify(exactly = 1) {
            webhookRepository.insert(
                provider = "stripe",
                externalEventId = eventId,
                eventType = "invoice.paid",
                payload = payload,
                payloadHash = any(),
                signatureVerified = true,
                processingStatus = WebhookProcessingStatus.PROCESSED,
                relatedEntityType = "organization_subscription",
                relatedEntityId = subscriptionId,
                lastError = null,
            )
        }
    }

    private fun sign(payload: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val signedPayload = "$timestamp.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(webhookSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(signedPayload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$signature"
    }
}
