package com.rally26.identity.domain

import java.time.Instant
import java.util.UUID

enum class AccountDeletionStatus { PENDING, CANCELED, COMPLETED }

/** See V102's migration comment — self-service, distinct from the platform-admin-driven account merge in `identityintegrity`. */
data class AccountDeletionRequest(
    val id: UUID,
    val userId: UUID,
    val status: AccountDeletionStatus,
    val requestedAt: Instant,
    val scheduledFor: Instant,
    val canceledAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
