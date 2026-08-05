package com.rally26.identity.domain

import java.time.Instant
import java.util.UUID

enum class AppUserStatus { ACTIVE, SUSPENDED, PENDING_EMAIL_VERIFICATION }

data class AppUser(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: AppUserStatus,
    val passwordHash: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
