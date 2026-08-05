package com.rally26.household.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.domain.HouseholdStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
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

class HouseholdServiceTest {
    private val householdRepository = mockk<HouseholdRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val service = HouseholdService(householdRepository, membershipService, auditService, authorizationService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `list requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findAll(orgId, 0, 20) } returns emptyList()

        service.list(orgId, currentUser, 0, 20)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
    }

    @Test
    fun `get returns household for active member`() {
        val household = sampleHousehold()
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(household.id, orgId) } returns household
        every { authorizationService.hasHouseholdCapability(orgId, household.id, currentUser, Capabilities.HOUSEHOLD_VIEW) } returns true

        val result = service.get(orgId, household.id, currentUser)

        assertEquals(household.id, result.id)
    }

    @Test
    fun `get throws NotFoundException when household does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `create requires manager role and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val household = sampleHousehold()
        every {
            householdRepository.insert(
                orgId,
                household.displayName,
                household.contactEmail,
                household.contactPhone,
                household.notes,
            )
        } returns
            household
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.create(
                orgId,
                household.displayName,
                household.contactEmail,
                household.contactPhone,
                household.notes,
                currentUser,
            )

        assertEquals(household.id, result.id)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "household.created", "household", household.id, any()) }
    }

    @Test
    fun `update throws NotFoundException when household does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.update(orgId, UUID.randomUUID(), "New Name", null, null, null, currentUser)
        }
    }

    @Test
    fun `update records audit and returns updated household`() {
        val household = sampleHousehold()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(household.id, orgId) } returns household
        every { householdRepository.update(household.id, orgId, any(), any(), any(), any(), any(), any()) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.update(orgId, household.id, "Updated Name", null, null, null, currentUser)

        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "household.updated", "household", household.id, any()) }
    }

    @Test
    fun `listAdults throws NotFoundException when household does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.listAdults(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `addAdult requires manager role and records audit`() {
        val household = sampleHousehold()
        val adult = sampleAdult(household.id)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(household.id, orgId) } returns household
        every {
            householdRepository.insertAdult(
                household.id,
                orgId,
                adult.firstName,
                adult.lastName,
                adult.email,
                adult.phone,
                adult.relationship,
                adult.isPrimary,
            )
        } returns adult
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.addAdult(
                orgId,
                household.id,
                adult.firstName,
                adult.lastName,
                adult.email,
                adult.phone,
                adult.relationship,
                adult.isPrimary,
                currentUser,
            )

        assertEquals(adult.id, result.id)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "household.adult.added", "household_adult", adult.id, any()) }
    }

    @Test
    fun `removeAdult throws NotFoundException when adult does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.archiveAdult(any(), any(), orgId) } returns 0

        assertFailsWith<NotFoundException> {
            service.removeAdult(orgId, UUID.randomUUID(), UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `removeAdult records audit on success`() {
        val adultId = UUID.randomUUID()
        val householdId = UUID.randomUUID()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.archiveAdult(adultId, householdId, orgId) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.removeAdult(orgId, householdId, adultId, currentUser)

        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "household.adult.removed", "household_adult", adultId, any()) }
    }

    private fun sampleHousehold() =
        Household(
            id = UUID.randomUUID(),
            organizationId = orgId,
            displayName = "Smith Family",
            contactEmail = "smith@example.com",
            contactPhone = "555-0100",
            notes = null,
            emailRemindersOptOut = false,
            smsRemindersOptIn = false,
            status = HouseholdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleAdult(householdId: UUID) =
        HouseholdAdult(
            id = UUID.randomUUID(),
            householdId = householdId,
            organizationId = orgId,
            firstName = "Jane",
            lastName = "Smith",
            email = "jane@example.com",
            phone = null,
            relationship = "Parent",
            isPrimary = true,
            status = AdultStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun managerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
