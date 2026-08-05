package com.rally26.fee.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FeePaymentReminderProperties
import com.rally26.fee.persistence.FeePaymentReminderCandidate
import com.rally26.fee.persistence.FeeRepository
import com.rally26.outbox.application.OutboxWriter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class FeePaymentReminderScannerTest {
    private val feeRepository = mockk<FeeRepository>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val objectMapper = ObjectMapper()

    @Test
    fun `does nothing when the job is disabled`() {
        val scanner = FeePaymentReminderScanner(feeRepository, outboxWriter, FeePaymentReminderProperties(enabled = false), objectMapper)

        scanner.scanAndEnqueue()

        verify(exactly = 0) { feeRepository.findNeedingPaymentReminder(any()) }
    }

    @Test
    fun `enqueues an outbox event and marks a due candidate reminded`() {
        val properties = FeePaymentReminderProperties(enabled = true, daysBefore = 5)
        val scanner = FeePaymentReminderScanner(feeRepository, outboxWriter, properties, objectMapper)
        val candidate =
            FeePaymentReminderCandidate(
                feeAssignmentId = UUID.randomUUID(),
                organizationId = UUID.randomUUID(),
                householdId = UUID.randomUUID(),
                householdContactEmail = "family@example.test",
                householdContactPhone = null,
                householdSmsOptIn = false,
                participantName = "Jamie Lee",
                description = "Fall registration",
                currency = "USD",
                dueDate = LocalDate.now().plusDays(3),
                balanceMinor = 10_000L,
            )
        every { feeRepository.findNeedingPaymentReminder(5) } returns listOf(candidate)
        val payloadSlot = slot<String>()
        every {
            outboxWriter.write(
                aggregateType = "fee_assignment",
                aggregateId = candidate.feeAssignmentId,
                organizationId = candidate.organizationId,
                eventType = "fee.payment_reminder_due",
                payloadJson = capture(payloadSlot),
            )
        } just runs
        every { feeRepository.markPaymentReminderSent(candidate.feeAssignmentId) } returns 1

        scanner.scanAndEnqueue()

        verify(exactly = 1) { feeRepository.markPaymentReminderSent(candidate.feeAssignmentId) }
        assertTrue(payloadSlot.captured.contains("family@example.test"))
        assertTrue(payloadSlot.captured.contains("Fall registration"))
    }

    @Test
    fun `does nothing when there are no due candidates`() {
        val scanner = FeePaymentReminderScanner(feeRepository, outboxWriter, FeePaymentReminderProperties(), objectMapper)
        every { feeRepository.findNeedingPaymentReminder(any()) } returns emptyList()

        scanner.scanAndEnqueue()

        verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { feeRepository.markPaymentReminderSent(any()) }
    }
}
