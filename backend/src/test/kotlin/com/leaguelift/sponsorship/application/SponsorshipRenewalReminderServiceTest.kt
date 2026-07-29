package com.leaguelift.sponsorship.application

import com.leaguelift.config.SponsorshipRenewalReminderProperties
import com.leaguelift.notification.EmailMessage
import com.leaguelift.notification.EmailProvider
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

/**
 * Phase 6 remainder (ADR-019). Unit-level, mirroring `LedgerServiceTest`'s style —
 * the repository's actual join/date-window SQL is exercised end to end by
 * `SponsorshipIntegrationTest` via the rest of the confirm/approve flow, not
 * re-verified here; this test is about the service's own send/skip/mark-sent logic.
 */
class SponsorshipRenewalReminderServiceTest {

	private val sponsorshipRepository = mockk<SponsorshipRepository>()
	private val emailProvider = mockk<EmailProvider>()

	@Test
	fun `does nothing when the job is disabled`() {
		val service = SponsorshipRenewalReminderService(sponsorshipRepository, emailProvider, SponsorshipRenewalReminderProperties(enabled = false))

		service.sendDueReminders()

		verify(exactly = 0) { sponsorshipRepository.findNeedingRenewalReminder(any()) }
	}

	@Test
	fun `emails a candidate with a contact email and marks it reminded`() {
		val properties = SponsorshipRenewalReminderProperties(enabled = true, daysBefore = 21)
		val service = SponsorshipRenewalReminderService(sponsorshipRepository, emailProvider, properties)
		val candidate = SponsorshipRenewalCandidate(
			sponsorshipId = UUID.randomUUID(), organizationId = UUID.randomUUID(), packageName = "Gold Sponsor",
			placementEndDate = LocalDate.now().plusDays(10), sponsorName = "Acme Co", sponsorContactEmail = "sponsor@acme.test",
		)
		every { sponsorshipRepository.findNeedingRenewalReminder(21) } returns listOf(candidate)
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs
		every { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) } returns 1

		service.sendDueReminders()

		verify(exactly = 1) { emailProvider.send(any()) }
		verify(exactly = 1) { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) }
		kotlin.test.assertEquals("sponsor@acme.test", messageSlot.captured.to)
		kotlin.test.assertTrue(messageSlot.captured.subject.contains("Gold Sponsor"))
	}

	@Test
	fun `skips the email but still marks reminded when a sponsor has no contact email on file`() {
		val service = SponsorshipRenewalReminderService(sponsorshipRepository, emailProvider, SponsorshipRenewalReminderProperties())
		val candidate = SponsorshipRenewalCandidate(
			sponsorshipId = UUID.randomUUID(), organizationId = UUID.randomUUID(), packageName = "Silver Sponsor",
			placementEndDate = LocalDate.now().plusDays(5), sponsorName = "No Email Co", sponsorContactEmail = null,
		)
		every { sponsorshipRepository.findNeedingRenewalReminder(any()) } returns listOf(candidate)
		every { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) } returns 1

		service.sendDueReminders()

		verify(exactly = 0) { emailProvider.send(any()) }
		verify(exactly = 1) { sponsorshipRepository.markRenewalReminderSent(candidate.sponsorshipId) }
	}

	@Test
	fun `does nothing when there are no due candidates`() {
		val service = SponsorshipRenewalReminderService(sponsorshipRepository, emailProvider, SponsorshipRenewalReminderProperties())
		every { sponsorshipRepository.findNeedingRenewalReminder(any()) } returns emptyList()

		service.sendDueReminders()

		verify(exactly = 0) { emailProvider.send(any()) }
		verify(exactly = 0) { sponsorshipRepository.markRenewalReminderSent(any()) }
	}
}
