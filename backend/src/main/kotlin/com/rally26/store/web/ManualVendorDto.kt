package com.rally26.store.web

import com.rally26.store.domain.ManualVendor
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ManualVendorMutationRequest(
	@field:NotBlank @field:Size(max = 160) val name: String,
	@field:Size(max = 160) val contactName: String? = null,
	@field:Email @field:Size(max = 254) val contactEmail: String? = null,
	@field:Size(max = 40) val phone: String? = null,
	@field:Pattern(regexp = "^https://.*", message = "Website URL must use HTTPS.") @field:Size(max = 500) val websiteUrl: String? = null,
	@field:Size(max = 2000) val notes: String? = null,
)

data class ManualVendorResponse(
	val id: UUID,
	val organizationId: UUID,
	val name: String,
	val contactName: String?,
	val contactEmail: String?,
	val phone: String?,
	val websiteUrl: String?,
	val notes: String?,
	val status: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)

fun ManualVendor.toResponse() = ManualVendorResponse(
	id, organizationId, name, contactName, contactEmail, phone, websiteUrl, notes, status.name, createdAt, updatedAt,
)
