package com.leaguelift.identity.domain

import java.time.Instant
import java.util.UUID

enum class AppUserStatus { ACTIVE, SUSPENDED }

data class AppUser(
	val id: UUID,
	val externalSubject: String,
	val email: String,
	val displayName: String,
	val status: AppUserStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)
