package com.rally26.fundraisinggame.web

import com.rally26.fundraisinggame.application.FundraisingGamePermissions
import com.rally26.fundraisinggame.domain.FundraisingGame
import com.rally26.fundraisinggame.domain.FundraisingGameEntry
import com.rally26.fundraisinggame.domain.FundraisingGameType
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateFundraisingGameRequest(
    val gameType: FundraisingGameType,
    @field:NotBlank @field:Size(max = 160) val title: String,
    @field:Size(max = 3000) val instructions: String? = null,
    @field:Size(max = 1000) val prizeDescription: String? = null,
    @field:Min(1) @field:Max(100000) val maxEntries: Int? = null,
    @field:Min(1) @field:Max(20) val entriesPerPerson: Int = 1,
    @field:Min(1) @field:Max(26) val rows: Int? = null,
    @field:Min(1) @field:Max(26) val cols: Int? = null,
)

data class UpdateFundraisingGameRequest(
    @field:NotBlank @field:Size(max = 160) val title: String,
    @field:Size(max = 3000) val instructions: String? = null,
    @field:Size(max = 1000) val prizeDescription: String? = null,
    @field:Min(1) @field:Max(100000) val maxEntries: Int? = null,
    @field:Min(1) @field:Max(20) val entriesPerPerson: Int = 1,
    @field:Min(1) @field:Max(26) val rows: Int? = null,
    @field:Min(1) @field:Max(26) val cols: Int? = null,
)

data class CreateFreeGameEntryRequest(
    @field:NotBlank @field:Size(max = 120) val displayName: String,
    @field:NotBlank @field:Email @field:Size(max = 254) val email: String,
    @field:Size(max = 64) val selectionKey: String? = null,
    @field:Size(max = 1000) val selectionText: String? = null,
)

data class FundraisingGamePermissionsResponse(
    val canConfigure: Boolean,
    val canOpen: Boolean,
    val canClose: Boolean,
    val canDrawWinner: Boolean,
)

data class FundraisingGameResponse(
    val id: UUID,
    val organizationId: UUID,
    val campaignId: UUID,
    val gameType: String,
    val title: String,
    val instructions: String?,
    val prizeDescription: String?,
    val maxEntries: Int?,
    val entriesPerPerson: Int,
    val rows: Int?,
    val cols: Int?,
    val status: String,
    val winnerEntryId: UUID?,
    val winnerSelectedAt: Instant?,
    val entryCount: Long,
    val permissions: FundraisingGamePermissionsResponse,
)

data class FundraisingGameEntryResponse(
    val id: UUID,
    val displayName: String,
    val email: String,
    val selectionKey: String?,
    val selectionText: String?,
    val isWinner: Boolean,
    val createdAt: Instant,
)

data class PublicFundraisingGameEntryResponse(
    val id: UUID,
    val displayName: String,
    val selectionKey: String?,
    val selectionText: String?,
    val isWinner: Boolean,
)

data class PublicFundraisingGameResponse(
    val id: UUID,
    val campaignSlug: String,
    val gameType: String,
    val title: String,
    val instructions: String?,
    val prizeDescription: String?,
    val maxEntries: Int?,
    val entriesPerPerson: Int,
    val rows: Int?,
    val cols: Int?,
    val status: String,
    val entryCount: Long,
    val winnerDisplayName: String?,
    val entries: List<PublicFundraisingGameEntryResponse>,
    val freeEntryDisclosure: String =
        "No purchase or donation is necessary to enter. " +
            "Donating does not improve your odds or provide additional entries.",
)

fun FundraisingGame.toResponse(
    entryCount: Long,
    permissions: FundraisingGamePermissions,
) = FundraisingGameResponse(
    id,
    organizationId,
    campaignId,
    gameType.name,
    title,
    instructions,
    prizeDescription,
    maxEntries,
    entriesPerPerson,
    rows,
    cols,
    status.name,
    winnerEntryId,
    winnerSelectedAt,
    entryCount,
    FundraisingGamePermissionsResponse(permissions.canConfigure, permissions.canOpen, permissions.canClose, permissions.canDrawWinner),
)

fun FundraisingGameEntry.toResponse() =
    FundraisingGameEntryResponse(id, displayName, email, selectionKey, selectionText, isWinner, createdAt)

fun FundraisingGameEntry.toPublicResponse() = PublicFundraisingGameEntryResponse(id, displayName, selectionKey, selectionText, isWinner)
