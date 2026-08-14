package com.rally26.fundraisinggame.web

import com.rally26.common.web.CurrentUser
import com.rally26.fundraisinggame.application.FundraisingGameService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/campaigns/{campaignId}/game")
class FundraisingGameController(
    private val service: FundraisingGameService,
) {
    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingGameResponse? {
        val game = service.getForManagement(organizationId, campaignId, currentUser) ?: return null
        return game.toResponse(
            service.listEntries(organizationId, campaignId, currentUser).size.toLong(),
            service.permissionsFor(game, currentUser),
        )
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: CreateFundraisingGameRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FundraisingGameResponse> {
        val game =
            service.create(
                organizationId,
                campaignId,
                request.gameType,
                request.title,
                request.instructions,
                request.prizeDescription,
                request.maxEntries,
                request.entriesPerPerson,
                request.rows,
                request.cols,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(game.toResponse(0, service.permissionsFor(game, currentUser)))
    }

    @PatchMapping
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: UpdateFundraisingGameRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingGameResponse {
        val game =
            service.update(
                organizationId,
                campaignId,
                request.title,
                request.instructions,
                request.prizeDescription,
                request.maxEntries,
                request.entriesPerPerson,
                request.rows,
                request.cols,
                currentUser,
            )
        return game.toResponse(
            service.listEntries(organizationId, campaignId, currentUser).size.toLong(),
            service.permissionsFor(game, currentUser),
        )
    }

    @PostMapping("/open")
    fun open(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingGameResponse {
        val game = service.open(organizationId, campaignId, currentUser)
        return game.toResponse(
            service.listEntries(organizationId, campaignId, currentUser).size.toLong(),
            service.permissionsFor(game, currentUser),
        )
    }

    @PostMapping("/close")
    fun close(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingGameResponse {
        val game = service.close(organizationId, campaignId, currentUser)
        return game.toResponse(
            service.listEntries(organizationId, campaignId, currentUser).size.toLong(),
            service.permissionsFor(game, currentUser),
        )
    }

    @PostMapping("/draw-winner")
    fun drawWinner(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingGameEntryResponse = service.drawWinner(organizationId, campaignId, currentUser).toResponse()

    @GetMapping("/entries")
    fun entries(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FundraisingGameEntryResponse> = service.listEntries(organizationId, campaignId, currentUser).map { it.toResponse() }
}
