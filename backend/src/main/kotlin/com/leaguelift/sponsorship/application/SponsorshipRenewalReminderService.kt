package com.leaguelift.sponsorship.application

import com.leaguelift.config.SponsorshipRenewalReminderProperties
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
import com.leaguelift.sponsorship.persistence.SponsorshipRenewalCandidate
import com.leaguelift.sponsorship.persistence.SponsorshipRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(SponsorshipRenewalReminderService::class.java)

/**
 * A minimal renewal-reminder job (Phase 6 remainder, ADR-019) — a `@Scheduled` direct
 * query, deliberately OUTSIDE the outbox-worker pattern. DESIGN-DOC.md section 17: the
 * outbox worker doesn't exist yet (Phase 8, "not built yet — `outbox_event` rows are
 * written elsewhere in the codebase but nothing consumes them"). Building this on top
 * of infrastructure that doesn't exist would block a real Phase-6 need on an unrelated
 * Phase-8 milestone. **This is a deliberate stopgap expected to be rebuilt once Phase
 * 8's real outbox-consumer/notification infrastructure lands** — the query-and-email
 * shape here (find due candidates, send, mark sent) is exactly what an outbox-event
 * handler would do instead, once one exists.
 *
 * Each candidate is reminded at most once (`renewal_reminder_sent_at` guards it) — this
 * job can safely run as often as its cron fires without re-emailing the same sponsor.
 */
@Service
class SponsorshipRenewalReminderService(
	private val sponsorshipRepository: SponsorshipRepository,
	private val emailProvider: EmailProvider,
	private val properties: SponsorshipRenewalReminderProperties,
) {

	@Scheduled(cron = "\${leaguelift.sponsorship.renewal-reminder.cron:0 0 8 * * *}")
	fun sendDueReminders() {
		if (!properties.enabled) return
		val candidates = sponsorshipRepository.findNeedingRenewalReminder(properties.daysBefore)
		if (candidates.isEmpty()) return
		log.info("Sending {} sponsorship renewal reminder(s)", candidates.size)
		candidates.forEach(::remindOne)
	}

	private fun remindOne(candidate: SponsorshipRenewalCandidate) {
		if (candidate.sponsorContactEmail != null) {
			emailProvider.send(
				EmailMessage(
					to = candidate.sponsorContactEmail,
					subject = "Your sponsorship of ${candidate.packageName} is ending soon",
					body = "Hi ${candidate.sponsorName},\n\n" +
						"Your sponsorship placement for \"${candidate.packageName}\" is scheduled to end on " +
						"${candidate.placementEndDate}. Reach out to the organization if you'd like to renew.\n\n" +
						"— LeagueLift",
				),
			)
		} else {
			log.info(
				"Sponsorship {} for package \"{}\" is expiring {} but its sponsor has no contact email on file — skipping email, marking reminded.",
				candidate.sponsorshipId, candidate.packageName, candidate.placementEndDate,
			)
		}
		sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId)
	}
}
