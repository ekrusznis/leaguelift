package com.rally26.subscription.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class SubscriptionCanceledEmailHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val objectMapper = jacksonObjectMapper()
    private val handler = SubscriptionCanceledEmailHandler(emailProvider, objectMapper)

    private fun eventWithPayload(payload: OrganizationBillingLifecyclePayload): OutboxEvent {
        val now = Instant.now()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "organization_subscription",
            aggregateId = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            eventType = "organization_subscription.canceled",
            schemaVersion = 1,
            payload = objectMapper.writeValueAsString(payload),
            status = OutboxEventStatus.PROCESSING,
            attemptCount = 1,
            availableAt = now,
            processedAt = null,
            lastError = null,
            createdAt = now,
        )
    }

    @Test
    fun `emails the owner that the subscription has ended`() {
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(eventWithPayload(OrganizationBillingLifecyclePayload("Riverside Youth Sports", listOf("owner@example.test"))))

        verify(exactly = 1) { emailProvider.send(any()) }
        assertTrue(messageSlot.captured.subject.contains("ended"))
        assertTrue(messageSlot.captured.body.contains("canceled"))
    }
}
