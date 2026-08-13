package com.rally26.event.application

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
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EventRsvpChangeNotificationHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val objectMapper = jacksonObjectMapper()
    private val handler = EventRsvpChangeNotificationHandler(emailProvider, ResendTemplateProperties(), FrontendProperties(), objectMapper)

    private fun eventWithPayload(payloadJson: String): OutboxEvent {
        val now = Instant.now()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "event_rsvp",
            aggregateId = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            eventType = "event.rsvp_changed",
            schemaVersion = 1,
            payload = payloadJson,
            status = OutboxEventStatus.PROCESSING,
            attemptCount = 1,
            availableAt = now,
            processedAt = null,
            lastError = null,
            createdAt = now,
        )
    }

    @Test
    fun `emails every team staff recipient`() {
        val payload =
            objectMapper.writeValueAsString(
                RsvpChangeNotificationPayload(
                    UUID.randomUUID(),
                    "Varsity Soccer Practice",
                    "Jamie Lee",
                    "NO_RESPONSE",
                    "ATTENDING",
                    listOf("coach@example.com", "manager@example.com"),
                ),
            )
        val messages = mutableListOf<EmailMessage>()
        every { emailProvider.send(capture(messages)) } just runs

        handler.handle(eventWithPayload(payload))

        verify(exactly = 2) { emailProvider.send(any()) }
        assertEquals(setOf("coach@example.com", "manager@example.com"), messages.map { it.to }.toSet())
    }

    @Test
    fun `sends nothing when no team staff is resolvable`() {
        val payload =
            objectMapper.writeValueAsString(
                RsvpChangeNotificationPayload(UUID.randomUUID(), "Varsity Soccer Practice", "Jamie Lee", null, "ATTENDING", emptyList()),
            )

        handler.handle(eventWithPayload(payload))

        verify(exactly = 0) { emailProvider.send(any()) }
    }
}
