package com.rally26.authorization.web

import com.rally26.authorization.domain.AuthorizationContext
import java.util.UUID

data class ContextResponse(
	val contextType: String,
	val resourceId: UUID?,
	val organizationId: UUID?,
	val label: String,
	val role: String,
	val capabilities: List<String>,
)

fun AuthorizationContext.toResponse() = ContextResponse(
	contextType = contextType.name,
	resourceId = resourceId,
	organizationId = organizationId,
	label = label,
	role = role,
	capabilities = capabilities.sorted(),
)

data class GrantRoleAssignmentRequest(
	val userId: UUID,
	val role: String,
)

data class RoleAssignmentResponse(
	val id: UUID,
	val userId: UUID,
	val userEmail: String?,
	val userDisplayName: String?,
	val contextType: String,
	val resourceId: UUID?,
	val role: String,
)
