package com.leaguelift.actioncenter.domain

import java.time.Instant
import java.util.UUID

enum class ActionCenterPriority { INFO, NORMAL, HIGH, URGENT }

enum class ActionCenterType {
    PROFILE_CORRECTION_REVIEW,
    OVERDUE_FEES,
    EVENT_REVIEW,
    FULFILLMENT_EXCEPTION,
    OFFLINE_FINANCIAL_REVIEW,
    PAYMENT_PLAN_OVERDUE,
    RECONCILIATION_REVIEW,
    FEE_PAYMENT,
    DOCUMENT_ACKNOWLEDGMENT,
    EVENT_RSVP,
    SUPPORT_CASE_RESPONSE,
    PLATFORM_SUPPORT_QUEUE,
    PLATFORM_DELIVERY_FAILURES,
}

data class ActionCenterItem(
    val id: String,
    val type: ActionCenterType,
    val priority: ActionCenterPriority,
    val title: String,
    val description: String,
    val actionPath: String,
    val organizationId: UUID? = null,
    val contextType: String? = null,
    val contextId: UUID? = null,
    val dueAt: Instant? = null,
    val createdAt: Instant? = null,
)

data class ActionCenter(
    val items: List<ActionCenterItem>,
    val totalCount: Int,
    val highPriorityCount: Int,
)
