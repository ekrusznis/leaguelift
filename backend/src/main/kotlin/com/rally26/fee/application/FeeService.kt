package com.rally26.fee.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.util.CsvUtil
import com.rally26.common.web.CurrentUser
import com.rally26.fee.domain.AdjustmentType
import com.rally26.fee.domain.FeeAdjustment
import com.rally26.fee.domain.FeeAssignment
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.FeeAssignmentSummary
import com.rally26.fee.domain.FeeAssignmentWithBalance
import com.rally26.fee.domain.FeePayment
import com.rally26.fee.domain.FeeTemplate
import com.rally26.fee.domain.PaymentMethod
import com.rally26.fee.domain.computeFeeBalance
import com.rally26.fee.domain.resolveStatusAfterBalanceChange
import com.rally26.fee.infra.StripeFeePaymentCheckoutClient
import com.rally26.fee.paymentplan.persistence.FeePaymentPlanRepository
import com.rally26.fee.persistence.FeeAdjustmentRepository
import com.rally26.fee.persistence.FeePaymentRepository
import com.rally26.fee.persistence.FeeRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

private val log = LoggerFactory.getLogger(FeeService::class.java)

/** Mirrors `fundraising/application/ContributionService.kt`'s `{CONTRIBUTION_ID}` success-url placeholder convention. */
const val FEE_PAYMENT_ID_PLACEHOLDER = "{FEE_PAYMENT_ID}"

/** `fee_assignment.payment_confirmed_online` outbox payload — consumed by `FeePaymentReceiptEmailHandler`. */
data class FeePaymentConfirmedPayload(
    val payerEmail: String,
    val payerName: String?,
    val amountMinor: Long,
    val currency: String,
    val feeDescription: String,
    val organizationId: UUID,
    val householdId: UUID,
    val paidAt: String,
)

data class FeePaymentCheckout(
    val feePaymentId: UUID,
    val checkoutUrl: String,
)

