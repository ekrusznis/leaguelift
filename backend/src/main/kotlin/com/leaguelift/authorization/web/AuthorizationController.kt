package com.leaguelift.authorization.web

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.ResourceRole
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The capability-based authorization model's API surface (DESIGN-DOC.md section 9.2,
 * ADR-020): `GET /me/contexts` for the frontend's `useContexts` hook, plus the
 * org-manager-only endpoints that grant/revoke TEAM and TOURNAMENT resource-scoped
 * roles (there is deliberately no endpoint for PLATFORM or PARTICIPANT grants — see
 * AuthorizationService's class doc).
 */
@RestController
@RequestMapping("/api/v1")
class AuthorizationController(private val authorizationService: AuthorizationService) {

	@GetMapping("/me/contexts")
	fun contexts(@AuthenticationPrincipal currentUser: CurrentUser): List<ContextResponse> =
		authorizationService.listContexts(currentUser).map { it.toResponse() }

	@PostMapping("/organizations/{organizationId}/teams/{teamId}/role-assignments")
	@ResponseStatus(HttpStatus.CREATED)
	fun grantTeamRole(
		@PathVariable organizationId: UUID,
		@PathVariable teamId: UUID,
		@RequestBody request: GrantRoleAssignmentRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): RoleAssignmentResponse {
		val role = parseResourceRole(request.role)
		val assignment = authorizationService.grantTeamRole(organizationId, teamId, request.userId, role, currentUser)
		return RoleAssignmentResponse(assignment.id, assignment.userId, assignment.contextType.name, assignment.resourceId, assignment.role.name)
	}

	@DeleteMapping("/organizations/{organizationId}/teams/{teamId}/role-assignments/{assignmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun revokeTeamRole(
		@PathVariable organizationId: UUID,
		@PathVariable teamId: UUID,
		@PathVariable assignmentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) {
		authorizationService.revokeTeamRole(organizationId, teamId, assignmentId, currentUser)
	}

	@PostMapping("/organizations/{organizationId}/tournaments/{tournamentId}/role-assignments")
	@ResponseStatus(HttpStatus.CREATED)
	fun grantTournamentRole(
		@PathVariable organizationId: UUID,
		@PathVariable tournamentId: UUID,
		@RequestBody request: GrantRoleAssignmentRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): RoleAssignmentResponse {
		val role = parseResourceRole(request.role)
		val assignment = authorizationService.grantTournamentRole(organizationId, tournamentId, request.userId, role, currentUser)
		return RoleAssignmentResponse(assignment.id, assignment.userId, assignment.contextType.name, assignment.resourceId, assignment.role.name)
	}

	@DeleteMapping("/organizations/{organizationId}/tournaments/{tournamentId}/role-assignments/{assignmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun revokeTournamentRole(
		@PathVariable organizationId: UUID,
		@PathVariable tournamentId: UUID,
		@PathVariable assignmentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	) {
		authorizationService.revokeTournamentRole(organizationId, tournamentId, assignmentId, currentUser)
	}

	private fun parseResourceRole(role: String): ResourceRole =
		runCatching { ResourceRole.valueOf(role) }.getOrNull()
			?: throw ValidationException("'$role' is not a recognized role.")
}
