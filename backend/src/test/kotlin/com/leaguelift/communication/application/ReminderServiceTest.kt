package com.leaguelift.communication.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.communication.domain.Announcement
import com.leaguelift.communication.domain.AnnouncementAudience
import com.leaguelift.communication.domain.AnnouncementKind
import com.leaguelift.communication.domain.AnnouncementScopeType
import com.leaguelift.communication.persistence.EventReminderSource
import com.leaguelift.communication.persistence.ReminderSourceRepository
import com.leaguelift.membership.application.MembershipService
import io.mockk.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
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
        val source = EventReminderSource(
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
                organizationId, AnnouncementScopeType.TEAM, teamId, AnnouncementKind.EVENT_REMINDER,
                "EVENT", eventId, null, any(), any(), any(), AnnouncementAudience.ALL, true, false, user,
            )
        } returns result

        service.send(organizationId, ReminderResourceType.EVENT, eventId, "request-123", true, false, user)

        verify(exactly = 1) {
            announcements.createSystemAndPublish(
                organizationId, AnnouncementScopeType.TEAM, teamId, AnnouncementKind.EVENT_REMINDER,
                "EVENT", eventId, null, any(), "Event reminder: Regional semifinal",
                match { it.contains("Aug 1, 2026 at 8:30 PM") && it.contains("Community Center") },
                AnnouncementAudience.ALL, true, false, user,
            )
        }
        assertTrue(source.timezone == "America/New_York")
    }
}
