package com.rally26.fundraising.application

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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContributionThankYouEmailHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val objectMapper = jacksonObjectMapper()
    private val handler = ContributionThankYouEmailHandler(emailProvider, ResendTemplateProperties(), FrontendProperties(), objectMapper)

    @Test
    fun `sends a thank-you email built from the payload`() {
        val now = Instant.now()
        val payload =
            objectMapper.writeValueAsString(
                ContributionConfirmedPayload(
                    "supporter@example.test",
                    "Jane Doe",
                    5_000L,
                    "USD",
                    "Fall Fundraiser",
                    "fall-fundraiser",
                    now.toString(),
                ),
            )
        val event =
            OutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = "contribution",
                aggregateId = UUID.randomUUID(),
                organizationId = UUID.randomUUID(),
                eventType = "contribution.confirmed",
                schemaVersion = 1,
                payload = payload,
                status = OutboxEventStatus.PROCESSING,
                attemptCount = 1,
                availableAt = now,
                processedAt = null,
                lastError = null,
                createdAt = now,
            )
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(event)

        verify(exactly = 1) { emailProvider.send(any()) }
        assertEquals("supporter@example.test", messageSlot.captured.to)
        assertTrue(messageSlot.captured.body.contains("Fall Fundraiser"))
    }
}
