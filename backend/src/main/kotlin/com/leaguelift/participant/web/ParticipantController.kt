package com.leaguelift.participant.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.participant.application.ParticipantService
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class ParticipantController(private val participantService: ParticipantService) {

    @GetMapping("/households/{householdId}/participants")
    fun list(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<ParticipantResponse> =
        participantService.listForHousehold(organizationId, householdId, currentUser).map { it.toResponse() }

    @PostMapping("/households/{householdId}/participants")
    fun create(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: CreateParticipantRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<ParticipantResponse> {
        val participant = participantService.create(
            organizationId, householdId, request.firstName, request.lastName, request.dateOfBirth, request.notes, currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(participant.toResponse())
    }

    @PatchMapping("/participants/{participantId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @Valid @RequestBody request: UpdateParticipantRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ParticipantResponse = participantService.update(
        organizationId, participantId, request.firstName, request.lastName, request.dateOfBirth, request.notes, currentUser,
    ).toResponse()

    @GetMapping("/participants/{participantId}/teams")
    fun listTeams(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<ParticipantTeamResponse> =
        participantService.listTeams(organizationId, participantId, currentUser).map { it.toResponse() }

    @PostMapping("/participants/{participantId}/teams")
    fun assignToTeam(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @Valid @RequestBody request: AssignTeamRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<ParticipantTeamResponse> {
        val assignment = participantService.assignToTeam(
            organizationId, participantId, request.teamId, request.joinedAt, currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment.toResponse())
    }

    @DeleteMapping("/participants/{participantId}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFromTeam(
        @PathVariable organizationId: UUID,
        @PathVariable participantId: UUID,
        @PathVariable teamId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = participantService.removeFromTeam(organizationId, participantId, teamId, currentUser)
}
