package com.leaguelift.membership.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.membership.domain.OrganizationMembership
import java.time.Instant
import java.util.UUID

data class MembershipResponse(
	val id: UUID,
	val organizationId: UUID,
	val userId: UUID,
	val role: String,
	val status: String,
	val createdAt: Instant,
)

fun OrganizationMembership.toResponse() = MembershipResponse(
	id = id,
	organizationId = organizationId,
	userId = userId,
	role = role.name,
	status = status.name,
	createdAt = createdAt,
)

typealias MembershipPageResponse = PageResponse<MembershipResponse>
