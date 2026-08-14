package com.rally26.dispute.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.config.DisputeProperties
import com.rally26.dispute.domain.DisputeSourceType
import com.rally26.dispute.domain.DisputeStatus
import com.rally26.dispute.domain.PaymentDispute
import com.rally26.dispute.persistence.PaymentDisputeRepository
import com.rally26.fee.persistence.FeePaymentRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.membership.application.MembershipService
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.sponsorship.persistence.SponsorshipRepository
import com.stripe.model.Dispute
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger(DisputeService::class.java)

data class DisputeNotificationPayload(
    val organizationName: String,
    val ownerEmails: List<String>,
    val amountMinor: Long,
    val currency: String,
    val reason: String,
    val outcome: String? = null,
)

/** Which source record a payment_intent belongs to, resolved by trying each module's own lookup in turn. */
private data class DisputeSource(
    val sourceType: DisputeSourceType,
    val sourceId: UUID,
    val organizationId: UUID,
)

/**
 * Orchestrates DESIGN-DOC.md §14.6 item #4 (Stripe dispute/chargeback handling).
 * Rally26 is the merchant of record (ADR-005) — every dispute is against Rally26's
 * own Stripe account, routed back to an organization purely via the disputed
 * payment_intent, never Connect account context.
 */
@Service
class DisputeService(
    private val orderRepository: OrderRepository,
    private val contributionRepository: ContributionRepository,
    private val sponsorshipRepository: SponsorshipRepository,
    private val feePaymentRepository: FeePaymentRepository,
    private val paymentDisputeRepository: PaymentDisputeRepository,
    private val ledgerService: LedgerService,
    private val auditService: AuditService,
    private val organizationRepository: OrganizationRepository,
    private val membershipRepository: MembershipRepository,
    private val appUserRepository: AppUserRepository,
    private val membershipService: MembershipService,
    private val outboxWriter: OutboxWriter,
    private val objectMapper: ObjectMapper,
    private val disputeProperties: DisputeProperties,
) {
    /** Returns the new `payment_dispute` row's id, or null if the disputed payment_intent doesn't match any known source record (logged as a warning — should not happen in practice, but a webhook must never throw on data it can't fully resolve). */
    @Transactional
    fun handleDisputeCreated(dispute: Dispute): UUID? {
        val existing = paymentDisputeRepository.findByStripeDisputeId(dispute.id)
        if (existing != null) return existing.id

        val paymentIntentId = dispute.paymentIntent
        val source = paymentIntentId?.let { resolveSource(it) }
        if (source == null) {
            log.warn(
                "Stripe dispute {} references payment_intent {} which matches no known order/contribution/sponsorship/fee_payment — cannot route to an organization.",
                dispute.id,
                paymentIntentId,
            )
            return null
        }

        ledgerService.recordDisputeOpened(
            organizationId = source.organizationId,
            sourceType = source.sourceType.toLedgerSourceType(),
            sourceId = source.sourceId,
            grossAmountMinor = dispute.amount,
            currency = dispute.currency,
            disputeFeeMinor = disputeProperties.feeMinor,
            stripeDisputeId = dispute.id,
        )

        val evidenceDueBy = dispute.evidenceDetails?.dueBy?.let { Instant.ofEpochSecond(it) }
        val row =
            paymentDisputeRepository.insert(
                organizationId = source.organizationId,
                sourceType = source.sourceType,
                sourceId = source.sourceId,
                stripeDisputeId = dispute.id,
                stripeChargeId = dispute.charge ?: "",
                amountMinor = dispute.amount,
                currency = dispute.currency,
                reason = dispute.reason ?: "unknown",
                status = disputeStatusFrom(dispute.status),
                evidenceDueBy = evidenceDueBy,
                openedAt = Instant.now(),
            )

        auditService.record(
            actorUserId = null,
            organizationId = source.organizationId,
            action = "payment_dispute.opened",
            entityType = "payment_dispute",
            entityId = row.id,
        )
        enqueueNotification(row, eventType = "payment_dispute.opened", outcome = null)
        return row.id
    }

    /** Returns the resolved `payment_dispute` row's id, or null if the `created` event was somehow missed. */
    @Transactional
    fun handleDisputeClosed(dispute: Dispute): UUID? {
        val existing = paymentDisputeRepository.findByStripeDisputeId(dispute.id)
        if (existing == null) {
            log.warn("Stripe dispute {} closed but no payment_dispute row exists — the created event may have been missed.", dispute.id)
            return null
        }
        if (existing.resolvedAt != null) return existing.id

        val won = dispute.status == "won"
        if (won) {
            ledgerService.recordDisputeWon(
                organizationId = existing.organizationId,
                sourceType = existing.sourceType.toLedgerSourceType(),
                sourceId = existing.sourceId,
                grossAmountMinor = existing.amountMinor,
                currency = existing.currency,
                stripeDisputeId = existing.stripeDisputeId,
            )
        }
        val finalStatus = if (won) DisputeStatus.WON else DisputeStatus.LOST
        paymentDisputeRepository.resolve(existing.stripeDisputeId, finalStatus, Instant.now())

        auditService.record(
            actorUserId = null,
            organizationId = existing.organizationId,
            action = "payment_dispute.resolved",
            entityType = "payment_dispute",
            entityId = existing.id,
            summary = "Dispute ${if (won) "won" else "lost"}",
        )
        enqueueNotification(
            existing.copy(status = finalStatus),
            eventType = "payment_dispute.resolved",
            outcome = if (won) "won" else "lost",
        )
        return existing.id
    }

    fun list(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<PaymentDispute> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return paymentDisputeRepository.findByOrganization(organizationId)
    }

    fun search(
        organizationId: UUID,
        query: String?,
        status: DisputeStatus?,
        sourceType: DisputeSourceType?,
        ascending: Boolean,
        offset: Int,
        limit: Int,
        currentUser: CurrentUser,
    ): List<PaymentDispute> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return paymentDisputeRepository.search(
            organizationId,
            normalizeSearchQuery(query),
            status,
            sourceType,
            ascending,
            offset.coerceAtLeast(0),
            limit.coerceIn(1, 100),
        )
    }

    fun count(
        organizationId: UUID,
        query: String?,
        status: DisputeStatus?,
        sourceType: DisputeSourceType?,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return paymentDisputeRepository.count(organizationId, normalizeSearchQuery(query), status, sourceType)
    }

    private fun normalizeSearchQuery(query: String?): String? {
        val normalized = query?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized.length > 200) throw ValidationException("Search text must be 200 characters or fewer.")
        return normalized
    }

    private fun resolveSource(paymentIntentId: String): DisputeSource? {
        orderRepository.findByStripePaymentIntentId(paymentIntentId)?.let {
            return DisputeSource(DisputeSourceType.ORDER, it.id, it.organizationId)
        }
        contributionRepository.findByStripePaymentIntentId(paymentIntentId)?.let {
            return DisputeSource(DisputeSourceType.CONTRIBUTION, it.id, it.organizationId)
        }
        sponsorshipRepository.findByStripePaymentIntentId(paymentIntentId)?.let {
            return DisputeSource(DisputeSourceType.SPONSORSHIP, it.id, it.organizationId)
        }
        feePaymentRepository.findByStripePaymentIntentId(paymentIntentId)?.let {
            return DisputeSource(DisputeSourceType.FEE_PAYMENT, it.id, it.organizationId)
        }
        return null
    }

    /** Every active OWNER/ADMINISTRATOR — same "tell both roles" rationale as OrganizationSubscriptionService.ownerEmailsFor. */
    private fun ownerEmailsFor(organizationId: UUID): List<String> =
        membershipRepository
            .listActiveManagers(organizationId)
            .mapNotNull { appUserRepository.findById(it.userId)?.email }

    private fun enqueueNotification(
        row: PaymentDispute,
        eventType: String,
        outcome: String?,
    ) {
        val organization = organizationRepository.findById(row.organizationId) ?: return
        val ownerEmails = ownerEmailsFor(row.organizationId)
        if (ownerEmails.isEmpty()) return
        outboxWriter.write(
            aggregateType = "payment_dispute",
            aggregateId = row.id,
            organizationId = row.organizationId,
            eventType = eventType,
            payloadJson =
                objectMapper.writeValueAsString(
                    DisputeNotificationPayload(organization.name, ownerEmails, row.amountMinor, row.currency, row.reason, outcome),
                ),
        )
    }
}

private fun DisputeSourceType.toLedgerSourceType(): LedgerSourceType =
    when (this) {
        DisputeSourceType.ORDER -> LedgerSourceType.ORDER
        DisputeSourceType.CONTRIBUTION -> LedgerSourceType.CONTRIBUTION
        DisputeSourceType.SPONSORSHIP -> LedgerSourceType.SPONSORSHIP
        DisputeSourceType.FEE_PAYMENT -> LedgerSourceType.FEE_PAYMENT
    }

/** Stripe's real dispute.status vocabulary includes several "warning_*" pre-response states; anything not explicitly "under_review"/"won"/"lost" defaults to NEEDS_RESPONSE — fail-safe as still-open-and-needing-attention rather than silently treating an unrecognized status as resolved. */
private fun disputeStatusFrom(status: String?): DisputeStatus =
    when (status) {
        "won" -> DisputeStatus.WON
        "lost", "warning_closed" -> DisputeStatus.LOST
        "under_review", "warning_under_review" -> DisputeStatus.UNDER_REVIEW
        else -> DisputeStatus.NEEDS_RESPONSE
    }
