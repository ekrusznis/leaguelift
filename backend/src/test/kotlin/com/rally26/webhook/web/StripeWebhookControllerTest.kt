package com.rally26.webhook.web

import com.rally26.config.StripeProperties
import com.rally26.fee.application.FeeService
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.order.application.OrderService
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderStatus
import com.rally26.payout.application.PayoutAccountService
import com.rally26.payout.infra.StripeAccountStatus
import com.rally26.payout.infra.StripeConnectClient
import com.rally26.sponsorship.application.SponsorshipService
import com.rally26.sponsorship.domain.Sponsorship
import com.rally26.sponsorship.domain.SponsorshipStatus
import com.rally26.webhook.domain.WebhookEvent
import com.rally26.webhook.domain.WebhookProcessingStatus
import com.rally26.webhook.persistence.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unlike `fundraising/integration/ContributionIntegrationTest.kt` (which calls
 * [ContributionService.confirmFromWebhook] directly to exercise the DB flow), this
 * test exercises what's unique to the webhook *transport* layer: real Stripe
 * signature verification (via the actual Stripe SDK, no mocking of that part) and
 * the (provider, externalEventId) idempotency guard. [ContributionService] itself
 * is mocked here — its own behavior is covered elsewhere.
 */
class StripeWebhookControllerTest {
    private val webhookSecret = "whsec_test_secret"
    private val stripeProperties = StripeProperties(secretKey = "sk_test_unused", webhookSecret = webhookSecret)
    private val webhookEventRepository = mockk<WebhookEventRepository>()
    private val contributionService = mockk<ContributionService>()
    private val orderService = mockk<OrderService>()
    private val sponsorshipService = mockk<SponsorshipService>()
    private val feeService = mockk<FeeService>()
    private val controller =
        StripeWebhookController(
            stripeProperties,
            webhookEventRepository,
            contributionService,
            orderService,
            sponsorshipService,
            feeService,
        )

    @Test
    fun `an invalid signature is rejected with 400 and never reaches the repository or contribution service`() {
        val payload = checkoutSessionCompletedPayload("evt_bad_sig", "cs_test_1", "paid")

        val response = controller.receive(payload, "t=123,v1=not-a-real-signature")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(exactly = 0) { webhookEventRepository.findExisting(any(), any()) }
        verify(exactly = 0) { contributionService.confirmFromWebhook(any(), any(), any()) }
    }

