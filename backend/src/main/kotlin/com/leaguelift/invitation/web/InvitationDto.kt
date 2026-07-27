package com.leaguelift.invitation.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.invitation.domain.Invitation
import com.leaguelift.membership.domain.MembershipRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateInvitationRequest(
	@field:NotBlank
	@field:Email
	val email: String,
	@field:NotNull
	val role: MembershipRole,
)

data class InvitationResponse(
	val id: UUID,
	val organizationId: UUID,
	val email: String,
	val role: MembershipRole,
	val status: String,
	val expiresAt: Instant,
	val createdAt: Instant,
)

/** Returned only from the create endpoint, once, so the inviter can build a share
 *  link. Never returned from list/get endpoints (docs/security.md — minimal public
 *  responses, no long-lived token exposure beyond the moment of creation). */
data class CreateInvitationResponse(
	val invitation: InvitationResponse,
	val token: String,
)

fun Invitation.toResponse() = InvitationResponse(
	id = id,
	organizationId = organizationId,
	email = email,
	role = role,
	status = status.name,
	expiresAt = expiresAt,
	createdAt = createdAt,
)

typealias InvitationPageResponse = PageResponse<InvitationResponse>
