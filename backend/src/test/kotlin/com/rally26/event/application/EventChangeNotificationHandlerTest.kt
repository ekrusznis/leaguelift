package com.rally26.event.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.SmsMessage
import com.rally26.notification.SmsProvider
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

class EventChangeNotificationHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val smsProvider = mockk<SmsProvider>()
    private val objectMapper = jacksonObjectMapper()
    private val handler = EventChangeNotificationHandler("event.time_changed", emailProvider, smsProvider, objectMapper)

    private fun eventWithPayload(payloadJson: String): OutboxEvent {
        val now = Instant.now()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "event",
            aggregateId = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            eventType = "event.time_changed",
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
    fun `emails and texts every recipient with an available channel`() {
        val payload =
            objectMapper.writeValueAsString(
                EventChangeNotificationPayload(
                    UUID.randomUUID(),
                    "Varsity Soccer Practice",
                    "The time for \"Varsity Soccer Practice\" has changed.",
                    listOf(EventRecipientContact("parent@example.com", "+15551234567"), EventRecipientContact(null, "+15557654321")),
                ),
            )
        val emailSlot = slot<EmailMessage>()
        val smsSlots = mutableListOf<SmsMessage>()
        every { emailProvider.send(capture(emailSlot)) } just runs
        every { smsProvider.send(capture(smsSlots)) } just runs

        handler.handle(eventWithPayload(payload))

        verify(exactly = 1) { emailProvider.send(any()) }
        assertEquals("parent@example.com", emailSlot.captured.to)
        verify(exactly = 2) { smsProvider.send(any()) }
        assertEquals(setOf("+15551234567", "+15557654321"), smsSlots.map { it.to }.toSet())
    }

    @Test
    fun `sends nothing when there are no recipients`() {
        val payload =
            objectMapper.writeValueAsString(
                EventChangeNotificationPayload(UUID.randomUUID(), "Varsity Soccer Practice", "The time has changed.", emptyList()),
            )

        handler.handle(eventWithPayload(payload))

        verify(exactly = 0) { emailProvider.send(any()) }
        verify(exactly = 0) { smsProvider.send(any()) }
    }
}
