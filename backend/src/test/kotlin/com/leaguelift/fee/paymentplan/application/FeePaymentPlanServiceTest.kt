package com.leaguelift.fee.paymentplan.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.paymentplan.domain.FeePaymentPlan
import com.leaguelift.fee.paymentplan.domain.FeePaymentPlanStatus
import com.leaguelift.fee.paymentplan.domain.NewInstallment
import com.leaguelift.fee.paymentplan.persistence.FeePaymentPlanRepository
import com.leaguelift.fee.persistence.FeeAdjustmentRepository
import com.leaguelift.fee.persistence.FeePaymentRepository
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.membership.application.MembershipService
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

class FeePaymentPlanServiceTest {
    private val plans = mockk<FeePaymentPlanRepository>()
    private val fees = mockk<FeeRepository>()
    private val payments = mockk<FeePaymentRepository>()
    private val adjustments = mockk<FeeAdjustmentRepository>()
    private val membership = mockk<MembershipService>()
    private val audit = mockk<AuditService>()
    private val service = FeePaymentPlanService(plans, fees, payments, adjustments, membership, audit)
    private val organizationId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val now = Instant.parse("2026-08-01T16:00:00Z")

    @Test
    fun `create requires installments to equal the current fee balance`() {
        every { membership.requireManagerRole(organizationId, user) } returns mockk()
        every { fees.findAssignmentById(assignmentId, organizationId) } returns assignment()
        every { plans.findActiveByAssignment(organizationId, assignmentId) } returns null
        every { payments.sumActiveByAssignment(assignmentId, organizationId) } returns 2000
        every { adjustments.sumActiveByAssignment(assignmentId, organizationId) } returns 0

        assertFailsWith<ValidationException> {
            service.create(
                organizationId, assignmentId,
                listOf(NewInstallment(3000, LocalDate.parse("2026-09-01")), NewInstallment(3000, LocalDate.parse("2026-10-01"))),
                null, user,
            )
        }
    }

    @Test
    fun `create persists an ordered plan and records audit`() {
        val plan = FeePaymentPlan(
            UUID.randomUUID(), organizationId, assignmentId, householdId, FeePaymentPlanStatus.ACTIVE,
            8000, "USD", null, user.userId, null, null, null, now, now,
        )
        every { membership.requireManagerRole(organizationId, user) } returns mockk()
        every { fees.findAssignmentById(assignmentId, organizationId) } returns assignment()
        every { plans.findActiveByAssignment(organizationId, assignmentId) } returns null
        every { payments.sumActiveByAssignment(assignmentId, organizationId) } returns 2000
        every { adjustments.sumActiveByAssignment(assignmentId, organizationId) } returns 0
        every { plans.insertPlan(organizationId, assignmentId, householdId, 8000, "USD", null, user.userId) } returns plan
        every { plans.insertInstallment(any(), any(), any(), any(), any()) } returnsMany listOf(UUID.randomUUID(), UUID.randomUUID())
        every { plans.findByIdForUpdate(organizationId, plan.id) } returns plan
        every { plans.listInstallments(organizationId, plan.id) } returns emptyList()
        every { audit.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(
            organizationId, assignmentId,
            listOf(NewInstallment(4000, LocalDate.parse("2026-09-01")), NewInstallment(4000, LocalDate.parse("2026-10-01"))),
            null, user,
        )

        assertEquals(plan.id, result.plan.id)
        verify(exactly = 2) { plans.insertInstallment(organizationId, plan.id, any(), 4000, any()) }
        verify(exactly = 1) { audit.record(user.userId, organizationId, "fee_payment_plan.created", "fee_payment_plan", plan.id, any()) }
    }

    private fun assignment() = FeeAssignment(
        assignmentId, organizationId, householdId, null, null, "Club dues", 10000, "USD",
        LocalDate.parse("2026-09-01"), FeeAssignmentStatus.PARTIALLY_PAID, now, now,
    )
}