    @Test
    fun `a valid checkout_session_completed event confirms the contribution and records a PROCESSED webhook_event`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = checkoutSessionCompletedPayload(eventId, "cs_test_2", "paid")
        val signature = sign(payload, webhookSecret)
        val contribution = sampleContribution()
        every { webhookEventRepository.findExisting("stripe", eventId) } returns null
        every { contributionService.confirmFromWebhook("cs_test_2", "paid", null) } returns contribution
        every {
            webhookEventRepository.insert(
                "stripe",
                eventId,
                "checkout.session.completed",
                payload,
                any(),
                true,
                WebhookProcessingStatus.PROCESSED,
                "contribution",
                contribution.id,
                null,
            )
        } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { contributionService.confirmFromWebhook("cs_test_2", "paid", null) }
    }

    @Test
    fun `a replayed event (already recorded) is a no-op — contribution service is not called again`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = checkoutSessionCompletedPayload(eventId, "cs_test_3", "paid")
        val signature = sign(payload, webhookSecret)
        every { webhookEventRepository.findExisting("stripe", eventId) } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { contributionService.confirmFromWebhook(any(), any(), any()) }
    }

    @Test
    fun `an unrecognized event type is stored as IGNORED without touching the contribution service`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload =
            """{"id":"$eventId","object":"event","api_version":"2025-03-31.basil",""" +
                """"type":"customer.created","data":{"object":{"id":"cus_test_1","object":"customer"}}}"""
        val signature = sign(payload, webhookSecret)
        every { webhookEventRepository.findExisting("stripe", eventId) } returns null
        every {
            webhookEventRepository.insert(
                "stripe",
                eventId,
                "customer.created",
                payload,
                any(),
                true,
                WebhookProcessingStatus.IGNORED,
                null,
                null,
                null,
            )
        } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { contributionService.confirmFromWebhook(any(), any(), any()) }
    }

    @Test
    fun `a checkout_session_completed event with an orderId in metadata dispatches to OrderService, not ContributionService`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val orderId = UUID.randomUUID()
        val payload = orderCheckoutSessionCompletedPayload(eventId, "cs_test_4", "paid", orderId)
        val signature = sign(payload, webhookSecret)
        val order = sampleOrder(orderId)
        every { webhookEventRepository.findExisting("stripe", eventId) } returns null
        every { orderService.confirmFromWebhook("cs_test_4", "paid", null, null) } returns order
        every {
            webhookEventRepository.insert(
                "stripe",
                eventId,
                "checkout.session.completed",
                payload,
                any(),
                true,
                WebhookProcessingStatus.PROCESSED,
                "order",
                order.id,
                null,
            )
        } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { orderService.confirmFromWebhook("cs_test_4", "paid", null, null) }
        verify(exactly = 0) { contributionService.confirmFromWebhook(any(), any(), any()) }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `a checkout_session_completed event with a sponsorshipId in metadata dispatches to SponsorshipService, not ContributionService or OrderService`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val sponsorshipId = UUID.randomUUID()
        val payload = sponsorshipCheckoutSessionCompletedPayload(eventId, "cs_test_5", "paid", sponsorshipId)
        val signature = sign(payload, webhookSecret)
        val sponsorship = sampleSponsorship(sponsorshipId)
        every { webhookEventRepository.findExisting("stripe", eventId) } returns null
        every { sponsorshipService.confirmFromWebhook("cs_test_5", "paid", null) } returns sponsorship
        every {
            webhookEventRepository.insert(
                "stripe",
                eventId,
                "checkout.session.completed",
                payload,
                any(),
                true,
                WebhookProcessingStatus.PROCESSED,
                "sponsorship",
                sponsorship.id,
                null,
            )
        } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { sponsorshipService.confirmFromWebhook("cs_test_5", "paid", null) }
        verify(exactly = 0) { contributionService.confirmFromWebhook(any(), any(), any()) }
        verify(exactly = 0) { orderService.confirmFromWebhook(any(), any(), any(), any()) }
    }

    @Test
    fun `an account_updated event syncs the connected account's status through PayoutAccountService`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = accountUpdatedPayload(eventId, "acct_test_1")
        val signature = sign(payload, webhookSecret)
        val payoutAccountService = mockk<PayoutAccountService>()
        val stripeConnectClient = mockk<StripeConnectClient>()
        val controllerWithPayout =
            StripeWebhookController(
                stripeProperties,
                webhookEventRepository,
                contributionService,
                orderService,
                sponsorshipService,
                feeService,
                payoutAccountService = payoutAccountService,
                stripeConnectClient = stripeConnectClient,
            )
        val accountId = UUID.randomUUID()
        every { webhookEventRepository.findExisting("stripe", eventId) } returns null
        every { stripeConnectClient.statusFrom(any()) } returns StripeAccountStatus(true, false, false, "requirements.past_due")
        every { payoutAccountService.syncFromWebhook("acct_test_1", any()) } returns accountId
        every {
            webhookEventRepository.insert(
                "stripe",
                eventId,
                "account.updated",
                payload,
                any(),
                true,
                WebhookProcessingStatus.PROCESSED,
                "organization_payout_account",
                accountId,
                null,
            )
        } returns sampleWebhookEvent()

        val response = controllerWithPayout.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { payoutAccountService.syncFromWebhook("acct_test_1", any()) }
    }

    private fun accountUpdatedPayload(
        eventId: String,
        stripeAccountId: String,
    ): String =
        """{"id":"$eventId","object":"event","api_version":"2025-03-31.basil","type":"account.updated","data":{"object":{""" +
            """"id":"$stripeAccountId","object":"account","details_submitted":true,"charges_enabled":false,"payouts_enabled":false,""" +
            """"requirements":{"disabled_reason":"requirements.past_due"}}}}"""

    private fun checkoutSessionCompletedPayload(
        eventId: String,
        sessionId: String,
        paymentStatus: String,
    ): String =
        """{"id":"$eventId","object":"event","api_version":"2025-03-31.basil","type":"checkout.session.completed","data":{"object":{"id":"$sessionId","object":"checkout.session","payment_status":"$paymentStatus","metadata":{}}}}"""

    private fun orderCheckoutSessionCompletedPayload(
        eventId: String,
        sessionId: String,
        paymentStatus: String,
        orderId: UUID,
    ): String =
        """{"id":"$eventId","object":"event","api_version":"2025-03-31.basil","type":"checkout.session.completed","data":{"object":{"id":"$sessionId","object":"checkout.session","payment_status":"$paymentStatus","metadata":{"orderId":"$orderId"}}}}"""

    private fun sponsorshipCheckoutSessionCompletedPayload(
        eventId: String,
        sessionId: String,
        paymentStatus: String,
        sponsorshipId: UUID,
    ): String =
        """{"id":"$eventId","object":"event","api_version":"2025-03-31.basil","type":"checkout.session.completed","data":{"object":{"id":"$sessionId","object":"checkout.session","payment_status":"$paymentStatus","metadata":{"sponsorshipId":"$sponsorshipId"}}}}"""

    private fun sign(
        payload: String,
        secret: String,
    ): String {
        val timestamp = System.currentTimeMillis() / 1000
        val signedPayload = "$timestamp.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(signedPayload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$signature"
    }

    private fun sampleContribution() =
        Contribution(
            id = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            campaignId = UUID.randomUUID(),
            amountMinor = 5000L,
            currency = "USD",
            supporterName = "Jane Doe",
            isAnonymous = false,
            supporterEmail = null,
            status = ContributionStatus.CONFIRMED,
            stripeCheckoutSessionId = "cs_test_2",
            stripePaymentIntentId = null,
            confirmedAt = Instant.now(),
            refundedAt = null,
            createdAt = Instant.now(),
        )

    private fun sampleOrder(orderId: UUID) =
        Order(
            id = orderId,
            organizationId = UUID.randomUUID(),
            storeId = UUID.randomUUID(),
            status = OrderStatus.CONFIRMED,
            currency = "USD",
            supporterName = "Jane Doe",
            supporterEmail = null,
            shippingAddress = null,
            stripeCheckoutSessionId = "cs_test_4",
            stripePaymentIntentId = null,
            confirmedAt = Instant.now(),
            refundedAt = null,
            createdAt = Instant.now(),
        )

    private fun sampleSponsorship(sponsorshipId: UUID) =
        Sponsorship(
            id = sponsorshipId,
            organizationId = UUID.randomUUID(),
            packageId = UUID.randomUUID(),
            sponsorId = UUID.randomUUID(),
            amountMinor = 25_000L,
            currency = "USD",
            status = SponsorshipStatus.CONFIRMED,
            stripeCheckoutSessionId = "cs_test_5",
            stripePaymentIntentId = null,
            confirmedAt = Instant.now(),
            createdAt = Instant.now(),
        )

    private fun sampleWebhookEvent() =
        WebhookEvent(
            id = UUID.randomUUID(),
            provider = "stripe",
            externalEventId = "evt_x",
            eventType = "checkout.session.completed",
            payload = "{}",
            payloadHash = "hash",
            signatureVerified = true,
            processingStatus = WebhookProcessingStatus.PROCESSED,
            attemptCount = 1,
            relatedEntityType = null,
            relatedEntityId = null,
            receivedAt = Instant.now(),
            processedAt = Instant.now(),
            lastError = null,
        )
}
