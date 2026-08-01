package com.leaguelift.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.event.domain.EventTemplate
import com.leaguelift.event.domain.EventTemplateStatus
import com.leaguelift.event.domain.EventType
import com.leaguelift.event.domain.EventVisibility
import com.leaguelift.event.persistence.EventTemplateRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.OrganizationMembership
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventTemplateServiceTest {

    private val repository = mockk<EventTemplateRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = EventTemplateService(repository, membershipService, auditService, ObjectMapper())
    private val organizationId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val membership = mockk<OrganizationMembership>()

    private fun template(
        id: UUID = UUID.randomUUID(),
        name: String = "Weeknight practice",
        status: EventTemplateStatus = EventTemplateStatus.ACTIVE,
    ) = EventTemplate(
        id = id,
        organizationId = organizationId,
        name = name,
        eventType = EventType.PRACTICE,
        title = "Team practice",
        description = null,
        durationMinutes = 90,
        arrivalOffsetMinutes = 15,
        meetingOffsetMinutes = null,
        timezone = "America/New_York",
        venueName = "Community Gym",
        address = "100 Main Street",
        area = "Court 2",
        meetingPoint = null,
        directionsNotes = null,
        visibility = EventVisibility.TEAM,
        status = status,
        createdByUserId = currentUser.userId,
        updatedByUserId = currentUser.userId,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `active templates can be listed by an active organization member`() {
        val expected = listOf(template())
        every { membershipService.requireActiveMembership(organizationId, currentUser) } returns membership
        every { repository.listForOrganization(organizationId, false) } returns expected

        assertEquals(expected, service.list(organizationId, false, currentUser))

        verify(exactly = 1) { membershipService.requireActiveMembership(organizationId, currentUser) }
        verify(exactly = 0) { membershipService.requireManagerRole(any(), any()) }
    }

    @Test
    fun `including archived templates requires manager access`() {
        every { membershipService.requireManagerRole(organizationId, currentUser) } returns membership
        every { repository.listForOrganization(organizationId, true) } returns emptyList()

        service.list(organizationId, true, currentUser)

        verify(exactly = 1) { membershipService.requireManagerRole(organizationId, currentUser) }
    }

    @Test
    fun `create normalizes optional text and records audit`() {
        val created = template()
        every { membershipService.requireManagerRole(organizationId, currentUser) } returns membership
        every {
            repository.insert(
                organizationId,
                "Weeknight practice",
                EventType.PRACTICE,
                "Team practice",
                null,
                90,
                15,
                null,
                "America/New_York",
                "Community Gym",
                null,
                "Court 2",
                null,
                null,
                EventVisibility.TEAM,
                currentUser.userId,
            )
        } returns created
        every { auditService.record(currentUser.userId, organizationId, "event_template.created", "event_template", created.id, any()) } just runs

        val result = service.create(
            organizationId = organizationId,
            name = "  Weeknight practice  ",
            eventType = EventType.PRACTICE,
            title = " Team practice ",
            description = "  ",
            durationMinutes = 90,
            arrivalOffsetMinutes = 15,
            meetingOffsetMinutes = null,
            timezone = " America/New_York ",
            venueName = " Community Gym ",
            address = null,
            area = " Court 2 ",
            meetingPoint = null,
            directionsNotes = null,
            visibility = EventVisibility.TEAM,
            currentUser = currentUser,
        )

        assertEquals(created, result)
        verify(exactly = 1) { auditService.record(currentUser.userId, organizationId, "event_template.created", "event_template", created.id, any()) }
    }

    @Test
    fun `create rejects an invalid timezone before persistence`() {
        every { membershipService.requireManagerRole(organizationId, currentUser) } returns membership

        assertFailsWith<ValidationException> {
            service.create(
                organizationId, "Practice", EventType.PRACTICE, null, null, 60, null, null,
                "Mars/Olympus", null, null, null, null, null, EventVisibility.TEAM, currentUser,
            )
        }

        verify(exactly = 0) { repository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `duplicate active name becomes a typed conflict`() {
        every { membershipService.requireManagerRole(organizationId, currentUser) } returns membership
        every { repository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            DuplicateKeyException("duplicate")

        assertFailsWith<ConflictException> {
            service.create(
                organizationId, "Practice", EventType.PRACTICE, null, null, 60, null, null,
                "America/New_York", null, null, null, null, null, EventVisibility.TEAM, currentUser,
            )
        }
    }

    @Test
    fun `archive is idempotent for an already archived template`() {
        val archived = template(status = EventTemplateStatus.ARCHIVED)
        every { membershipService.requireManagerRole(organizationId, currentUser) } returns membership
        every { repository.findById(archived.id, organizationId) } returns archived

        assertEquals(archived, service.archive(organizationId, archived.id, currentUser))

        verify(exactly = 0) { repository.archive(any(), any(), any()) }
        verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any(), any()) }
    }
}
