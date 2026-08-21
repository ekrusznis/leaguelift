package com.rally26.organization.domain

import java.time.Instant
import java.util.UUID

enum class OrganizationDeletionStatus { PENDING, CANCELED, COMPLETED }

/** See V103's migration comment — Owner-only, the org-wide sibling of `identity.domain.AccountDeletionRequest`. */
data class OrganizationDeletionRequest(
    val id: UUID,
    val organizationId: UUID,
    val requestedByUserId: UUID,
    val status: OrganizationDeletionStatus,
    val requestedAt: Instant,
    val scheduledFor: Instant,
    val canceledAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
