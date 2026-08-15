package com.rally26.fundraisinggame.web

import com.rally26.fundraisinggame.application.FundraisingGameService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public/campaigns/{slug}/game")
@Tag(name = "fundraising-games", description = "Public, unauthenticated free-entry promotional game participation.")
class FundraisingGamePublicController(
    private val service: FundraisingGameService,
) {
    @GetMapping
    @Operation(
        summary = "Get public free-entry game",
        description = "Public response omits entrant email and includes the server-owned no-purchase disclosure.",
    )
    fun get(
        @PathVariable slug: String,
    ): PublicFundraisingGameResponse? {
        val game = service.getPublicOrNull(slug) ?: return null
        val entries = service.listPublicEntries(slug)
        val winner = game.winnerEntryId?.let { winnerId -> entries.firstOrNull { it.id == winnerId } }
        return PublicFundraisingGameResponse(
            id = game.id,
            campaignSlug = slug,
            gameType = game.gameType.name,
            title = game.title,
            instructions = game.instructions,
            prizeDescription = game.prizeDescription,
            maxEntries = game.maxEntries,
            entriesPerPerson = game.entriesPerPerson,
            rows = game.rows,
            cols = game.cols,
            status = game.status.name,
            entryCount = entries.size.toLong(),
            winnerDisplayName = winner?.displayName,
            entries = entries.map { it.toPublicResponse() },
        )
    }

    @PostMapping("/entries")
    @Operation(
        summary = "Enter promotional game for free",
        description =
            "Accepts no payment/contribution identifier and never calls a " +
                "payment provider. Donations do not improve odds or entry entitlement.",
    )
    fun enter(
        @PathVariable slug: String,
        @Valid @RequestBody request: CreateFreeGameEntryRequest,
    ): ResponseEntity<PublicFundraisingGameEntryResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service.enterPublic(slug, request.displayName, request.email, request.selectionKey, request.selectionText).toPublicResponse(),
        )
}
