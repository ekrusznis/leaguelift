package com.rally26.integration.eventsource.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.eventsource.domain.EventSourceConnection
import com.rally26.integration.eventsource.domain.EventSourceConnectionStatus
import com.rally26.integration.eventsource.domain.EventSourceProvider
import com.rally26.integration.eventsource.persistence.EventSourceConnectionRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventSourceConnectionServiceTest {

	private val eventSourceConnectionRepository = mockk<EventSourceConnectionRepository>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val teamRepository = mockk<TeamRepository>()
	private val service = EventSourceConnectionService(eventSourceConnectionRepository, membershipService, auditService, teamRepository)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

	private fun managerMembership() = OrganizationMembership(UUID.randomUUID(), orgId, currentUser.userId, MembershipRole.OWNER, MembershipStatus.ACTIVE, Instant.now(), Instant.now())

	private fun connection(id: UUID = UUID.randomUUID()) = EventSourceConnection(
		id, orgId, EventSourceProvider.ICS_FEED, "Varsity Schedule", "https://example.com/feed.ics", "America/New_York", null,
		EventSourceConnectionStatus.ACTIVE, null, null, null, currentUser.userId, Instant.now(), Instant.now(),
	)

	@Test
	fun `connectIcsFeed requires manager role`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } throws ForbiddenException("DENIED", "no")

		assertFailsWith<ForbiddenException> {
			service.connectIcsFeed(orgId, "Varsity Schedule", "https://example.com/feed.ics", "America/New_York", null, currentUser)
		}
	}

	@Test
	fun `connectIcsFeed rejects a non-http url`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

		assertFailsWith<ValidationException> {
			service.connectIcsFeed(orgId, "Varsity Schedule", "ftp://example.com/feed.ics", "America/New_York", null, currentUser)
		}
	}

	@Test
	fun `connectIcsFeed rejects a blank label`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

		assertFailsWith<ValidationException> {
			service.connectIcsFeed(orgId, "  ", "https://example.com/feed.ics", "America/New_York", null, currentUser)
		}
	}

	@Test
	fun `connectIcsFeed rejects an invalid timezone`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

		assertFailsWith<ValidationException> {
			service.connectIcsFeed(orgId, "Varsity Schedule", "https://example.com/feed.ics", "Not/AZone", null, currentUser)
		}
	}

	@Test
	fun `connectIcsFeed inserts and records an audit event`() {
		val created = connection()
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every {
			eventSourceConnectionRepository.insert(orgId, EventSourceProvider.ICS_FEED, "Varsity Schedule", "https://example.com/feed.ics", "America/New_York", null, currentUser.userId)
		} returns created
		every { auditService.record(currentUser.userId, orgId, "event_source_connection.connected", "event_source_connection", created.id) } just runs

		val result = service.connectIcsFeed(orgId, "Varsity Schedule", "https://example.com/feed.ics", "America/New_York", null, currentUser)

		assertEquals(created.id, result.id)
		verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "event_source_connection.connected", "event_source_connection", created.id) }
	}

	@Test
	fun `disconnect throws NotFoundException for a connection in another organization`() {
		val connectionId = UUID.randomUUID()
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { eventSourceConnectionRepository.findById(connectionId, orgId) } returns null

		assertFailsWith<NotFoundException> {
			service.disconnect(orgId, connectionId, currentUser)
		}
	}

	@Test
	fun `disconnect updates status and records an audit event`() {
		val existing = connection()
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { eventSourceConnectionRepository.findById(existing.id, orgId) } returns existing
		every { eventSourceConnectionRepository.disconnect(existing.id, orgId) } returns 1
		every { auditService.record(currentUser.userId, orgId, "event_source_connection.disconnected", "event_source_connection", existing.id) } just runs

		service.disconnect(orgId, existing.id, currentUser)

		verify(exactly = 1) { eventSourceConnectionRepository.disconnect(existing.id, orgId) }
	}

	@Test
	fun `list requires active membership`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
		every { eventSourceConnectionRepository.listForOrganization(orgId) } returns listOf(connection())

		val result = service.list(orgId, currentUser)

		assertEquals(1, result.size)
	}
}
