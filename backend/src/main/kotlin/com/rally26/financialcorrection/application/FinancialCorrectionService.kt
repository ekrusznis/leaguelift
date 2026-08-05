package com.rally26.financialcorrection.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.finance.domain.PaymentSource
import com.rally26.financialcorrection.domain.FinancialCorrection
import com.rally26.financialcorrection.domain.FinancialCorrectionPreview
import com.rally26.financialcorrection.domain.FinancialCorrectionTargetType
import com.rally26.financialcorrection.domain.FinancialCorrectionType
import com.rally26.financialcorrection.persistence.FinancialCorrectionRepository
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.fundraising.infra.StripeCheckoutClient
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.membership.application.MembershipService
import com.rally26.offlinefinance.domain.OfflineFinancialRecordType
import com.rally26.offlinefinance.domain.OfflineVerificationStatus
import com.rally26.offlinefinance.persistence.OfflineFinancialRecordRepository
import com.rally26.order.domain.OrderStatus
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.order.persistence.OrderItemRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.sponsorship.domain.SponsorshipStatus
import com.rally26.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.rally26.sponsorship.persistence.SponsorshipRepository
import com.stripe.exception.StripeException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val CORRECTION_REFUND_WINDOW: Duration = Duration.ofDays(14)

private data class ResolvedCorrectionTarget(
    val correctionType: FinancialCorrectionType,
    val targetType: FinancialCorrectionTargetType,
    val targetId: UUID,
    val label: String,
    val paymentSource: String,
    val originalAmountMinor: Long,
    val currency: String,
    val paymentIntentId: String?,
    val confirmedAt: Instant?,
    val ledgerSourceType: LedgerSourceType,
    val ledgerSourceId: UUID,
)

