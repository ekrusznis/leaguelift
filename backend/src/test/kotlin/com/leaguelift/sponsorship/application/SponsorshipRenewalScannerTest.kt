package com.leaguelift.sponsorship.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.config.SponsorshipRenewalReminderProperties
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.sponsorship.persistence.SponsorshipRenewalCandidate
import com.leaguelift.sponsorship.persistence.SponsorshipRepository
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

/**
 * Phase 8 slice 1 (ADR-022). Mirrors the retired `SponsorshipRenewalReminderServiceTest`'s
 * style — this only covers the scan/enqueue/mark-reminded half; the actual send is
 * [SponsorshipRenewalReminderHandlerTest].
 */
class SponsorshipRenewalScannerTest {

	private val sponsorshipRepository = mockk<SponsorshipRepository>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val objectMapper = ObjectMapper()

	@Test
	fun `does nothing when the job is disabled`() {
		val scanner = SponsorshipRenewalScanner(sponsorshipRepository, outboxWriter, SponsorshipRenewalReminderProperties(enabled = false), objectMapper)

		scanner.scanAndEnqueue()

		verify(exactly = 0) { sponsorshipRepository.findNeedingRenewalReminder(any()) }
	}

	@Test
	fun `enqueues an outbox event and marks a due candidate reminded`() {
		val properties = SponsorshipRenewalReminderProperties(enabled = true, daysBefore = 21)
		val scanner = SponsorshipRenewalScanner(sponsorshipRepository, outboxWriter, properties, objectMapper)
		val candidate = SponsorshipRenewalCandidate(
			sponsorshipId = UUID.randomUUID(), organizationId = UUID.randomUUID(), packageName = "Gold Sponsor",
			placementEndDate = LocalDate.now().plusDays(10), sponsorName = "Acme Co", sponsorContactEmail = "sponsor@acme.test",
		)
		every { sponsorshipRepository.findNeedingRenewalReminder(21) } returns listOf(candidate)
		val payloadSlot = slot<String>()
		every {
			outboxWriter.write(
				aggregateType = "sponsorship",
				aggregateId = candidate.sponsorshipId,
				organizationId = candidate.organizationId,
				eventType = "sponsorship.renewal_reminder_due",
				payloadJson = capture(payloadSlot),
			)
		} just runs
		every { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) } returns 1

		scanner.scanAndEnqueue()

		verify(exactly = 1) { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) }
		assertTrue(payloadSlot.captured.contains("sponsor@acme.test"))
		assertTrue(payloadSlot.captured.contains("Gold Sponsor"))
	}

	@Test
	fun `does nothing when there are no due candidates`() {
		val scanner = SponsorshipRenewalScanner(sponsorshipRepository, outboxWriter, SponsorshipRenewalReminderProperties(), objectMapper)
		every { sponsorshipRepository.findNeedingRenewalReminder(any()) } returns emptyList()

		scanner.scanAndEnqueue()

		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
		verify(exactly = 0) { sponsorshipRepository.markRenewalReminderSent(any()) }
	}

	@Test
	fun `still marks reminded when a sponsor has no contact email on file`() {
		val scanner = SponsorshipRenewalScanner(sponsorshipRepository, outboxWriter, SponsorshipRenewalReminderProperties(), objectMapper)
		val candidate = SponsorshipRenewalCandidate(
			sponsorshipId = UUID.randomUUID(), organizationId = UUID.randomUUID(), packageName = "Silver Sponsor",
			placementEndDate = LocalDate.now().plusDays(5), sponsorName = "No Email Co", sponsorContactEmail = null,
		)
		every { sponsorshipRepository.findNeedingRenewalReminder(any()) } returns listOf(candidate)
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs
		every { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) } returns 1

		scanner.scanAndEnqueue()

		// Enqueues (and marks reminded) even with no contact email on file — the handler
		// decides at send time whether there's anything to actually email.
		verify(exactly = 1) { outboxWriter.write(any(), any(), any(), any(), any()) }
		verify(exactly = 1) { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) }
	}
}
