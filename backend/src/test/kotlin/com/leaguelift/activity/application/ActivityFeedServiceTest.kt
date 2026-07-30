package com.leaguelift.activity.application

import com.leaguelift.audit.domain.AuditEvent
import com.leaguelift.audit.persistence.AuditEventRepository
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.membership.persistence.MembershipRepository
import com.leaguelift.organization.domain.Organization
import com.leaguelift.organization.domain.OrganizationStatus
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.organization.persistence.OrganizationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityFeedServiceTest {

	private val auditEventRepository = mockk<AuditEventRepository>()
	private val membershipRepository = mockk<MembershipRepository>()
	private val organizationRepository = mockk<OrganizationRepository>()

	private val service = ActivityFeedService(auditEventRepository, membershipRepository, organizationRepository)

	private fun membership(organizationId: UUID) = OrganizationMembership(UUID.randomUUID(), organizationId, UUID.randomUUID(), MembershipRole.OWNER, MembershipStatus.ACTIVE, Instant.now(), Instant.now())

	private fun organization(id: UUID, name: String) = Organization(id, name, "slug-$name", OrganizationType.RECREATIONAL_LEAGUE, OrganizationStatus.ACTIVE, emptyList(), null, null, Instant.now(), Instant.now())

	private fun event(organizationId: UUID?) = AuditEvent(UUID.randomUUID(), UUID.randomUUID(), organizationId, "team.created", "team", UUID.randomUUID(), "{}", Instant.now())

	@Test
	fun `a platform administrator sees the feed across every organization, unfiltered`() {
		val currentUser = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)
		val orgA = UUID.randomUUID()
		val e1 = event(orgA)
		every { auditEventRepository.listRecentAcrossAllOrganizations(50) } returns listOf(e1)
		every { organizationRepository.findById(orgA) } returns organization(orgA, "Org A")

		val result = service.getFeed(currentUser)

		assertEquals(1, result.size)
		assertEquals("Org A", result.first().organizationName)
		verify(exactly = 0) { membershipRepository.listActiveForUser(any()) }
	}

	@Test
	fun `a regular user sees the feed scoped to only the organizations they belong to`() {
		val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach", platformAdministrator = false)
		val orgA = UUID.randomUUID()
		val orgB = UUID.randomUUID()
		every { membershipRepository.listActiveForUser(currentUser.userId) } returns listOf(membership(orgA), membership(orgB))
		every { auditEventRepository.listRecentForOrganizations(setOf(orgA, orgB), 50) } returns listOf(event(orgA), event(orgB))
		every { organizationRepository.findById(orgA) } returns organization(orgA, "Org A")
		every { organizationRepository.findById(orgB) } returns organization(orgB, "Org B")

		val result = service.getFeed(currentUser)

		assertEquals(2, result.size)
		assertTrue(result.any { it.organizationName == "Org A" })
		assertTrue(result.any { it.organizationName == "Org B" })
	}

	@Test
	fun `a user with no organization memberships gets an empty feed without querying audit events`() {
		val currentUser = CurrentUser(UUID.randomUUID(), "nobody@example.com", "Nobody", platformAdministrator = false)
		every { membershipRepository.listActiveForUser(currentUser.userId) } returns emptyList()
		every { auditEventRepository.listRecentForOrganizations(emptySet(), 50) } returns emptyList()

		val result = service.getFeed(currentUser)

		assertEquals(emptyList(), result)
	}
}
