package com.leaguelift.store.domain

import java.time.Instant
import java.util.UUID

enum class StoreStatus { DRAFT, ACTIVE, ARCHIVED }

data class Store(
	val id: UUID,
	val organizationId: UUID,
	val teamId: UUID?,
	val name: String,
	val slug: String,
	val status: StoreStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)

private val SLUG_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

fun isValidStoreSlug(slug: String): Boolean = SLUG_PATTERN.matches(slug)
