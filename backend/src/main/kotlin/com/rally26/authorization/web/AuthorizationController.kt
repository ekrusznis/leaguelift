package com.rally26.authorization.web

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.persistence.AppUserRepository
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
class AuthorizationController(
	private val authorizationService: AuthorizationService,
	private val appUserRepository: AppUserRepository,
) {

	@GetMapping("/me/contexts")
	fun contexts(@AuthenticationPrincipal currentUser: CurrentUser): List<ContextResponse> =
		authorizationService.listContexts(currentUser).map { it.toResponse() }

	@GetMapping("/organizations/{organizationId}/teams/{teamId}/role-assignments")
	fun listTeamRoleAssignments(
		@PathVariable organizationId: UUID,
		@PathVariable teamId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<RoleAssignmentResponse> =
		authorizationService.listTeamRoleAssignments(organizationId, teamId, currentUser).map { it.toResponse() }

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
		return assignment.toResponse()
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

	@GetMapping("/organizations/{organizationId}/tournaments/{tournamentId}/role-assignments")
	fun listTournamentRoleAssignments(
		@PathVariable organizationId: UUID,
		@PathVariable tournamentId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): List<RoleAssignmentResponse> =
		authorizationService.listTournamentRoleAssignments(organizationId, tournamentId, currentUser).map { it.toResponse() }

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
		return assignment.toResponse()
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

	private fun RoleAssignment.toResponse(): RoleAssignmentResponse {
		val user = appUserRepository.findById(userId)
		return RoleAssignmentResponse(id, userId, user?.email, user?.displayName, contextType.name, resourceId, role.name)
	}

	private fun parseResourceRole(role: String): ResourceRole =
		runCatching { ResourceRole.valueOf(role) }.getOrNull()
			?: throw ValidationException("'$role' is not a recognized role.")
}
