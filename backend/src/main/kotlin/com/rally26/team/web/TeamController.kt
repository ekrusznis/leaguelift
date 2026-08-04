package com.rally26.team.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.team.application.TeamService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/teams")
class TeamController(private val teamService: TeamService) {

    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<TeamResponse> {
        val offset = page * size
        val items = teamService.list(organizationId, currentUser, offset, size).map { it.toResponse() }
        val total = teamService.count(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateTeamRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<TeamResponse> {
        val team = teamService.create(
            organizationId,
            request.name,
            request.sport,
            request.season,
            request.contactEmail,
            currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(team.toResponse())
    }

    @GetMapping("/{teamId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable teamId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): TeamResponse = teamService.get(organizationId, teamId, currentUser).toResponse()

    @PatchMapping("/{teamId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable teamId: UUID,
        @Valid @RequestBody request: UpdateTeamRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): TeamResponse = teamService.update(
        organizationId,
        teamId,
        request.name,
        request.sport,
        request.season,
        request.contactEmail,
        currentUser,
    ).toResponse()

    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable teamId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = teamService.archive(organizationId, teamId, currentUser)
}