@Service
class FeeService(
    private val feeRepository: FeeRepository,
    private val feePaymentRepository: FeePaymentRepository,
    private val feeAdjustmentRepository: FeeAdjustmentRepository,
    private val feePaymentPlanRepository: FeePaymentPlanRepository,
    private val householdRepository: HouseholdRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val authorizationService: AuthorizationService,
    private val stripeFeePaymentCheckoutClient: StripeFeePaymentCheckoutClient,
    private val ledgerService: LedgerService,
    private val outboxWriter: OutboxWriter,
    private val objectMapper: ObjectMapper,
) {
    // --- Fee Templates ---

    fun listTemplates(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FeeTemplate> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.findAllTemplates(organizationId, offset, limit)
    }

    fun countTemplates(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.countAllTemplates(organizationId)
    }

    fun getTemplate(
        organizationId: UUID,
        templateId: UUID,
        currentUser: CurrentUser,
    ): FeeTemplate {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return feeRepository.findTemplateById(templateId, organizationId)
            ?: throw NotFoundException("FEE_TEMPLATE_NOT_FOUND", "The fee template could not be found.")
    }

    @Transactional
    fun createTemplate(
        organizationId: UUID,
        name: String,
        description: String?,
        amountMinor: Long,
        currency: String,
        currentUser: CurrentUser,
    ): FeeTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        val template = feeRepository.insertTemplate(organizationId, name, description, amountMinor, currency)
        auditService.record(currentUser.userId, organizationId, "fee_template.created", "fee_template", template.id)
        return template
    }

    @Transactional
    fun updateTemplate(
        organizationId: UUID,
        templateId: UUID,
        name: String?,
        description: String?,
        amountMinor: Long?,
        currentUser: CurrentUser,
    ): FeeTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        feeRepository.findTemplateById(templateId, organizationId)
            ?: throw NotFoundException("FEE_TEMPLATE_NOT_FOUND", "The fee template could not be found.")
        feeRepository.updateTemplate(templateId, organizationId, name, description, amountMinor)
        auditService.record(currentUser.userId, organizationId, "fee_template.updated", "fee_template", templateId)
        return feeRepository.findTemplateById(templateId, organizationId)!!
    }

    @Transactional
    fun archiveTemplate(
        organizationId: UUID,
        templateId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = feeRepository.archiveTemplate(templateId, organizationId)
        if (rows == 0) throw NotFoundException("FEE_TEMPLATE_NOT_FOUND", "The fee template could not be found.")
        auditService.record(currentUser.userId, organizationId, "fee_template.archived", "fee_template", templateId)
    }

    // --- Fee Assignments ---

    /** Read-only, so open to a guardian viewing their own household's fees, not just org staff. */
    fun listForHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentWithBalance> {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_FEE_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's fees.")
        }
        return feeRepository.findByHousehold(householdId, organizationId, offset, limit).map(::withBalance)
    }

    /** Read-only — same guardian-or-staff access as [listForHousehold]. */
    fun countForHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): Long {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_FEE_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's fees.")
        }
        return feeRepository.countByHousehold(householdId, organizationId)
    }

    fun getAssignment(
        organizationId: UUID,
        assignmentId: UUID,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val assignment =
            feeRepository.findAssignmentById(assignmentId, organizationId)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        return withBalance(assignment)
    }

    @Transactional
    fun createAssignment(
        organizationId: UUID,
        householdId: UUID,
        participantId: UUID?,
        feeTemplateId: UUID?,
        description: String,
        originalAmountMinor: Long,
        currency: String,
        dueDate: LocalDate?,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireManagerRole(organizationId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val assignment =
            feeRepository.insertAssignment(
                organizationId,
                householdId,
                participantId,
                feeTemplateId,
                description,
                originalAmountMinor,
                currency,
                dueDate,
            )
        auditService.record(currentUser.userId, organizationId, "fee_assignment.created", "fee_assignment", assignment.id)
        return withBalance(assignment)
    }

    @Transactional
    fun updateAssignmentStatus(
        organizationId: UUID,
        assignmentId: UUID,
        status: FeeAssignmentStatus,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireManagerRole(organizationId, currentUser)
        feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        feeRepository.updateAssignmentStatus(assignmentId, organizationId, status)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.status_updated", "fee_assignment", assignmentId)
        return withBalance(feeRepository.findAssignmentById(assignmentId, organizationId)!!)
    }

    // --- Payments ---

    @Transactional
    fun recordPayment(
        organizationId: UUID,
        assignmentId: UUID,
        amountMinor: Long,
        method: PaymentMethod,
        paidAt: LocalDate,
        note: String?,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireManagerRole(organizationId, currentUser)
        val assignment = requireOpenAssignment(organizationId, assignmentId)
        val currentPaid = feePaymentRepository.sumActiveByAssignment(assignment.id, organizationId)
        val currentAdjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, organizationId)
        val currentBalance = (assignment.originalAmountMinor - currentPaid - currentAdjusted).coerceAtLeast(0)
        if (amountMinor <= 0 || amountMinor > currentBalance) {
            throw ValidationException(
                "Payment amount must be between 1 and the current outstanding balance of $currentBalance minor units.",
            )
        }
        val payment =
            feePaymentRepository.insert(
                organizationId,
                assignmentId,
                assignment.householdId,
                amountMinor,
                assignment.currency,
                method,
                paidAt,
                note,
                currentUser.userId,
            )
        allocateToActivePlan(organizationId, assignmentId, payment.id, amountMinor)
        val result = recomputeStatus(organizationId, assignment)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.payment_recorded", "fee_assignment", assignmentId)
        return result
    }

    /**
     * Guardian-initiated online payment via Stripe Checkout — the counterpart to
     * staff-only [recordPayment]. [Capabilities.HOUSEHOLD_FEE_PAY] is granted in
     * `CapabilityRegistry` but had no real call site before this. Confirmation
     * happens only via the Stripe webhook ([confirmOnlineCheckoutFromWebhook]),
     * mirroring `ContributionService.createCheckoutSession`'s own reasoning: a
     * guardian who pays and closes the tab before redirecting back must not leave
     * Stripe holding confirmed money this app never records.
     */
    @Transactional
    fun createOnlineCheckoutSession(
        organizationId: UUID,
        assignmentId: UUID,
        amountMinor: Long,
        currentUser: CurrentUser,
        successUrl: String,
        cancelUrl: String,
    ): FeePaymentCheckout {
        val assignment = requireOpenAssignment(organizationId, assignmentId)
        if (!authorizationService.hasHouseholdCapability(
                organizationId,
                assignment.householdId,
                currentUser,
                Capabilities.HOUSEHOLD_FEE_PAY,
            )
        ) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to pay this household's fees.")
        }
        val currentPaid = feePaymentRepository.sumActiveByAssignment(assignment.id, organizationId)
        val currentAdjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, organizationId)
        val currentBalance = (assignment.originalAmountMinor - currentPaid - currentAdjusted).coerceAtLeast(0)
        if (amountMinor <= 0 || amountMinor > currentBalance) {
            throw ValidationException(
                "Payment amount must be between 1 and the current outstanding balance of $currentBalance minor units.",
            )
        }
        return try {
            val provisional =
                feePaymentRepository.insertPendingOnline(
                    organizationId,
                    assignment.id,
                    assignment.householdId,
                    amountMinor,
                    assignment.currency,
                    LocalDate.now(),
                    currentUser.userId,
                    currentUser.email,
                    currentUser.displayName,
                )
            val resolvedSuccessUrl = successUrl.replace(FEE_PAYMENT_ID_PLACEHOLDER, provisional.id.toString())
            val session =
                stripeFeePaymentCheckoutClient.createFeePaymentCheckoutSession(
                    provisional.id,
                    amountMinor,
                    assignment.currency,
                    assignment.description,
                    resolvedSuccessUrl,
                    cancelUrl,
                )
            feePaymentRepository.attachStripeSession(provisional.id, session.sessionId)
            FeePaymentCheckout(feePaymentId = provisional.id, checkoutUrl = session.checkoutUrl)
        } catch (e: StripeException) {
            log.warn("Stripe checkout session creation failed for fee assignment {}: {}", assignmentId, e.message, e)
            throw ServiceUnavailableException(
                "FEE_PAYMENT_PROVIDER_UNAVAILABLE",
                "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
            )
        }
    }

    /** Idempotent: a duplicate webhook delivery or an already-confirmed payment is a safe no-op. */
    @Transactional
    fun confirmOnlineCheckoutFromWebhook(
        stripeSessionId: String,
        stripePaymentStatus: String,
        stripePaymentIntentId: String?,
    ): FeePayment? {
        val payment = feePaymentRepository.findByStripeCheckoutSessionId(stripeSessionId) ?: return null
        if (stripePaymentStatus != "paid") return payment
        val updated = feePaymentRepository.markConfirmed(payment.id, stripePaymentIntentId)
        if (updated > 0) {
            val assignment =
                feeRepository.findAssignmentById(payment.feeAssignmentId, payment.organizationId)
                    ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
            allocateToActivePlan(payment.organizationId, assignment.id, payment.id, payment.amountMinor)
            recomputeStatus(payment.organizationId, assignment)
            auditService.record(
                null,
                payment.organizationId,
                "fee_assignment.payment_confirmed_online",
                "fee_assignment",
                assignment.id,
            )
            ledgerService.recordConfirmedFeePayment(payment.organizationId, payment.id, payment.amountMinor, payment.currency)
            ledgerService.recordStripeProcessingFee(payment.organizationId, LedgerSourceType.FEE_PAYMENT, payment.id, payment.currency, stripePaymentIntentId)
            if (payment.payerEmail != null) {
                outboxWriter.write(
                    aggregateType = "fee_payment",
                    aggregateId = payment.id,
                    organizationId = payment.organizationId,
                    eventType = "fee_assignment.payment_confirmed_online",
                    payloadJson =
                        objectMapper.writeValueAsString(
                            FeePaymentConfirmedPayload(
                                payment.payerEmail,
                                payment.payerName,
                                payment.amountMinor,
                                payment.currency,
                                assignment.description,
                                payment.organizationId,
                                payment.householdId,
                                payment.paidAt.toString(),
                            ),
                        ),
                )
            }
        }
        return feePaymentRepository.findById(payment.id, payment.organizationId)
    }

    @Transactional
    fun voidPayment(
        organizationId: UUID,
        assignmentId: UUID,
        paymentId: UUID,
        voidReason: String,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireManagerRole(organizationId, currentUser)
        val assignment =
            feeRepository.findAssignmentById(assignmentId, organizationId)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        val payment =
            feePaymentRepository
                .findById(paymentId, organizationId)
                ?.takeIf { it.feeAssignmentId == assignmentId }
                ?: throw NotFoundException("FEE_PAYMENT_NOT_FOUND", "The payment could not be found.")
        if (payment.isVoided) throw ValidationException("This payment has already been voided.")
        feePaymentRepository.void(paymentId, organizationId, currentUser.userId, voidReason)
        feePaymentPlanRepository.findLatestByAssignment(organizationId, assignmentId)?.let {
            feePaymentPlanRepository.refreshStatus(organizationId, it.id)
        }
        val result = recomputeStatus(organizationId, assignment)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.payment_voided", "fee_assignment", assignmentId)
        return result
    }

    fun listPayments(
        organizationId: UUID,
        assignmentId: UUID,
        currentUser: CurrentUser,
    ): List<FeePayment> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        return feePaymentRepository.findAllByAssignment(assignmentId, organizationId)
    }

    /**
     * Read-only, household-capability-gated (not staff-only like [listPayments]) —
     * a guardian polls this after returning from Stripe Checkout to learn whether
     * their online payment has been confirmed by the webhook yet, mirroring
     * `ContributionService.getStatus`'s own poll-after-redirect purpose.
     */
    fun getPaymentStatus(
        organizationId: UUID,
        assignmentId: UUID,
        paymentId: UUID,
        currentUser: CurrentUser,
    ): FeePayment {
        val assignment =
            feeRepository.findAssignmentById(assignmentId, organizationId)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        if (!authorizationService.hasHouseholdCapability(
                organizationId,
                assignment.householdId,
                currentUser,
                Capabilities.HOUSEHOLD_FEE_VIEW,
            )
        ) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's fees.")
        }
        return feePaymentRepository
            .findById(paymentId, organizationId)
            ?.takeIf { it.feeAssignmentId == assignmentId }
            ?: throw NotFoundException("FEE_PAYMENT_NOT_FOUND", "The payment could not be found.")
    }

    // --- Adjustments (manual discounts/credits) ---

    @Transactional
    fun applyAdjustment(
        organizationId: UUID,
        assignmentId: UUID,
        adjustmentType: AdjustmentType,
        amountMinor: Long,
        reason: String?,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireManagerRole(organizationId, currentUser)
        return insertAdjustmentAndRecompute(organizationId, assignmentId, adjustmentType, amountMinor, reason, currentUser).second
    }

    /**
     * Phase 23 (DESIGN-DOC.md section 13/14.1): a guardian applying their own
     * household's available family credit against one of their own outstanding
     * fees — the same underlying `fee_adjustment` (CREDIT) mechanism `applyAdjustment`
     * above uses for manager-issued discounts, just gated by household capability
     * instead of manager role. Returns the created adjustment so the caller
     * (`FamilyCreditService`) can record which grant(s) funded it. The household-id
     * match check (assignment must belong to the caller's own household) is the
     * actual authorization boundary here, not just the capability check — a real
     * capability grant is org-membership/guardian-relationship scoped, not
     * fee-assignment scoped.
     */
    @Transactional
    fun applyCreditFromHousehold(
        organizationId: UUID,
        assignmentId: UUID,
        householdId: UUID,
        amountMinor: Long,
        currentUser: CurrentUser,
    ): Pair<FeeAdjustment, FeeAssignmentWithBalance> {
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_CREDIT_APPLY)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to apply credit for this household.")
        }
        val assignment =
            feeRepository.findAssignmentById(assignmentId, organizationId)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        if (assignment.householdId != householdId) {
            throw ValidationException("This fee does not belong to your household.")
        }
        return insertAdjustmentAndRecompute(
            organizationId,
            assignmentId,
            AdjustmentType.CREDIT,
            amountMinor,
            "Family credit applied by guardian",
            currentUser,
        )
    }

    private fun insertAdjustmentAndRecompute(
        organizationId: UUID,
        assignmentId: UUID,
        adjustmentType: AdjustmentType,
        amountMinor: Long,
        reason: String?,
        currentUser: CurrentUser,
    ): Pair<FeeAdjustment, FeeAssignmentWithBalance> {
        val assignment = requireOpenAssignment(organizationId, assignmentId)
        if (feePaymentPlanRepository.findActiveByAssignment(organizationId, assignmentId) != null) {
            throw ValidationException("Cancel and recreate the active payment plan before applying an adjustment.")
        }
        val adjustment =
            feeAdjustmentRepository.insert(
                organizationId,
                assignmentId,
                assignment.householdId,
                adjustmentType,
                amountMinor,
                assignment.currency,
                reason,
                currentUser.userId,
            )
        val result = recomputeStatus(organizationId, assignment)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.adjustment_applied", "fee_assignment", assignmentId)
        return adjustment to result
    }

    @Transactional
    fun voidAdjustment(
        organizationId: UUID,
        assignmentId: UUID,
        adjustmentId: UUID,
        voidReason: String,
        currentUser: CurrentUser,
    ): FeeAssignmentWithBalance {
        membershipService.requireManagerRole(organizationId, currentUser)
        val assignment =
            feeRepository.findAssignmentById(assignmentId, organizationId)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        val adjustment =
            feeAdjustmentRepository
                .findById(adjustmentId, organizationId)
                ?.takeIf { it.feeAssignmentId == assignmentId }
                ?: throw NotFoundException("FEE_ADJUSTMENT_NOT_FOUND", "The adjustment could not be found.")
        if (adjustment.isVoided) throw ValidationException("This adjustment has already been voided.")
        if (feePaymentPlanRepository.findActiveByAssignment(organizationId, assignmentId) != null) {
            throw ValidationException("Cancel and recreate the active payment plan before voiding an adjustment.")
        }
        feeAdjustmentRepository.void(adjustmentId, organizationId, currentUser.userId, voidReason)
        val result = recomputeStatus(organizationId, assignment)
        auditService.record(currentUser.userId, organizationId, "fee_assignment.adjustment_voided", "fee_assignment", assignmentId)
        return result
    }

    fun listAdjustments(
        organizationId: UUID,
        assignmentId: UUID,
        currentUser: CurrentUser,
    ): List<FeeAdjustment> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        feeRepository.findAssignmentById(assignmentId, organizationId)
            ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        return feeAdjustmentRepository.findAllByAssignment(assignmentId, organizationId)
    }

    // --- Org-wide (collections dashboard, CSV export, owner dashboard) ---

    fun listForOrganization(
        organizationId: UUID,
        status: FeeAssignmentStatus?,
        overdueOnly: Boolean,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<FeeAssignmentSummary> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return feeRepository.findAllForOrganization(organizationId, status, overdueOnly, offset, limit)
    }

    fun countForOrganization(
        organizationId: UUID,
        status: FeeAssignmentStatus?,
        overdueOnly: Boolean,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return feeRepository.countAllForOrganization(organizationId, status, overdueOnly)
    }

    /** Capped at 5000 rows — a hard export size ceiling, not pagination; revisit if a real org ever needs more. */
    fun exportCollectionsCsv(
        organizationId: UUID,
        status: FeeAssignmentStatus?,
        overdueOnly: Boolean,
        currentUser: CurrentUser,
    ): String {
        membershipService.requireManagerRole(organizationId, currentUser)
        val rows = feeRepository.findAllForOrganization(organizationId, status, overdueOnly, 0, 5000)
        return buildCsv(rows)
    }

    // --- Internal helpers ---

    private fun requireOpenAssignment(
        organizationId: UUID,
        assignmentId: UUID,
    ): FeeAssignment {
        val assignment =
            feeRepository.findAssignmentById(assignmentId, organizationId)
                ?: throw NotFoundException("FEE_ASSIGNMENT_NOT_FOUND", "The fee assignment could not be found.")
        if (assignment.status == FeeAssignmentStatus.WAIVED || assignment.status == FeeAssignmentStatus.CANCELLED) {
            throw ValidationException("Cannot record a payment or adjustment against a ${assignment.status} fee.")
        }
        return assignment
    }

    private fun withBalance(assignment: FeeAssignment): FeeAssignmentWithBalance {
        val paid = feePaymentRepository.sumActiveByAssignment(assignment.id, assignment.organizationId)
        val adjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, assignment.organizationId)
        return FeeAssignmentWithBalance(assignment, computeFeeBalance(assignment.originalAmountMinor, paid, adjusted))
    }

    private fun recomputeStatus(
        organizationId: UUID,
        assignment: FeeAssignment,
    ): FeeAssignmentWithBalance {
        val paid = feePaymentRepository.sumActiveByAssignment(assignment.id, organizationId)
        val adjusted = feeAdjustmentRepository.sumActiveByAssignment(assignment.id, organizationId)
        val balance = computeFeeBalance(assignment.originalAmountMinor, paid, adjusted)
        val newStatus = resolveStatusAfterBalanceChange(assignment.status, balance, paid > 0 || adjusted > 0)
        if (newStatus != assignment.status) {
            feeRepository.updateAssignmentStatus(assignment.id, organizationId, newStatus)
        }
        val finalAssignment = feeRepository.findAssignmentById(assignment.id, organizationId)!!
        return FeeAssignmentWithBalance(finalAssignment, balance)
    }

    private fun allocateToActivePlan(
        organizationId: UUID,
        assignmentId: UUID,
        paymentId: UUID,
        amountMinor: Long,
    ) {
        val plan = feePaymentPlanRepository.findActiveByAssignment(organizationId, assignmentId) ?: return
        var remaining = amountMinor
        for (installment in feePaymentPlanRepository.listInstallments(organizationId, plan.id)) {
            if (remaining <= 0) break
            val installmentRemaining = (installment.amountMinor - installment.paidMinor).coerceAtLeast(0)
            if (installmentRemaining == 0L) continue
            val allocated = minOf(remaining, installmentRemaining)
            feePaymentPlanRepository.allocatePayment(organizationId, plan.id, installment.id, paymentId, allocated)
            remaining -= allocated
        }
        if (remaining != 0L) {
            throw ValidationException("The payment exceeds the remaining active payment-plan schedule.")
        }
        feePaymentPlanRepository.refreshStatus(organizationId, plan.id)
    }

    private fun buildCsv(rows: List<FeeAssignmentSummary>): String {
        val header =
            listOf("Household", "Participant", "Description", "Original", "Paid", "Adjusted", "Balance", "Currency", "Due Date", "Status")
        val lines = mutableListOf(header.joinToString(",") { CsvUtil.escape(it) })
        for (row in rows) {
            lines +=
                listOf(
                    row.householdName,
                    row.participantName ?: "",
                    row.description,
                    CsvUtil.formatMinor(row.originalAmountMinor),
                    CsvUtil.formatMinor(row.paidMinor),
                    CsvUtil.formatMinor(row.adjustedMinor),
                    CsvUtil.formatMinor(row.balance.balanceMinor),
                    row.currency,
                    row.dueDate?.toString() ?: "",
                    row.status.name,
                ).joinToString(",") { CsvUtil.escape(it) }
        }
        return lines.joinToString("\r\n")
    }
}
