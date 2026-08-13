package com.rally26.subscription.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
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

class SubscriptionTrialEndingEmailHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val objectMapper = jacksonObjectMapper()
    private val handler = SubscriptionTrialEndingEmailHandler(emailProvider, ResendTemplateProperties(), FrontendProperties(), objectMapper)

    private fun eventWithPayload(payload: OrganizationBillingTrialEndingPayload): OutboxEvent {
        val now = Instant.now()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "organization_subscription",
            aggregateId = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            eventType = "organization_subscription.trial_ending",
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
    fun `emails the owner with the real trial end date`() {
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(
            eventWithPayload(OrganizationBillingTrialEndingPayload("Riverside Youth Sports", listOf("owner@example.test"), "2026-08-25")),
        )

        verify(exactly = 1) { emailProvider.send(any()) }
        assertTrue(messageSlot.captured.subject.contains("2026-08-25"))
        assertTrue(messageSlot.captured.body.contains("2026-08-25"))
    }
}
