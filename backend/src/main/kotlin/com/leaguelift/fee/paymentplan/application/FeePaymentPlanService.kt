package com.leaguelift.fee.paymentplan.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.paymentplan.domain.FeeInstallment
import com.leaguelift.fee.paymentplan.domain.FeeInstallmentStatus
import com.leaguelift.fee.paymentplan.domain.FeePaymentPlanDetails
import com.leaguelift.fee.paymentplan.domain.FeePaymentPlanStatus
import com.leaguelift.fee.paymentplan.domain.NewInstallment
import com.leaguelift.fee.paymentplan.persistence.FeePaymentPlanRepository
import com.leaguelift.fee.persistence.FeeAdjustmentRepository
import com.leaguelift.fee.persistence.FeePaymentRepository
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class FeePaymentPlanService(
    private val planRepository: FeePaymentPlanRepository,
    private val feeRepository: FeeRepository,
    private val feePaymentRepository: FeePaymentRepository,
    private val feeAdjustmentRepository: FeeAdjustmentRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun get(organizationId: UUID, assignmentId: UUID, currentUser: CurrentUser): FeePaymentPlanDetails? {
        membershipService.requireActiveMembership(organizationId, currentUser)
        feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        val plan = planRepository.findLatestByAssignment(organizationId, assignmentId) ?: return null
        return details(plan.id, organizationId)
    }

    @Transactional
    fun create(
        organizationId: UUID,
        assignmentId: UUID,
        installments: List<NewInstallment>,
        note: String?,
        currentUser: CurrentUser,
    ): FeePaymentPlanDetails {
        membershipService.requireManagerRole(organizationId, currentUser)
        val assignment = feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        if (assignment.status == FeeAssignmentStatus.WAIVED || assignment.status == FeeAssignmentStatus.CANCELLED || assignment.status == FeeAssignmentStatus.PAID) {
            throw ValidationException("A payment plan can only be created for an outstanding fee.")
        }
        if (planRepository.findActiveByAssignment(organizationId, assignmentId) != null) {
            throw ConflictException("PAYMENT_PLAN_ALREADY_ACTIVE", "This fee already has an active payment plan.")
        }
        if (installments.size !in 2..24) throw ValidationException("A payment plan must contain between 2 and 24 installments.")
        if (installments.any { it.amountMinor <= 0 }) throw ValidationException("Every installment amount must be greater than zero.")
        if (installments.zipWithNext().any { (a, b) -> b.dueDate.isBefore(a.dueDate) }) {
            throw ValidationException("Installment due dates must be in chronological order.")
        }
        val paid = feePaymentRepository.sumActiveByAssignment(assignmentId, organizationId)
        val adjusted = feeAdjustmentRepository.sumActiveByAssignment(assignmentId, organizationId)
        val balance = (assignment.originalAmountMinor - paid - adjusted).coerceAtLeast(0)
        if (balance <= 0) throw ValidationException("This fee has no outstanding balance.")
        val scheduled = installments.sumOf { it.amountMinor }
        if (scheduled != balance) {
            throw ValidationException("Installments must total the current outstanding balance of $balance minor units.")
        }
        val plan = planRepository.insertPlan(
            organizationId, assignmentId, assignment.householdId, scheduled, assignment.currency,
            note?.trim()?.takeIf { it.isNotEmpty() }?.take(1000), currentUser.userId,
        )
        installments.forEachIndexed { index, installment ->
            planRepository.insertInstallment(organizationId, plan.id, index + 1, installment.amountMinor, installment.dueDate)
        }
        auditService.record(currentUser.userId, organizationId, "fee_payment_plan.created", "fee_payment_plan", plan.id)
        return details(plan.id, organizationId)
    }

    @Transactional
    fun cancel(organizationId: UUID, assignmentId: UUID, reason: String, currentUser: CurrentUser): FeePaymentPlanDetails {
        membershipService.requireManagerRole(organizationId, currentUser)
        feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        val plan = planRepository.findActiveByAssignment(organizationId, assignmentId)
            ?: throw NotFoundException("ACTIVE_PAYMENT_PLAN_NOT_FOUND", "This fee does not have an active payment plan.")
        val normalizedReason = reason.trim()
        if (normalizedReason.length !in 1..500) throw ValidationException("A cancellation reason is required and must be 500 characters or fewer.")
        if (planRepository.cancel(organizationId, plan.id, currentUser.userId, normalizedReason) != 1) {
            throw ConflictException("PAYMENT_PLAN_CHANGED", "The payment plan changed before it could be cancelled.")
        }
        auditService.record(currentUser.userId, organizationId, "fee_payment_plan.cancelled", "fee_payment_plan", plan.id)
        return details(plan.id, organizationId)
    }

    private fun details(planId: UUID, organizationId: UUID): FeePaymentPlanDetails {
        val plan = planRepository.findByIdForUpdate(organizationId, planId)
            ?: throw NotFoundException("PAYMENT_PLAN_NOT_FOUND", "The payment plan could not be found.")
        val today = LocalDate.now()
        val installments = planRepository.listInstallments(organizationId, planId).map { row ->
            val status = when {
                plan.status == FeePaymentPlanStatus.CANCELLED -> FeeInstallmentStatus.CANCELLED
                row.paidMinor >= row.amountMinor -> FeeInstallmentStatus.PAID
                row.paidMinor > 0 -> FeeInstallmentStatus.PARTIALLY_PAID
                row.dueDate.isBefore(today) -> FeeInstallmentStatus.OVERDUE
                row.dueDate == today -> FeeInstallmentStatus.DUE
                else -> FeeInstallmentStatus.UPCOMING
            }
            FeeInstallment(
                row.id, row.organizationId, row.paymentPlanId, row.sequenceNumber,
                row.amountMinor, row.dueDate, row.paidMinor, status, row.createdAt,
            )
        }
        val paid = installments.sumOf { it.paidMinor }.coerceAtMost(plan.totalMinor)
        return FeePaymentPlanDetails(plan, installments, paid, (plan.totalMinor - paid).coerceAtLeast(0))
    }
}
