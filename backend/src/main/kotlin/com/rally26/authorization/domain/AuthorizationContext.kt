package com.rally26.authorization.domain

import java.util.UUID

/**
 * One entry in the list `GET /me/contexts` returns (DESIGN-DOC.md section 9.2/10.3).
 * [capabilities] is always the fully-resolved, deny-by-default set for [role] in this
 * [contextType] — the frontend never re-derives capabilities from a role name.
 */
data class AuthorizationContext(
    val contextType: ContextType,
    val resourceId: UUID?,
    val organizationId: UUID?,
    val label: String,
    val role: String,
    val capabilities: Set<String>,
)
