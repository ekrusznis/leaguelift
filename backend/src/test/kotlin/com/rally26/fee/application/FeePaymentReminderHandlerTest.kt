package com.rally26.fee.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.notification.SmsMessage
import com.rally26.notification.SmsProvider
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import com.rally26.subscription.application.PlanEntitlementService
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

class FeePaymentReminderHandlerTest {
    private val emailProvider = mockk<EmailProvider>()
    private val smsProvider = mockk<SmsProvider>()
    private val objectMapper = ObjectMapper()
    private val planEntitlementService =
        mockk<PlanEntitlementService> {
            every { smsAllowed(any()) } returns true
        }
    private val handler =
        FeePaymentReminderHandler(
            emailProvider,
            smsProvider,
            ResendTemplateProperties(),
            FrontendProperties(),
            objectMapper,
            planEntitlementService,
        )

    private fun eventWithPayload(payloadJson: String): OutboxEvent {
        val now = Instant.now()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "fee_assignment",
            aggregateId = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            eventType = "fee.payment_reminder_due",
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
    fun `sends only an email when only a contact email is available`() {
        val payload =
            """{"feeAssignmentId":"${UUID.randomUUID()}","householdContactEmail":"family@example.test","householdContactPhone":null,""" +
                """"participantName":"Jamie Lee","description":"Fall registration","currency":"USD","dueDate":"2026-08-10","balanceMinor":10000}"""
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(eventWithPayload(payload))

        verify(exactly = 1) { emailProvider.send(any()) }
        verify(exactly = 0) { smsProvider.send(any()) }
        assertEquals("family@example.test", messageSlot.captured.to)
        assertTrue(messageSlot.captured.subject.contains("Fall registration"))
    }

    @Test
    fun `sends only an SMS when only a contact phone is available`() {
        val payload =
            """{"feeAssignmentId":"${UUID.randomUUID()}","householdContactEmail":null,"householdContactPhone":"+15550100",""" +
                """"participantName":"Jamie Lee","description":"Fall registration","currency":"USD","dueDate":"2026-08-10","balanceMinor":10000}"""
        val smsSlot = slot<SmsMessage>()
        every { smsProvider.send(capture(smsSlot)) } just runs

        handler.handle(eventWithPayload(payload))

        verify(exactly = 0) { emailProvider.send(any()) }
        verify(exactly = 1) { smsProvider.send(any()) }
        assertEquals("+15550100", smsSlot.captured.to)
        assertTrue(smsSlot.captured.body.contains("Fall registration"))
    }

    @Test
    fun `sends both channels when both are available`() {
        val payload =
            """{"feeAssignmentId":"${UUID.randomUUID()}","householdContactEmail":"family@example.test",""" +
                """"householdContactPhone":"+15550100","participantName":null,""" +
                """"description":"Fall registration","currency":"USD","dueDate":"2026-08-10","balanceMinor":10000}"""
        every { emailProvider.send(any()) } just runs
        every { smsProvider.send(any()) } just runs

        handler.handle(eventWithPayload(payload))

        verify(exactly = 1) { emailProvider.send(any()) }
        verify(exactly = 1) { smsProvider.send(any()) }
    }

    @Test
    fun `skips SMS but still sends the email when the organization's plan does not allow SMS`() {
        every { planEntitlementService.smsAllowed(any()) } returns false
        val payload =
            """{"feeAssignmentId":"${UUID.randomUUID()}","householdContactEmail":"family@example.test",""" +
                """"householdContactPhone":"+15550100","participantName":null,""" +
                """"description":"Fall registration","currency":"USD","dueDate":"2026-08-10","balanceMinor":10000}"""
        every { emailProvider.send(any()) } just runs

        handler.handle(eventWithPayload(payload))

        verify(exactly = 1) { emailProvider.send(any()) }
        verify(exactly = 0) { smsProvider.send(any()) }
    }

    @Test
    fun `skips both channels when neither is available`() {
        val payload =
            """{"feeAssignmentId":"${UUID.randomUUID()}","householdContactEmail":null,"householdContactPhone":null,""" +
                """"participantName":null,"description":"Fall registration","currency":"USD","dueDate":"2026-08-10","balanceMinor":10000}"""

        handler.handle(eventWithPayload(payload))

        verify(exactly = 0) { emailProvider.send(any()) }
        verify(exactly = 0) { smsProvider.send(any()) }
    }
}
