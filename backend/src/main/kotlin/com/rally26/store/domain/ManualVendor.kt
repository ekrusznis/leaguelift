package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

enum class ManualVendorStatus { ACTIVE, ARCHIVED }

data class ManualVendor(
	val id: UUID,
	val organizationId: UUID,
	val name: String,
	val contactName: String?,
	val contactEmail: String?,
	val phone: String?,
	val websiteUrl: String?,
	val notes: String?,
	val status: ManualVendorStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)
