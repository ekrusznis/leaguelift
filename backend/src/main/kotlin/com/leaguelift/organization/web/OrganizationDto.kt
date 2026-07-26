package com.leaguelift.organization.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.organization.domain.Organization
import com.leaguelift.organization.domain.OrganizationType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateOrganizationRequest(
	@field:NotBlank
	@field:Size(min = 2, max = 120)
	val name: String,
	@field:NotBlank
	@field:Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
	val slug: String,
	@field:NotNull
	val organizationType: OrganizationType,
)

data class UpdateOrganizationRequest(
	@field:Size(min = 2, max = 120)
	val name: String? = null,
	val organizationType: OrganizationType? = null,
)

data class OrganizationResponse(
	val id: UUID,
	val name: String,
	val slug: String,
	val organizationType: OrganizationType,
	val status: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)

fun Organization.toResponse() = OrganizationResponse(
	id = id,
	name = name,
	slug = slug,
	organizationType = organizationType,
	status = status.name,
	createdAt = createdAt,
	updatedAt = updatedAt,
)

typealias OrganizationPageResponse = PageResponse<OrganizationResponse>
