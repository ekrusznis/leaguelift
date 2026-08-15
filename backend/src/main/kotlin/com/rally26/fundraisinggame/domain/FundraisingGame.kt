package com.rally26.fundraisinggame.domain

import java.time.Instant
import java.util.UUID

enum class FundraisingGameType {
    BIG_GAME_SQUARES,
    BRACKET_CHALLENGE,
    PREDICTION_CHALLENGE,
    FREE_PRIZE_DRAWING,
    TRIVIA_CHALLENGE,
}

enum class FundraisingGameStatus { DRAFT, OPEN, CLOSED }

data class FundraisingGame(
    val id: UUID,
    val organizationId: UUID,
    val campaignId: UUID,
    val createdByUserId: UUID,
    val gameType: FundraisingGameType,
    val title: String,
    val instructions: String?,
    val prizeDescription: String?,
    val maxEntries: Int?,
    val entriesPerPerson: Int,
    val rows: Int?,
    val cols: Int?,
    val status: FundraisingGameStatus,
    val winnerEntryId: UUID?,
    val winnerSelectedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FundraisingGameEntry(
    val id: UUID,
    val gameId: UUID,
    val displayName: String,
    val email: String,
    val selectionKey: String?,
    val selectionText: String?,
    val isWinner: Boolean,
    val createdAt: Instant,
)
