package com.rally26.order.domain

import java.time.Instant
import java.util.UUID

enum class FulfillmentSource { PRINTIFY, MANUAL }

enum class FulfillmentStatus {
    NOT_SUBMITTED,
    DRAFT_CREATED,
    FAILED,
    READY,
    IN_PRODUCTION,
    SHIPPED,
    DELIVERED,
    NEEDS_ATTENTION,
    CANCELED,
}

data class Fulfillment(
    val id: UUID,
    val orderId: UUID,
    val source: FulfillmentSource,
    val status: FulfillmentStatus,
    val printifyOrderId: String?,
    val manualVendorId: UUID?,
    val manualVendorName: String?,
    val vendorOrderReference: String?,
    val carrier: String?,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val internalNotes: String?,
    val attentionReason: String?,
    val lastError: String?,
    val statusChangedAt: Instant,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FulfillmentHistory(
    val id: UUID,
    val organizationId: UUID,
    val fulfillmentId: UUID,
    val previousStatus: FulfillmentStatus?,
    val newStatus: FulfillmentStatus,
    val note: String,
    val actorUserId: UUID?,
    val createdAt: Instant,
)

enum class FulfillmentReprintStatus { REQUESTED, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELED }

data class FulfillmentReprint(
    val id: UUID,
    val organizationId: UUID,
    val fulfillmentId: UUID,
    val orderId: UUID,
    val status: FulfillmentReprintStatus,
    val reason: String,
    val vendorOrderReference: String?,
    val carrier: String?,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val internalNotes: String?,
    val requestedByUserId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
)
