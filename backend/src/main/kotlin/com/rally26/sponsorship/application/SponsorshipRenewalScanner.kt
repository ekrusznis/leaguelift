package com.rally26.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.SponsorshipRenewalReminderProperties
import com.rally26.outbox.application.OutboxWriter
import com.rally26.sponsorship.persistence.SponsorshipRenewalCandidate
import com.rally26.sponsorship.persistence.SponsorshipRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(SponsorshipRenewalScanner::class.java)

private data class RenewalReminderPayload(
	val sponsorshipId: String,
	val sponsorName: String,
	val sponsorContactEmail: String?,
	val packageName: String,
	val placementEndDate: String,
)

/**
 * Replaces the direct-email `@Scheduled` job (Phase 6 remainder, ADR-019) now that the
 * outbox worker exists (Phase 8 slice 1, ADR-022) — this only finds due candidates and
 * enqueues one `sponsorship.renewal_reminder_due` outbox event per candidate;
 * [SponsorshipRenewalReminderHandler] does the actual send.
 *
 * `markRenewalReminderSent` is called here, right after enqueueing — deliberately NOT
 * in the handler. If it lived in the handler instead, a sponsorship stuck retrying
 * through the outbox's backoff window (e.g. Resend down for a day) would still have
 * `renewal_reminder_sent_at IS NULL`, so the next day's scan would enqueue a *second*
 * event for the same sponsorship. Marking it here makes the scan idempotent (each
 * sponsorship is enqueued exactly once) at the cost of a small, accepted risk in the
 * other direction: a crash between the outbox insert and this update would leave a
 * sponsorship enqueued-and-about-to-be-emailed but still eligible for re-scan tomorrow
 * — a possible duplicate reminder, never a silently skipped one. Not wrapped in
 * `@Transactional` for that same reason: writing the event *before* marking reminded
 * means a crash between the two only risks a harmless duplicate, not a silent miss.
 */
@Component
class SponsorshipRenewalScanner(
	private val sponsorshipRepository: SponsorshipRepository,
	private val outboxWriter: OutboxWriter,
	private val properties: SponsorshipRenewalReminderProperties,
	private val objectMapper: ObjectMapper,
) {

	@Scheduled(cron = "\${rally26.sponsorship.renewal-reminder.cron:0 0 8 * * *}")
	fun scanAndEnqueue() {
		if (!properties.enabled) return
		val candidates = sponsorshipRepository.findNeedingRenewalReminder(properties.daysBefore)
		if (candidates.isEmpty()) return
		log.info("Enqueueing {} sponsorship renewal reminder(s)", candidates.size)
		candidates.forEach(::enqueueOne)
	}

	private fun enqueueOne(candidate: SponsorshipRenewalCandidate) {
		val payload = RenewalReminderPayload(
			sponsorshipId = candidate.sponsorshipId.toString(),
			sponsorName = candidate.sponsorName,
			sponsorContactEmail = candidate.sponsorContactEmail,
			packageName = candidate.packageName,
			placementEndDate = candidate.placementEndDate.toString(),
		)
		outboxWriter.write(
			aggregateType = "sponsorship",
			aggregateId = candidate.sponsorshipId,
			organizationId = candidate.organizationId,
			eventType = "sponsorship.renewal_reminder_due",
			payloadJson = objectMapper.writeValueAsString(payload),
		)
		sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId)
	}
}
