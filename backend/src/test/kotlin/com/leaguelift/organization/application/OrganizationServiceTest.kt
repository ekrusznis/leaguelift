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

	private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

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
		sports = emptyList(),
		contactEmail = null,
		contactPhone = null,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	@Test
	fun `updating with an invalid contact email is rejected`() {
		val organization = existingOrganization()
		every { membershipService.requireActiveMembership(organization.id, currentUser) } returns ownerMembership(organization.id, currentUser.userId)
		every { organizationRepository.findById(organization.id) } returns organization

		assertFailsWith<ValidationException> {
			service.update(organization.id, null, null, null, "not-an-email", null, currentUser)
		}
	}

	@Test
	fun `updating with a valid profile succeeds and is audited`() {
		val organization = existingOrganization()
		val updated = organization.copy(sports = listOf("Soccer"), contactEmail = "ops@example.com")
		every { membershipService.requireActiveMembership(organization.id, currentUser) } returns ownerMembership(organization.id, currentUser.userId)
		every { organizationRepository.findById(organization.id) } returnsMany listOf(organization, updated)
		every {
			organizationRepository.updateProfile(organization.id, null, null, listOf("Soccer"), "ops@example.com", null)
		} returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.update(organization.id, null, null, listOf("Soccer"), "ops@example.com", null, currentUser)

		assertEquals("ops@example.com", result.contactEmail)
		verify(exactly = 1) {
			auditService.record(currentUser.userId, organization.id, "organization.updated", "organization", organization.id, any())
		}
	}

	@Test
	fun `onboarding is incomplete until profile and a second administrator exist`() {
		val organization = existingOrganization()
		every { membershipService.requireActiveMembership(organization.id, currentUser) } returns ownerMembership(organization.id, currentUser.userId)
		every { organizationRepository.findById(organization.id) } returns organization
		every { membershipService.countMembers(organization.id) } returns 1

		val progress = service.onboardingProgress(organization.id, currentUser)

		assertEquals(false, progress.profileComplete)
		assertEquals(false, progress.hasAdditionalAdministrator)
	}

	@Test
	fun `onboarding is complete once profile and a second administrator exist`() {
		val organization = existingOrganization().copy(sports = listOf("Soccer"), contactEmail = "ops@example.com")
		every { membershipService.requireActiveMembership(organization.id, currentUser) } returns ownerMembership(organization.id, currentUser.userId)
		every { organizationRepository.findById(organization.id) } returns organization
		every { membershipService.countMembers(organization.id) } returns 2

		val progress = service.onboardingProgress(organization.id, currentUser)

		assertEquals(true, progress.profileComplete)
		assertEquals(true, progress.hasAdditionalAdministrator)
	}

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
