package com.rally26.authorization.domain

import java.time.Instant
import java.util.UUID

enum class RoleAssignmentStatus { ACTIVE, REVOKED }

/**
 * A resource-scoped role grant: "this user has [role] on the [contextType] identified
 * by [resourceId]". Covers TEAM, TOURNAMENT, and PLATFORM contexts, plus the narrow
 * PARTICIPANT (athlete self-link) context — see [ContextType] and [ResourceRole] for
 * the full picture, and ADR-020 for why ORGANIZATION isn't represented here.
 *
 * [organizationId] is null only for PLATFORM (a platform grant isn't scoped to one
 * organization); every other context type requires it (DESIGN-DOC.md section 8.1 rule
 * 1 — every organization-owned table/row carries organization_id).
 */
data class RoleAssignment(
    val id: UUID,
    val organizationId: UUID?,
    val userId: UUID,
    val contextType: RoleAssignmentContextType,
    val resourceId: UUID?,
    val role: ResourceRole,
    val status: RoleAssignmentStatus,
    val grantedBy: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
