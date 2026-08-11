package com.rally26.communication.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.web.CurrentUser
import com.rally26.communication.domain.Announcement
import com.rally26.communication.domain.AnnouncementAudience
import com.rally26.communication.domain.AnnouncementKind
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.persistence.EventReminderSource
import com.rally26.communication.persistence.ParticipantReminderSource
import com.rally26.communication.persistence.ReminderSourceRepository
import com.rally26.membership.application.MembershipService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class ReminderServiceTest {
    private val sources = mockk<ReminderSourceRepository>()
    private val announcements = mockk<AnnouncementService>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val clock = Clock.fixed(Instant.parse("2026-08-01T15:50:00Z"), ZoneOffset.UTC)
    private val service = ReminderService(sources, announcements, membershipService, authorizationService, clock)
    private val organizationId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

    @Test
    fun `event reminder uses the event timezone and team audience`() {
        val source =
            EventReminderSource(
                id = eventId,
                organizationId = organizationId,
                teamId = teamId,
                tournamentId = null,
                title = "Regional semifinal",
                status = "SCHEDULED",
                startAt = Instant.parse("2026-08-02T00:30:00Z"),
                timezone = "America/New_York",
                venueName = "Community Center",
            )
        val result = mockk<Announcement>()
        every { sources.findEvent(organizationId, eventId) } returns source
        every { authorizationService.requireTeamCapability(organizationId, teamId, user, Capabilities.TEAM_COMMUNICATION_MANAGE) } just runs
        every {
            announcements.createSystemAndPublish(
                organizationId,
                AnnouncementScopeType.TEAM,
                teamId,
                AnnouncementKind.EVENT_REMINDER,
                "EVENT",
                eventId,
                null,
                any(),
                any(),
                any(),
                AnnouncementAudience.ALL,
                true,
                false,
                user,
            )
        } returns result

        service.send(organizationId, ReminderResourceType.EVENT, eventId, "request-123", true, false, user)

        verify(exactly = 1) {
            announcements.createSystemAndPublish(
                organizationId,
                AnnouncementScopeType.TEAM,
                teamId,
                AnnouncementKind.EVENT_REMINDER,
                "EVENT",
                eventId,
                null,
                any(),
                "Event reminder: Regional semifinal",
                match { it.contains("Aug 1, 2026 at 8:30 PM") && it.contains("Community Center") },
                AnnouncementAudience.ALL,
                true,
                false,
                user,
            )
        }
        assertTrue(source.timezone == "America/New_York")
    }

    @Test
    fun `eligibility reminder requires a manager role and notifies the participant's household guardians`() {
        val participantId = UUID.randomUUID()
        val householdId = UUID.randomUUID()
        val source =
            ParticipantReminderSource(
                id = participantId,
                organizationId = organizationId,
                householdId = householdId,
                firstName = "Jordan",
                lastName = "Smith",
            )
        val result = mockk<Announcement>()
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk()
        every { sources.findParticipant(organizationId, participantId) } returns source
        every {
            announcements.createSystemAndPublish(
                organizationId,
                AnnouncementScopeType.ORGANIZATION,
                organizationId,
                AnnouncementKind.ELIGIBILITY_REMINDER,
                "PARTICIPANT",
                participantId,
                householdId,
                any(),
                "Eligibility reminder: Jordan Smith",
                any(),
                AnnouncementAudience.GUARDIANS,
                true,
                false,
                user,
            )
        } returns result

        service.send(organizationId, ReminderResourceType.ELIGIBILITY, participantId, "request-456", true, false, user)

        verify(exactly = 1) { membershipService.requireManagerRole(organizationId, user) }
        verify(exactly = 1) {
            announcements.createSystemAndPublish(
                organizationId,
                AnnouncementScopeType.ORGANIZATION,
                organizationId,
                AnnouncementKind.ELIGIBILITY_REMINDER,
                "PARTICIPANT",
                participantId,
                householdId,
                any(),
                "Eligibility reminder: Jordan Smith",
                any(),
                AnnouncementAudience.GUARDIANS,
                true,
                false,
                user,
            )
        }
    }
}
