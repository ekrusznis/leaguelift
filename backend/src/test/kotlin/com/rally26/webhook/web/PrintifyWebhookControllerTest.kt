package com.rally26.webhook.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.config.PrintifyProperties
import com.rally26.order.application.FulfillmentOperationsService
import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentSource
import com.rally26.order.domain.FulfillmentStatus
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
 * Mirrors `StripeWebhookControllerTest`'s shape: exercises the webhook *transport*
 * layer (real HMAC signature verification, the (provider, externalEventId)
 * idempotency guard, the enabled/configured gate) — `FulfillmentOperationsService`
 * itself is mocked here, its own behavior is covered by
 * `FulfillmentOperationsServiceTest`.
 */
class PrintifyWebhookControllerTest {
    private val webhookSecret = "test_printify_webhook_secret"
    private val printifyProperties =
        PrintifyProperties(apiToken = "test-token", shopId = "shop_123", webhookSecret = webhookSecret, webhookEnabled = true)
    private val webhookEventRepository = mockk<WebhookEventRepository>()
    private val fulfillmentOperationsService = mockk<FulfillmentOperationsService>()
    private val controller =
        PrintifyWebhookController(printifyProperties, webhookEventRepository, fulfillmentOperationsService, jacksonObjectMapper())

    @Test
    fun `an invalid signature is rejected with 400 and never reaches the repository or fulfillment service`() {
        val payload = shipmentEventPayload("evt_bad_sig", "printify_order_1")

        val response = controller.receive(payload, "not-a-real-signature")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(exactly = 0) { webhookEventRepository.findExisting(any(), any()) }
        verify(exactly = 0) { fulfillmentOperationsService.applyProviderStatusUpdate(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `disabled or unconfigured webhook returns 404 before touching signature verification`() {
        val disabledController =
            PrintifyWebhookController(
                PrintifyProperties(apiToken = "test-token", shopId = "shop_123", webhookSecret = "", webhookEnabled = false),
                webhookEventRepository,
                fulfillmentOperationsService,
                jacksonObjectMapper(),
            )
        val payload = shipmentEventPayload("evt_1", "printify_order_1")

        val response = disabledController.receive(payload, "anything")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 0) { webhookEventRepository.findExisting(any(), any()) }
    }

    @Test
    fun `a valid order_shipment_created event updates the fulfillment and records a PROCESSED webhook_event`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = shipmentEventPayload(eventId, "printify_order_1")
        val signature = sign(payload, webhookSecret)
        val fulfillment = sampleFulfillment()
        every { webhookEventRepository.findExisting("printify", eventId) } returns null
        every {
            fulfillmentOperationsService.applyProviderStatusUpdate("printify_order_1", FulfillmentStatus.SHIPPED, null, null, null, any())
        } returns fulfillment
        every {
            webhookEventRepository.insert(
                "printify",
                eventId,
                "order:shipment:created",
                payload,
                any(),
                true,
                WebhookProcessingStatus.PROCESSED,
                "fulfillment",
                fulfillment.id,
                null,
            )
        } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) {
            fulfillmentOperationsService.applyProviderStatusUpdate("printify_order_1", FulfillmentStatus.SHIPPED, null, null, null, any())
        }
    }

    @Test
    fun `a replayed event (already recorded) is a no-op — fulfillment service is not called again`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = shipmentEventPayload(eventId, "printify_order_1")
        val signature = sign(payload, webhookSecret)
        every { webhookEventRepository.findExisting("printify", eventId) } returns sampleWebhookEvent()

        val response = controller.receive(payload, signature)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 0) { fulfillmentOperationsService.applyProviderStatusUpdate(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an unrecognized event type is stored as IGNORED without touching the fulfillment service`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = """{"id":"$eventId","type":"shop:disconnected","resource":{"id":"shop_123","type":"shop"}}"""
        val signature = sign(payload, webhookSecret)
        every { webhookEventRepository.findExisting("printify", eventId) } returns null
        every {
            webhookEventRepository.insert(
                "printify",
                eventId,
                "shop:disconnected",
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
        verify(exactly = 0) { fulfillmentOperationsService.applyProviderStatusUpdate(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an unmatched printify order id is stored as IGNORED`() {
        val eventId = "evt_${UUID.randomUUID()}"
        val payload = shipmentEventPayload(eventId, "printify_order_unknown")
        val signature = sign(payload, webhookSecret)
        every { webhookEventRepository.findExisting("printify", eventId) } returns null
        every {
            fulfillmentOperationsService.applyProviderStatusUpdate(
                "printify_order_unknown",
                FulfillmentStatus.SHIPPED,
                null,
                null,
                null,
                any(),
            )
        } returns null
        every {
            webhookEventRepository.insert(
                "printify",
                eventId,
                "order:shipment:created",
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
    }

    private fun shipmentEventPayload(
        eventId: String,
        printifyOrderId: String,
    ): String = """{"id":"$eventId","type":"order:shipment:created","resource":{"id":"$printifyOrderId","type":"order"}}"""

    private fun sign(
        payload: String,
        secret: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun sampleFulfillment() =
        Fulfillment(
            id = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            source = FulfillmentSource.PRINTIFY,
            status = FulfillmentStatus.SHIPPED,
            printifyOrderId = "printify_order_1",
            manualVendorId = null,
            manualVendorName = null,
            vendorOrderReference = null,
            carrier = null,
            trackingNumber = null,
            trackingUrl = null,
            internalNotes = null,
            attentionReason = null,
            lastError = null,
            statusChangedAt = Instant.now(),
            shippedAt = Instant.now(),
            deliveredAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleWebhookEvent() =
        WebhookEvent(
            id = UUID.randomUUID(),
            provider = "printify",
            externalEventId = "evt_x",
            eventType = "order:shipment:created",
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
