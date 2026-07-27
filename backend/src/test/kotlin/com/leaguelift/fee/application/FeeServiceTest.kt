package com.leaguelift.fee.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.FeeTemplate
import com.leaguelift.fee.domain.FeeTemplateStatus
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeeServiceTest {

    private val feeRepository = mockk<FeeRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = FeeService(feeRepository, householdRepository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "sub-manager", "manager@example.com", "Manager")

    // --- Fee Template tests ---

    @Test
    fun `listTemplates requires active membership`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAllTemplates(orgId, 0, 20) } returns emptyList()

        service.listTemplates(orgId, currentUser, 0, 20)

        verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
    }

    @Test
    fun `createTemplate requires manager role and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val template = sampleTemplate()
        every { feeRepository.insertTemplate(orgId, template.name, template.description, template.amountMinor, template.currency) } returns template
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.createTemplate(orgId, template.name, template.description, template.amountMinor, template.currency, currentUser)

        assertEquals(template.id, result.id)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "fee_template.created", "fee_template", template.id, any()) }
    }

    @Test
    fun `getTemplate throws NotFoundException when template does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findTemplateById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.getTemplate(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `updateTemplate throws NotFoundException when template does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findTemplateById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.updateTemplate(orgId, UUID.randomUUID(), null, null, null, currentUser)
        }
    }

    @Test
    fun `archiveTemplate records audit on success`() {
        val templateId = UUID.randomUUID()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.archiveTemplate(templateId, orgId) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.archiveTemplate(orgId, templateId, currentUser)

        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "fee_template.archived", "fee_template", templateId, any()) }
    }

    @Test
    fun `archiveTemplate throws NotFoundException when template does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.archiveTemplate(any(), orgId) } returns 0

        assertFailsWith<NotFoundException> {
            service.archiveTemplate(orgId, UUID.randomUUID(), currentUser)
        }
    }

    // --- Fee Assignment tests ---

    @Test
    fun `listForHousehold throws NotFoundException when household does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(householdId, orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.listForHousehold(orgId, householdId, currentUser, 0, 20)
        }
    }

    @Test
    fun `createAssignment requires manager role and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { householdRepository.findById(householdId, orgId) } returns mockk()
        val assignment = sampleAssignment()
        every {
            feeRepository.insertAssignment(orgId, householdId, assignment.participantId, assignment.feeTemplateId, assignment.description, assignment.originalAmountMinor, assignment.currency, assignment.dueDate)
        } returns assignment
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.createAssignment(
            orgId, householdId, assignment.participantId, assignment.feeTemplateId,
            assignment.description, assignment.originalAmountMinor, assignment.currency, assignment.dueDate, currentUser,
        )

        assertEquals(assignment.id, result.id)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "fee_assignment.created", "fee_assignment", assignment.id, any()) }
    }

    @Test
    fun `updateAssignmentStatus throws NotFoundException when assignment does not exist`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.updateAssignmentStatus(orgId, UUID.randomUUID(), FeeAssignmentStatus.PAID, currentUser)
        }
    }

    @Test
    fun `updateAssignmentStatus records audit on success`() {
        val assignment = sampleAssignment()
        val updated = assignment.copy(status = FeeAssignmentStatus.PAID)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment andThen updated
        every { feeRepository.updateAssignmentStatus(assignment.id, orgId, FeeAssignmentStatus.PAID) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateAssignmentStatus(orgId, assignment.id, FeeAssignmentStatus.PAID, currentUser)

        assertEquals(FeeAssignmentStatus.PAID, result.status)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "fee_assignment.status_updated", "fee_assignment", assignment.id, any()) }
    }

    private fun sampleTemplate() = FeeTemplate(
        id = UUID.randomUUID(),
        organizationId = orgId,
        name = "Spring Registration",
        description = "Annual spring season fee",
        amountMinor = 15000L,
        currency = "USD",
        status = FeeTemplateStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun sampleAssignment() = FeeAssignment(
        id = UUID.randomUUID(),
        organizationId = orgId,
        householdId = householdId,
        participantId = null,
        feeTemplateId = null,
        description = "Spring 2026 Registration",
        originalAmountMinor = 15000L,
        currency = "USD",
        dueDate = LocalDate.of(2026, 3, 1),
        status = FeeAssignmentStatus.OPEN,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun managerMembership() = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = orgId,
        userId = currentUser.userId,
        role = MembershipRole.ADMINISTRATOR,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