@Service
class FinancialCorrectionService(
    private val repository: FinancialCorrectionRepository,
    private val contributionRepository: ContributionRepository,
    private val sponsorshipRepository: SponsorshipRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val offlineFinancialRecordRepository: OfflineFinancialRecordRepository,
    private val stripeCheckoutClient: StripeCheckoutClient,
    private val stripeSponsorshipCheckoutClient: StripeSponsorshipCheckoutClient,
    private val stripeOrderCheckoutClient: StripeOrderCheckoutClient,
    private val ledgerService: LedgerService,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun preview(
        organizationId: UUID,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
        requestedAmountMinor: Long?,
        reason: String,
        currentUser: CurrentUser,
    ): FinancialCorrectionPreview {
        membershipService.requireManagerRole(organizationId, currentUser)
        return previewInternal(organizationId, targetType, targetId, requestedAmountMinor, reason)
    }

    @Transactional
    fun execute(
        organizationId: UUID,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
        requestedAmountMinor: Long?,
        reason: String,
        confirmationHash: String,
        idempotencyKey: String,
        currentUser: CurrentUser,
    ): FinancialCorrection {
        membershipService.requireManagerRole(organizationId, currentUser)
        val normalizedKey = idempotencyKey.trim()
        if (normalizedKey.length !in 8..120) throw ValidationException("Idempotency key must be between 8 and 120 characters.")
        findIdempotentResult(organizationId, normalizedKey, confirmationHash)?.let { return it }
        repository.lockTarget(organizationId, targetType, targetId)
        // Re-check after acquiring the target lock. A concurrent request may have completed while this request waited.
        findIdempotentResult(organizationId, normalizedKey, confirmationHash)?.let { return it }
        val preview = previewInternal(organizationId, targetType, targetId, requestedAmountMinor, reason)
        if (preview.confirmationHash != confirmationHash) {
            throw ConflictException("CORRECTION_PREVIEW_STALE", "The financial record changed after preview. Preview the correction again.")
        }
        val resolved = resolveTarget(organizationId, targetType, targetId)
        val providerReference =
            if (resolved.correctionType == FinancialCorrectionType.REFUND) {
                createProviderRefund(resolved, preview.requestedAmountMinor, "financial-correction:$organizationId:$normalizedKey")
            } else {
                null
            }
        val correction =
            repository.insert(
                organizationId = organizationId,
                correctionType = resolved.correctionType,
                targetType = targetType,
                targetId = targetId,
                amountMinor = preview.requestedAmountMinor,
                currency = preview.currency,
                reason = normalizeReason(reason),
                providerReference = providerReference,
                confirmationHash = confirmationHash,
                idempotencyKey = normalizedKey,
                createdByUserId = currentUser.userId,
            )
        if (resolved.correctionType == FinancialCorrectionType.REFUND) {
            ledgerService.recordCorrectionRefund(
                organizationId,
                correction.id,
                correction.amountMinor,
                correction.currency,
                providerReference ?: error("A provider refund must have a provider reference."),
            )
            if (preview.willFullyCorrect) markOnlineSourceRefunded(resolved)
        } else {
            ledgerService.reverseOfflineSource(
                organizationId,
                resolved.ledgerSourceType,
                resolved.ledgerSourceId,
                correction.id,
                correction.reason,
            )
            markOfflineSourceReversed(organizationId, targetId, resolved, correction.reason, currentUser)
        }
        auditService.record(
            currentUser.userId,
            organizationId,
            "financial_correction.executed",
            "financial_correction",
            correction.id,
            "{\"targetType\":\"${targetType.name}\",\"targetId\":\"$targetId\",\"amountMinor\":${correction.amountMinor}}",
        )
        return correction
    }

    fun list(
        organizationId: UUID,
        offset: Int,
        limit: Int,
        currentUser: CurrentUser,
    ): List<FinancialCorrection> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.list(organizationId, offset, limit.coerceIn(1, 100))
    }

    fun count(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.count(organizationId)
    }

    private fun findIdempotentResult(
        organizationId: UUID,
        idempotencyKey: String,
        confirmationHash: String,
    ): FinancialCorrection? {
        val existing = repository.findByIdempotencyKey(organizationId, idempotencyKey) ?: return null
        if (existing.confirmationHash != confirmationHash) {
            throw ConflictException("CORRECTION_IDEMPOTENCY_CONFLICT", "This idempotency key was already used for a different correction.")
        }
        return existing
    }

    private fun previewInternal(
        organizationId: UUID,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
        requestedAmountMinor: Long?,
        reason: String,
    ): FinancialCorrectionPreview {
        val normalizedReason = normalizeReason(reason)
        val resolved = resolveTarget(organizationId, targetType, targetId)
        val previouslyCorrected = repository.sumByTarget(organizationId, targetType, targetId)
        val remaining = resolved.originalAmountMinor - previouslyCorrected
        if (remaining <= 0) throw ValidationException("This financial record has already been fully corrected.")
        val requested = requestedAmountMinor ?: remaining
        if (requested <= 0 || requested > remaining) {
            throw ValidationException("Correction amount must be between 1 and the remaining correctable amount of $remaining minor units.")
        }
        if (resolved.correctionType == FinancialCorrectionType.REVERSAL && requested != remaining) {
            throw ValidationException("A verified offline financial record can only be reversed in full.")
        }
        if (resolved.correctionType == FinancialCorrectionType.REFUND) {
            val confirmedAt = resolved.confirmedAt ?: throw ValidationException("The source record has no confirmation date.")
            if (Duration.between(confirmedAt, Instant.now()) > CORRECTION_REFUND_WINDOW) {
                throw ValidationException("This payment is outside the ${CORRECTION_REFUND_WINDOW.toDays()}-day refund window.")
            }
        }
        val remainingAfter = remaining - requested
        val warnings =
            buildList {
                if (resolved.correctionType == FinancialCorrectionType.REFUND) {
                    add("The payment provider is called only after this preview is confirmed.")
                    add("The Rally26 platform fee is not returned; the organization earning reversal follows the existing ledger policy.")
                    if (remainingAfter > 0) add("This is a partial refund. The source record will remain confirmed.")
                } else {
                    add("The original offline record and ledger entries remain visible; opposite ledger entries will be appended.")
                    if (resolved.targetType == FinancialCorrectionTargetType.OFFLINE_FINANCIAL_RECORD) {
                        add("Any linked manual fulfillment must still be reviewed separately if work has already started.")
                    }
                }
            }
        val hash =
            sha256(
                listOf(
                    organizationId,
                    resolved.correctionType,
                    targetType,
                    targetId,
                    resolved.paymentSource,
                    resolved.originalAmountMinor,
                    previouslyCorrected,
                    requested,
                    resolved.currency,
                    normalizedReason,
                    resolved.paymentIntentId,
                    resolved.confirmedAt,
                    resolved.ledgerSourceType,
                    resolved.ledgerSourceId,
                ).joinToString("|"),
            )
        return FinancialCorrectionPreview(
            resolved.correctionType,
            targetType,
            targetId,
            resolved.label,
            resolved.paymentSource,
            resolved.originalAmountMinor,
            previouslyCorrected,
            requested,
            remainingAfter,
            resolved.currency,
            remainingAfter == 0L,
            warnings,
            hash,
        )
    }

    private fun resolveTarget(
        organizationId: UUID,
        targetType: FinancialCorrectionTargetType,
        targetId: UUID,
    ): ResolvedCorrectionTarget =
        when (targetType) {
            FinancialCorrectionTargetType.CONTRIBUTION -> {
                val item =
                    contributionRepository
                        .findById(targetId)
                        ?.takeIf { it.organizationId == organizationId }
                        ?: throw NotFoundException("CONTRIBUTION_NOT_FOUND", "The contribution could not be found.")
                if (item.paymentSource != PaymentSource.STRIPE ||
                    item.status != ContributionStatus.CONFIRMED ||
                    item.stripePaymentIntentId == null
                ) {
                    throw ValidationException("Only a confirmed Stripe contribution can use the refund workflow.")
                }
                ResolvedCorrectionTarget(
                    FinancialCorrectionType.REFUND,
                    targetType,
                    targetId,
                    "Campaign contribution",
                    item.paymentSource.name,
                    item.amountMinor,
                    item.currency,
                    item.stripePaymentIntentId,
                    item.confirmedAt,
                    LedgerSourceType.CONTRIBUTION,
                    item.id,
                )
            }
            FinancialCorrectionTargetType.SPONSORSHIP -> {
                val item =
                    sponsorshipRepository
                        .findById(targetId)
                        ?.takeIf { it.organizationId == organizationId }
                        ?: throw NotFoundException("SPONSORSHIP_NOT_FOUND", "The sponsorship could not be found.")
                if (item.paymentSource != PaymentSource.STRIPE ||
                    item.status != SponsorshipStatus.CONFIRMED ||
                    item.stripePaymentIntentId == null
                ) {
                    throw ValidationException("Only a confirmed Stripe sponsorship can use the refund workflow.")
                }
                ResolvedCorrectionTarget(
                    FinancialCorrectionType.REFUND,
                    targetType,
                    targetId,
                    "Sponsorship purchase",
                    item.paymentSource.name,
                    item.amountMinor,
                    item.currency,
                    item.stripePaymentIntentId,
                    item.confirmedAt,
                    LedgerSourceType.SPONSORSHIP,
                    item.id,
                )
            }
            FinancialCorrectionTargetType.ORDER -> {
                val item =
                    orderRepository.findById(targetId, organizationId)
                        ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")
                if (item.paymentSource != PaymentSource.STRIPE ||
                    item.status != OrderStatus.CONFIRMED ||
                    item.stripePaymentIntentId == null
                ) {
                    throw ValidationException("Only a confirmed Stripe order can use the refund workflow.")
                }
                val gross = orderItemRepository.findByOrder(item.id).sumOf { it.unitPriceMinor * it.quantity }
                ResolvedCorrectionTarget(
                    FinancialCorrectionType.REFUND,
                    targetType,
                    targetId,
                    "Store order",
                    item.paymentSource.name,
                    gross,
                    item.currency,
                    item.stripePaymentIntentId,
                    item.confirmedAt,
                    LedgerSourceType.ORDER,
                    item.id,
                )
            }
            FinancialCorrectionTargetType.OFFLINE_FINANCIAL_RECORD -> {
                val record =
                    offlineFinancialRecordRepository.findById(targetId, organizationId)
                        ?: throw NotFoundException("OFFLINE_FINANCIAL_RECORD_NOT_FOUND", "The offline financial record could not be found.")
                if (record.verificationStatus != OfflineVerificationStatus.VERIFIED) {
                    throw ValidationException("Only a verified offline financial record can be reversed.")
                }
                val sourceType =
                    when (record.recordType) {
                        OfflineFinancialRecordType.CONTRIBUTION -> LedgerSourceType.CONTRIBUTION
                        OfflineFinancialRecordType.SPONSORSHIP -> LedgerSourceType.SPONSORSHIP
                        OfflineFinancialRecordType.ORDER -> LedgerSourceType.ORDER
                    }
                ResolvedCorrectionTarget(
                    FinancialCorrectionType.REVERSAL,
                    targetType,
                    targetId,
                    record.displayLabel,
                    "OFFLINE",
                    record.amountMinor,
                    record.currency,
                    null,
                    record.verifiedAt,
                    sourceType,
                    record.recordId,
                )
            }
        }

    private fun createProviderRefund(
        target: ResolvedCorrectionTarget,
        amountMinor: Long,
        idempotencyKey: String,
    ): String =
        try {
            when (target.targetType) {
                FinancialCorrectionTargetType.CONTRIBUTION ->
                    stripeCheckoutClient.createRefund(
                        target.paymentIntentId!!,
                        amountMinor,
                        idempotencyKey,
                    )
                FinancialCorrectionTargetType.SPONSORSHIP ->
                    stripeSponsorshipCheckoutClient.createRefund(
                        target.paymentIntentId!!,
                        amountMinor,
                        idempotencyKey,
                    )
                FinancialCorrectionTargetType.ORDER ->
                    stripeOrderCheckoutClient.createRefund(
                        target.paymentIntentId!!,
                        amountMinor,
                        idempotencyKey,
                    )
                FinancialCorrectionTargetType.OFFLINE_FINANCIAL_RECORD -> error("Offline reversals do not call a payment provider.")
            }
        } catch (exception: StripeException) {
            throw ServiceUnavailableException(
                "REFUND_PROVIDER_UNAVAILABLE",
                "The payment provider could not complete the refund. No Rally26 correction was recorded.",
            )
        }

    private fun markOnlineSourceRefunded(target: ResolvedCorrectionTarget) {
        when (target.targetType) {
            FinancialCorrectionTargetType.CONTRIBUTION -> contributionRepository.markRefunded(target.targetId)
            FinancialCorrectionTargetType.SPONSORSHIP -> sponsorshipRepository.markRefunded(target.targetId)
            FinancialCorrectionTargetType.ORDER -> orderRepository.markRefunded(target.targetId)
            FinancialCorrectionTargetType.OFFLINE_FINANCIAL_RECORD -> Unit
        }
    }

    private fun markOfflineSourceReversed(
        organizationId: UUID,
        offlineRecordId: UUID,
        target: ResolvedCorrectionTarget,
        reason: String,
        currentUser: CurrentUser,
    ) {
        when (target.ledgerSourceType) {
            LedgerSourceType.CONTRIBUTION -> contributionRepository.markRefunded(target.ledgerSourceId)
            LedgerSourceType.SPONSORSHIP -> sponsorshipRepository.markRefunded(target.ledgerSourceId)
            LedgerSourceType.ORDER -> orderRepository.markRefunded(target.ledgerSourceId)
            else -> error("Unsupported offline source type ${target.ledgerSourceType}")
        }
        if (offlineFinancialRecordRepository.markReversed(offlineRecordId, organizationId, currentUser.userId, reason, Instant.now()) !=
            1
        ) {
            throw ConflictException("OFFLINE_RECORD_CHANGED", "The offline financial record changed before it could be reversed.")
        }
    }

    private fun normalizeReason(reason: String): String {
        val normalized = reason.trim()
        if (normalized.length !in 3..1000) throw ValidationException("A correction reason between 3 and 1000 characters is required.")
        return normalized
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
