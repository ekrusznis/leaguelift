package com.rally26.dashboard.web

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Shapes shared across more than one dashboard response. Every field group below is
 * tagged `isDemoData` at the group that carries it — parts of a response can be real
 * while others are canned sample data, per DESIGN-DOC.md section 10.1: build the real
 * API/auth shape now, swap individual service methods for real repository-backed
 * queries as the backing tables (schedule/events, credits, orders, etc.) are built.
 */
data class ScheduleItem(
	val id: String,
	val day: String,
	val date: String,
	val title: String,
	val subtitle: String,
	val time: String,
	val tag: String?,
)

data class RequiredActionItem(
	val id: String,
	val tone: String,
	val title: String,
	val subtitle: String,
	val dueLabel: String,
)

data class OrderSummary(
	val id: String,
	val productName: String,
	val orderNumber: String,
	val orderedAt: LocalDate,
	val status: String,
)

data class ActivityItem(
	val id: UUID,
	val action: String,
	val entityType: String,
	val entityId: UUID,
	val occurredAt: Instant,
)
