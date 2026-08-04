package com.rally26.fee.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class FeeTemplateStatus { ACTIVE, ARCHIVED }

enum class FeeAssignmentStatus { OPEN, PARTIALLY_PAID, PAID, WAIVED, CANCELLED }

data class FeeTemplate(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val description: String?,
    val amountMinor: Long,
    val currency: String,
    val status: FeeTemplateStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FeeAssignment(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val participantId: UUID?,
    val feeTemplateId: UUID?,
    val description: String,
    val originalAmountMinor: Long,
    val currency: String,
    val dueDate: LocalDate?,
    val status: FeeAssignmentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
