package com.rally26.tournament.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.tournament.application.TournamentService
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
@RequestMapping("/api/v1/organizations/{organizationId}/tournaments")
class TournamentController(
    private val tournamentService: TournamentService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<TournamentResponse> {
        val offset = page * size
        val items = tournamentService.list(organizationId, currentUser, offset, size).map { it.toResponse() }
        val total = tournamentService.count(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateTournamentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<TournamentResponse> {
        val tournament =
            tournamentService.create(
                organizationId,
                request.name,
                request.sport,
                request.startDate,
                request.endDate,
                request.location,
                request.contactEmail,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(tournament.toResponse())
    }

    @GetMapping("/{tournamentId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable tournamentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): TournamentResponse = tournamentService.get(organizationId, tournamentId, currentUser).toResponse()

    @PatchMapping("/{tournamentId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable tournamentId: UUID,
        @Valid @RequestBody request: UpdateTournamentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): TournamentResponse =
        tournamentService
            .update(
                organizationId,
                tournamentId,
                request.name,
                request.sport,
                request.startDate,
                request.endDate,
                request.location,
                request.contactEmail,
                currentUser,
            ).toResponse()

    @DeleteMapping("/{tournamentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable tournamentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = tournamentService.archive(organizationId, tournamentId, currentUser)
}
