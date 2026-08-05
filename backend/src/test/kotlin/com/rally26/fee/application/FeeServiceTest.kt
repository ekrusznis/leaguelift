package com.rally26.fee.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fee.domain.AdjustmentType
import com.rally26.fee.domain.FeeAdjustment
import com.rally26.fee.domain.FeeAssignment
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.FeePayment
import com.rally26.fee.domain.FeeTemplate
import com.rally26.fee.domain.FeeTemplateStatus
import com.rally26.fee.domain.PaymentMethod
import com.rally26.fee.paymentplan.persistence.FeePaymentPlanRepository
import com.rally26.fee.persistence.FeeAdjustmentRepository
import com.rally26.fee.persistence.FeePaymentRepository
import com.rally26.fee.persistence.FeeRepository
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
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeeServiceTest {
    private val feeRepository = mockk<FeeRepository>()
    private val feePaymentRepository = mockk<FeePaymentRepository>()
    private val feeAdjustmentRepository = mockk<FeeAdjustmentRepository>()
    private val feePaymentPlanRepository = mockk<FeePaymentPlanRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val service =
        FeeService(
            feeRepository,
            feePaymentRepository,
            feeAdjustmentRepository,
            feePaymentPlanRepository,
            householdRepository,
            membershipService,
            auditService,
            authorizationService,
        )

    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    // --- Fee Template tests ---

    init {
        every { feePaymentPlanRepository.findActiveByAssignment(any(), any()) } returns null
        every { feePaymentPlanRepository.findLatestByAssignment(any(), any()) } returns null
    }

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
        every { feeRepository.insertTemplate(orgId, template.name, template.description, template.amountMinor, template.currency) } returns
            template
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.createTemplate(
                orgId,
                template.name,
                template.description,
                template.amountMinor,
                template.currency,
                currentUser,
            )

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
            feeRepository.insertAssignment(
                orgId,
                householdId,
                assignment.participantId,
                assignment.feeTemplateId,
                assignment.description,
                assignment.originalAmountMinor,
                assignment.currency,
                assignment.dueDate,
            )
        } returns assignment
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        stubZeroBalance(assignment.id)

        val result =
            service.createAssignment(
                orgId,
                householdId,
                assignment.participantId,
                assignment.feeTemplateId,
                assignment.description,
                assignment.originalAmountMinor,
                assignment.currency,
                assignment.dueDate,
                currentUser,
            )

        assertEquals(assignment.id, result.assignment.id)
        assertEquals(15000L, result.balance.balanceMinor)
        verify(
            exactly = 1,
        ) { auditService.record(currentUser.userId, orgId, "fee_assignment.created", "fee_assignment", assignment.id, any()) }
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
        stubZeroBalance(assignment.id)

        val result = service.updateAssignmentStatus(orgId, assignment.id, FeeAssignmentStatus.PAID, currentUser)

        assertEquals(FeeAssignmentStatus.PAID, result.assignment.status)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "fee_assignment.status_updated", "fee_assignment", assignment.id, any())
        }
    }

    // --- Payments ---

    @Test
    fun `recordPayment requires manager role`() {
        val assignment = sampleAssignment()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment
        every {
            feePaymentRepository.insert(
                orgId,
                assignment.id,
                householdId,
                5000L,
                "USD",
                PaymentMethod.CASH,
                LocalDate.of(2026, 1, 1),
                null,
                currentUser.userId,
            )
        } returns samplePayment(assignment.id)
        every { feePaymentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 5000L
        every { feeAdjustmentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 0L
        every { feeRepository.updateAssignmentStatus(assignment.id, orgId, FeeAssignmentStatus.PARTIALLY_PAID) } returns 1
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returnsMany
            listOf(assignment, assignment.copy(status = FeeAssignmentStatus.PARTIALLY_PAID))
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.recordPayment(orgId, assignment.id, 5000L, PaymentMethod.CASH, LocalDate.of(2026, 1, 1), null, currentUser)

        assertEquals(FeeAssignmentStatus.PARTIALLY_PAID, result.assignment.status)
        assertEquals(10000L, result.balance.balanceMinor)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "fee_assignment.payment_recorded", "fee_assignment", assignment.id, any())
        }
    }

    @Test
    fun `recordPayment transitions to PAID when balance reaches zero`() {
        val assignment = sampleAssignment()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returnsMany
            listOf(assignment, assignment.copy(status = FeeAssignmentStatus.PAID))
        every { feePaymentRepository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            samplePayment(assignment.id)
        every { feePaymentRepository.sumActiveByAssignment(assignment.id, orgId) } returnsMany listOf(0L, 15000L)
        every { feeAdjustmentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 0L
        every { feeRepository.updateAssignmentStatus(assignment.id, orgId, FeeAssignmentStatus.PAID) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.recordPayment(orgId, assignment.id, 15000L, PaymentMethod.CHECK, LocalDate.of(2026, 1, 1), null, currentUser)

        assertEquals(0L, result.balance.balanceMinor)
        assertEquals(FeeAssignmentStatus.PAID, result.assignment.status)
        verify(exactly = 1) { feeRepository.updateAssignmentStatus(assignment.id, orgId, FeeAssignmentStatus.PAID) }
    }

    @Test
    fun `recordPayment rejects an additional payment when the balance is already zero`() {
        val assignment = sampleAssignment().copy(status = FeeAssignmentStatus.PAID)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment
        every { feePaymentRepository.sumActiveByAssignment(assignment.id, orgId) } returns assignment.originalAmountMinor
        every { feeAdjustmentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 0L

        assertFailsWith<ValidationException> {
            service.recordPayment(orgId, assignment.id, 1L, PaymentMethod.CASH, LocalDate.of(2026, 1, 1), null, currentUser)
        }
        verify(exactly = 0) { feePaymentRepository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recordPayment is blocked on a WAIVED assignment`() {
        val assignment = sampleAssignment().copy(status = FeeAssignmentStatus.WAIVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment

        assertFailsWith<ValidationException> {
            service.recordPayment(orgId, assignment.id, 5000L, PaymentMethod.CASH, LocalDate.of(2026, 1, 1), null, currentUser)
        }
    }

    @Test
    fun `recordPayment is blocked on a CANCELLED assignment`() {
        val assignment = sampleAssignment().copy(status = FeeAssignmentStatus.CANCELLED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment

        assertFailsWith<ValidationException> {
            service.recordPayment(orgId, assignment.id, 5000L, PaymentMethod.CASH, LocalDate.of(2026, 1, 1), null, currentUser)
        }
    }

    @Test
    fun `voidPayment reverts status when balance is no longer covered`() {
        val assignment = sampleAssignment().copy(status = FeeAssignmentStatus.PAID)
        val payment = samplePayment(assignment.id)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returnsMany
            listOf(assignment, assignment.copy(status = FeeAssignmentStatus.OPEN))
        every { feePaymentRepository.findById(payment.id, orgId) } returns payment
        every { feePaymentRepository.void(payment.id, orgId, currentUser.userId, "Entered in error") } returns 1
        every { feePaymentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 0L
        every { feeAdjustmentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 0L
        every { feeRepository.updateAssignmentStatus(assignment.id, orgId, FeeAssignmentStatus.OPEN) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.voidPayment(orgId, assignment.id, payment.id, "Entered in error", currentUser)

        assertEquals(FeeAssignmentStatus.OPEN, result.assignment.status)
        assertEquals(15000L, result.balance.balanceMinor)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "fee_assignment.payment_voided", "fee_assignment", assignment.id, any())
        }
    }

    @Test
    fun `voidPayment throws ValidationException when already voided`() {
        val assignment = sampleAssignment()
        val payment = samplePayment(assignment.id).copy(voidedAt = Instant.now())
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment
        every { feePaymentRepository.findById(payment.id, orgId) } returns payment

        assertFailsWith<ValidationException> {
            service.voidPayment(orgId, assignment.id, payment.id, "Already voided", currentUser)
        }
    }

    // --- Adjustments ---

    @Test
    fun `applyAdjustment requires manager role and records audit`() {
        val assignment = sampleAssignment()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returnsMany
            listOf(assignment, assignment.copy(status = FeeAssignmentStatus.PARTIALLY_PAID))
        every {
            feeAdjustmentRepository.insert(
                orgId,
                assignment.id,
                householdId,
                AdjustmentType.DISCOUNT,
                5000L,
                "USD",
                "Sibling discount",
                currentUser.userId,
            )
        } returns sampleAdjustment(assignment.id)
        every { feePaymentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 0L
        every { feeAdjustmentRepository.sumActiveByAssignment(assignment.id, orgId) } returns 5000L
        every { feeRepository.updateAssignmentStatus(assignment.id, orgId, FeeAssignmentStatus.PARTIALLY_PAID) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.applyAdjustment(orgId, assignment.id, AdjustmentType.DISCOUNT, 5000L, "Sibling discount", currentUser)

        assertEquals(10000L, result.balance.balanceMinor)
        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "fee_assignment.adjustment_applied", "fee_assignment", assignment.id, any())
        }
    }

    @Test
    fun `applyAdjustment is blocked on a CANCELLED assignment`() {
        val assignment = sampleAssignment().copy(status = FeeAssignmentStatus.CANCELLED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment

        assertFailsWith<ValidationException> {
            service.applyAdjustment(orgId, assignment.id, AdjustmentType.CREDIT, 1000L, null, currentUser)
        }
    }

    @Test
    fun `voidAdjustment throws NotFoundException for an adjustment on a different assignment`() {
        val assignment = sampleAssignment()
        val otherAssignmentId = UUID.randomUUID()
        val adjustment = sampleAdjustment(otherAssignmentId)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAssignmentById(assignment.id, orgId) } returns assignment
        every { feeAdjustmentRepository.findById(adjustment.id, orgId) } returns adjustment

        assertFailsWith<NotFoundException> {
            service.voidAdjustment(orgId, assignment.id, adjustment.id, "Mistake", currentUser)
        }
    }

    // --- Org-wide listing ---

    @Test
    fun `listForOrganization requires manager role`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { feeRepository.findAllForOrganization(orgId, null, false, 0, 20) } returns emptyList()

        service.listForOrganization(orgId, null, false, currentUser, 0, 20)

        verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
    }

    private fun stubZeroBalance(assignmentId: UUID) {
        every { feePaymentRepository.sumActiveByAssignment(assignmentId, orgId) } returns 0L
        every { feeAdjustmentRepository.sumActiveByAssignment(assignmentId, orgId) } returns 0L
    }

    private fun sampleTemplate() =
        FeeTemplate(
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

    private fun sampleAssignment() =
        FeeAssignment(
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

    private fun samplePayment(feeAssignmentId: UUID) =
        FeePayment(
            id = UUID.randomUUID(),
            organizationId = orgId,
            feeAssignmentId = feeAssignmentId,
            householdId = householdId,
            amountMinor = 5000L,
            currency = "USD",
            method = PaymentMethod.CASH,
            paidAt = LocalDate.of(2026, 1, 1),
            note = null,
            recordedByUserId = currentUser.userId,
            voidedAt = null,
            voidedByUserId = null,
            voidReason = null,
            createdAt = Instant.now(),
        )

    private fun sampleAdjustment(feeAssignmentId: UUID) =
        FeeAdjustment(
            id = UUID.randomUUID(),
            organizationId = orgId,
            feeAssignmentId = feeAssignmentId,
            householdId = householdId,
            adjustmentType = AdjustmentType.DISCOUNT,
            amountMinor = 5000L,
            currency = "USD",
            reason = "Sibling discount",
            createdByUserId = currentUser.userId,
            voidedAt = null,
            voidedByUserId = null,
            voidReason = null,
            createdAt = Instant.now(),
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
