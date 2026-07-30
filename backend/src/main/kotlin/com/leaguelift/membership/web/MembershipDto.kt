package com.leaguelift.membership.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.OrganizationMembership
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class MembershipResponse(
	val id: UUID,
	val organizationId: UUID,
	val userId: UUID,
	val userEmail: String?,
	val userDisplayName: String?,
	val role: String,
	val status: String,
	val createdAt: Instant,
)

fun OrganizationMembership.toResponse(userEmail: String? = null, userDisplayName: String? = null) = MembershipResponse(
	id = id,
	organizationId = organizationId,
	userId = userId,
	userEmail = userEmail,
	userDisplayName = userDisplayName,
	role = role.name,
	status = status.name,
	createdAt = createdAt,
)

typealias MembershipPageResponse = PageResponse<MembershipResponse>

data class UpdateMembershipRoleRequest(
	@field:NotNull
	val role: MembershipRole,
)
