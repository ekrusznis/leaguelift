package com.rally26.order.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentHistory
import com.rally26.order.domain.FulfillmentReprint
import com.rally26.order.domain.FulfillmentReprintStatus
import com.rally26.order.domain.FulfillmentSource
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.persistence.FulfillmentHistoryRepository
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.order.persistence.FulfillmentReprintRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.store.application.ManualVendorService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FulfillmentOperationsService(
    private val orderRepository: OrderRepository,
    private val fulfillmentRepository: FulfillmentRepository,
    private val historyRepository: FulfillmentHistoryRepository,
    private val reprintRepository: FulfillmentReprintRepository,
    private val manualVendorService: ManualVendorService,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun get(
        organizationId: UUID,
        orderId: UUID,
        currentUser: CurrentUser,
    ): Fulfillment? {
        membershipService.requireManagerRole(organizationId, currentUser)
        requireOrder(organizationId, orderId)
        return fulfillmentRepository.findByOrder(orderId)
    }

    fun listHistory(
        organizationId: UUID,
        orderId: UUID,
        currentUser: CurrentUser,
    ): List<FulfillmentHistory> {
        membershipService.requireManagerRole(organizationId, currentUser)
        requireOrder(organizationId, orderId)
        val fulfillment = fulfillmentRepository.findByOrder(orderId) ?: return emptyList()
        return historyRepository.listByFulfillment(fulfillment.id)
    }

    fun listReprints(
        organizationId: UUID,
        orderId: UUID,
        currentUser: CurrentUser,
    ): List<FulfillmentReprint> {
        membershipService.requireManagerRole(organizationId, currentUser)
        requireOrder(organizationId, orderId)
        return reprintRepository.listByOrder(orderId)
    }

    @Transactional
    fun update(
        organizationId: UUID,
        orderId: UUID,
        status: FulfillmentStatus,
        manualVendorId: UUID?,
        vendorOrderReference: String?,
        carrier: String?,
        trackingNumber: String?,
        trackingUrl: String?,
        internalNotes: String?,
        attentionReason: String?,
        note: String,
        currentUser: CurrentUser,
    ): Fulfillment {
        membershipService.requireManagerRole(organizationId, currentUser)
        requireOrder(organizationId, orderId)
        val existing =
            fulfillmentRepository.findByOrder(orderId)
                ?: throw NotFoundException("FULFILLMENT_NOT_FOUND", "The fulfillment record could not be found.")
        validateTransition(existing.status, status)
        val normalizedVendorId =
            when (existing.source) {
                FulfillmentSource.PRINTIFY -> {
                    if (manualVendorId != null) throw ValidationException("A Printify fulfillment cannot reference a manual vendor.")
                    null
                }
                FulfillmentSource.MANUAL -> {
                    if (manualVendorId != null && manualVendorId != existing.manualVendorId) {
                        manualVendorService.requireActiveVendor(organizationId, manualVendorId)
                    }
                    manualVendorId
                }
            }
        val normalizedAttention = attentionReason.normalized()
        if (status == FulfillmentStatus.NEEDS_ATTENTION && normalizedAttention == null) {
            throw ValidationException("Explain what requires attention.")
        }
        fulfillmentRepository.updateOperationalState(
            id = existing.id,
            status = status,
            manualVendorId = normalizedVendorId,
            vendorOrderReference = vendorOrderReference.normalized(),
            carrier = carrier.normalized(),
            trackingNumber = trackingNumber.normalized(),
            trackingUrl = trackingUrl.normalized(),
            internalNotes = internalNotes.normalized(),
            attentionReason = if (status == FulfillmentStatus.NEEDS_ATTENTION) normalizedAttention else null,
        )
        if (existing.status != status) {
            historyRepository.insert(organizationId, existing.id, existing.status, status, note.trim(), currentUser.userId)
        }
        auditService.record(currentUser.userId, organizationId, "fulfillment.updated", "fulfillment", existing.id)
        return fulfillmentRepository.findByOrder(orderId)!!
    }

    /**
     * Phase 24 slice 24.4 (ADR-070): system-initiated (verified Printify
     * webhook, no [CurrentUser]) — organization is resolved from the
     * fulfillment's own order row via [FulfillmentRepository.findByPrintifyOrderId],
     * never taken from anything the webhook payload itself claims. Reuses the
     * same [validateTransition] state machine [update] uses, so a webhook can
     * never push a fulfillment through an impossible transition. Idempotent:
     * a same-status replay (e.g. a redelivered webhook) is a no-op.
     */
    @Transactional
    fun applyProviderStatusUpdate(
        printifyOrderId: String,
        newStatus: FulfillmentStatus,
        carrier: String?,
        trackingNumber: String?,
        trackingUrl: String?,
        note: String,
    ): Fulfillment? {
        val match = fulfillmentRepository.findByPrintifyOrderId(printifyOrderId) ?: return null
        val existing = match.fulfillment
        if (existing.status == newStatus) return existing
        validateTransition(existing.status, newStatus)
        fulfillmentRepository.updateOperationalState(
            id = existing.id,
            status = newStatus,
            manualVendorId = null,
            vendorOrderReference = existing.vendorOrderReference,
            carrier = carrier ?: existing.carrier,
            trackingNumber = trackingNumber ?: existing.trackingNumber,
            trackingUrl = trackingUrl ?: existing.trackingUrl,
            internalNotes = existing.internalNotes,
            attentionReason = null,
        )
        historyRepository.insert(match.organizationId, existing.id, existing.status, newStatus, note, null)
        auditService.record(null, match.organizationId, "fulfillment.updated_by_printify_webhook", "fulfillment", existing.id)
        return fulfillmentRepository.findByOrder(existing.orderId)
    }

    @Transactional
    fun requestReprint(
        organizationId: UUID,
        orderId: UUID,
        reason: String,
        vendorOrderReference: String?,
        internalNotes: String?,
        currentUser: CurrentUser,
    ): FulfillmentReprint {
        membershipService.requireManagerRole(organizationId, currentUser)
        requireOrder(organizationId, orderId)
        val fulfillment =
            fulfillmentRepository.findByOrder(orderId)
                ?: throw NotFoundException("FULFILLMENT_NOT_FOUND", "The fulfillment record could not be found.")
        if (fulfillment.status ==
            FulfillmentStatus.CANCELED
        ) {
            throw ValidationException("A canceled fulfillment cannot receive a reprint request.")
        }
        if (reprintRepository.findOpenByFulfillment(fulfillment.id) != null) {
            throw ValidationException("This order already has an open reprint or replacement.")
        }
        val reprint =
            reprintRepository.insert(
                organizationId,
                fulfillment.id,
                orderId,
                reason.trim(),
                vendorOrderReference.normalized(),
                internalNotes.normalized(),
                currentUser.userId,
            )
        auditService.record(currentUser.userId, organizationId, "fulfillment_reprint.requested", "fulfillment_reprint", reprint.id)
        return reprint
    }

    @Transactional
    fun updateReprint(
        organizationId: UUID,
        orderId: UUID,
        reprintId: UUID,
        status: FulfillmentReprintStatus,
        vendorOrderReference: String?,
        carrier: String?,
        trackingNumber: String?,
        trackingUrl: String?,
        internalNotes: String?,
        currentUser: CurrentUser,
    ): FulfillmentReprint {
        membershipService.requireManagerRole(organizationId, currentUser)
        requireOrder(organizationId, orderId)
        val existing =
            reprintRepository.findById(reprintId, organizationId)?.takeIf { it.orderId == orderId }
                ?: throw NotFoundException("FULFILLMENT_REPRINT_NOT_FOUND", "The reprint or replacement could not be found.")
        validateReprintTransition(existing.status, status)
        reprintRepository.update(
            reprintId,
            organizationId,
            status,
            vendorOrderReference.normalized(),
            carrier.normalized(),
            trackingNumber.normalized(),
            trackingUrl.normalized(),
            internalNotes.normalized(),
        )
        auditService.record(currentUser.userId, organizationId, "fulfillment_reprint.updated", "fulfillment_reprint", reprintId)
        return reprintRepository.findById(reprintId, organizationId)!!
    }

    private fun requireOrder(
        organizationId: UUID,
        orderId: UUID,
    ) = orderRepository.findById(orderId, organizationId)
        ?: throw NotFoundException("ORDER_NOT_FOUND", "The order could not be found.")

    private fun validateTransition(
        from: FulfillmentStatus,
        to: FulfillmentStatus,
    ) {
        if (from == to) return
        val allowed =
            when (from) {
                FulfillmentStatus.NOT_SUBMITTED ->
                    setOf(
                        FulfillmentStatus.READY,
                        FulfillmentStatus.NEEDS_ATTENTION,
                        FulfillmentStatus.CANCELED,
                    )
                FulfillmentStatus.DRAFT_CREATED ->
                    setOf(
                        FulfillmentStatus.READY,
                        FulfillmentStatus.IN_PRODUCTION,
                        FulfillmentStatus.SHIPPED,
                        FulfillmentStatus.NEEDS_ATTENTION,
                        FulfillmentStatus.CANCELED,
                    )
                FulfillmentStatus.FAILED -> setOf(FulfillmentStatus.READY, FulfillmentStatus.NEEDS_ATTENTION, FulfillmentStatus.CANCELED)
                FulfillmentStatus.READY ->
                    setOf(
                        FulfillmentStatus.IN_PRODUCTION,
                        FulfillmentStatus.SHIPPED,
                        FulfillmentStatus.NEEDS_ATTENTION,
                        FulfillmentStatus.CANCELED,
                    )
                FulfillmentStatus.IN_PRODUCTION ->
                    setOf(
                        FulfillmentStatus.SHIPPED,
                        FulfillmentStatus.NEEDS_ATTENTION,
                        FulfillmentStatus.CANCELED,
                    )
                FulfillmentStatus.SHIPPED -> setOf(FulfillmentStatus.DELIVERED, FulfillmentStatus.NEEDS_ATTENTION)
                FulfillmentStatus.NEEDS_ATTENTION ->
                    setOf(
                        FulfillmentStatus.READY,
                        FulfillmentStatus.IN_PRODUCTION,
                        FulfillmentStatus.SHIPPED,
                        FulfillmentStatus.CANCELED,
                    )
                FulfillmentStatus.DELIVERED, FulfillmentStatus.CANCELED -> emptySet()
            }
        if (to !in allowed) throw ValidationException("Fulfillment cannot move from ${from.name} to ${to.name}.")
    }

    private fun validateReprintTransition(
        from: FulfillmentReprintStatus,
        to: FulfillmentReprintStatus,
    ) {
        if (from == to) return
        val allowed =
            when (from) {
                FulfillmentReprintStatus.REQUESTED ->
                    setOf(
                        FulfillmentReprintStatus.IN_PRODUCTION,
                        FulfillmentReprintStatus.SHIPPED,
                        FulfillmentReprintStatus.CANCELED,
                    )
                FulfillmentReprintStatus.IN_PRODUCTION -> setOf(FulfillmentReprintStatus.SHIPPED, FulfillmentReprintStatus.CANCELED)
                FulfillmentReprintStatus.SHIPPED -> setOf(FulfillmentReprintStatus.DELIVERED)
                FulfillmentReprintStatus.DELIVERED, FulfillmentReprintStatus.CANCELED -> emptySet()
            }
        if (to !in allowed) throw ValidationException("Reprint cannot move from ${from.name} to ${to.name}.")
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
