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
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionPaymentFailedEmailHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val objectMapper = jacksonObjectMapper()
    private val handler = SubscriptionPaymentFailedEmailHandler(emailProvider, objectMapper)

    private fun eventWithPayload(payload: OrganizationBillingLifecyclePayload): OutboxEvent {
        val now = Instant.now()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "organization_subscription",
            aggregateId = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            eventType = "organization_subscription.payment_failed",
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
    fun `emails every owner-or-administrator with the organization name`() {
        val messages = mutableListOf<EmailMessage>()
        every { emailProvider.send(capture(messages)) } just runs

        handler.handle(
            eventWithPayload(
                OrganizationBillingLifecyclePayload("Riverside Youth Sports", listOf("owner@example.test", "admin@example.test")),
            ),
        )

        verify(exactly = 2) { emailProvider.send(any()) }
        assertEquals(setOf("owner@example.test", "admin@example.test"), messages.map { it.to }.toSet())
        assertTrue(messages.all { it.subject.contains("Riverside Youth Sports") })
        assertTrue(messages.all { it.body.contains("couldn't process") })
    }
}
