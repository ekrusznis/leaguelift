package com.leaguelift.organization.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.organization.domain.Organization
import com.leaguelift.organization.domain.OrganizationStatus
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.organization.persistence.OrganizationRepository
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

class OrganizationServiceTest {

	private val organizationRepository = mockk<OrganizationRepository>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val service = OrganizationService(organizationRepository, membershipService, auditService)

	private val currentUser = CurrentUser(UUID.randomUUID(), "sub-owner", "owner@example.com", "Owner")

	@Test
	fun `creating an organization rejects an invalid slug`() {
		assertFailsWith<ValidationException> {
			service.create("Riverside Soccer", "Invalid Slug!", OrganizationType.RECREATIONAL_LEAGUE, currentUser)
		}
	}

	@Test
	fun `creating an organization rejects a slug already in use`() {
		every { organizationRepository.findBySlug("riverside-soccer") } returns existingOrganization()

		assertFailsWith<ConflictException> {
			service.create("Riverside Soccer", "riverside-soccer", OrganizationType.RECREATIONAL_LEAGUE, currentUser)
		}
	}

	@Test
	fun `creating an organization grants the creator OWNER membership and records an audit event`() {
		val created = existingOrganization()
		every { organizationRepository.findBySlug("riverside-soccer") } returns null
		every {
			organizationRepository.insert("Riverside Soccer", "riverside-soccer", OrganizationType.RECREATIONAL_LEAGUE)
		} returns created
		every { membershipService.grantOwner(created.id, currentUser.userId) } returns ownerMembership(created.id, currentUser.userId)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.create("Riverside Soccer", "riverside-soccer", OrganizationType.RECREATIONAL_LEAGUE, currentUser)

		assertEquals(created.id, result.id)
		verify(exactly = 1) { membershipService.grantOwner(created.id, currentUser.userId) }
		verify(exactly = 1) {
			auditService.record(currentUser.userId, created.id, "organization.created", "organization", created.id, any())
		}
	}

	private fun existingOrganization() = Organization(
		id = UUID.randomUUID(),
		name = "Riverside Soccer",
		slug = "riverside-soccer",
		organizationType = OrganizationType.RECREATIONAL_LEAGUE,
		status = OrganizationStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun ownerMembership(organizationId: UUID, userId: UUID) = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = organizationId,
		userId = userId,
		role = MembershipRole.OWNER,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
