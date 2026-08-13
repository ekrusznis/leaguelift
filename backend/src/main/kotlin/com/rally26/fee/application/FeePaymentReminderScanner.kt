package com.rally26.fee.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FeePaymentReminderProperties
import com.rally26.fee.persistence.FeePaymentReminderCandidate
import com.rally26.fee.persistence.FeeRepository
import com.rally26.outbox.application.OutboxWriter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(FeePaymentReminderScanner::class.java)

private data class FeePaymentReminderPayload(
    val feeAssignmentId: String,
    val householdContactEmail: String?,
    val householdContactPhone: String?,
    val participantName: String?,
    val description: String,
    val currency: String,
    val dueDate: String,
    val balanceMinor: Long,
    val householdId: String,
)

/**
 * Fee-due-date payment reminders (Phase 8 slice 2, ADR-023; SMS added slice 3, ADR-024)
 * — the first genuinely new
 * notification trigger this codebase adds on top of the outbox worker built in slice 1
 * (ADR-022). Mirrors `SponsorshipRenewalScanner`'s scan-and-enqueue-and-mark-reminded
 * shape exactly, including the same rationale for marking reminded here rather than in
 * the handler (a fee assignment stuck retrying through outbox backoff would otherwise
 * be re-enqueued by every subsequent day's scan).
 */
@Component
class FeePaymentReminderScanner(
    private val feeRepository: FeeRepository,
    private val outboxWriter: OutboxWriter,
    private val properties: FeePaymentReminderProperties,
    private val objectMapper: ObjectMapper,
) {
    @Scheduled(cron = "\${rally26.fee.payment-reminder.cron:0 0 8 * * *}")
    fun scanAndEnqueue() {
        if (!properties.enabled) return
        val candidates = feeRepository.findNeedingPaymentReminder(properties.daysBefore)
        if (candidates.isEmpty()) return
        log.info("Enqueueing {} fee payment reminder(s)", candidates.size)
        candidates.forEach(::enqueueOne)
    }

    private fun enqueueOne(candidate: FeePaymentReminderCandidate) {
        val payload =
            FeePaymentReminderPayload(
                feeAssignmentId = candidate.feeAssignmentId.toString(),
                householdContactEmail = candidate.householdContactEmail,
                householdContactPhone = candidate.householdContactPhone,
                participantName = candidate.participantName,
                description = candidate.description,
                currency = candidate.currency,
                dueDate = candidate.dueDate.toString(),
                balanceMinor = candidate.balanceMinor,
                householdId = candidate.householdId.toString(),
            )
        outboxWriter.write(
            aggregateType = "fee_assignment",
            aggregateId = candidate.feeAssignmentId,
            organizationId = candidate.organizationId,
            eventType = "fee.payment_reminder_due",
            payloadJson = objectMapper.writeValueAsString(payload),
        )
        feeRepository.markPaymentReminderSent(candidate.feeAssignmentId)
    }
}
