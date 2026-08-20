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

fun AuthorizationContext.toResponse() =
    ContextResponse(
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

/**
 * A deliberately reduced-exposure view of a team's role assignments — no email/phone,
 * just enough to answer "who else coaches this team" (LR-020). Labels mirror
 * `TeamRoleAssignmentsSection.tsx`'s `TEAM_ROLE_OPTIONS` so the same role reads
 * identically here and in the org-manager grant/revoke panel.
 */
data class TeamStaffResponse(
    val userId: UUID,
    val displayName: String?,
    val roleLabel: String,
)

fun teamRoleLabel(role: String): String =
    when (role) {
        "COACH_READ" -> "Coach (read-only)"
        "TEAM_EDITOR" -> "Team Editor"
        "TEAM_MANAGER" -> "Team Manager"
        else -> role
    }
