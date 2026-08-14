package com.rally26.fundraisinggame.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraisinggame.domain.FundraisingGame
import com.rally26.fundraisinggame.domain.FundraisingGameEntry
import com.rally26.fundraisinggame.domain.FundraisingGameStatus
import com.rally26.fundraisinggame.domain.FundraisingGameType
import com.rally26.fundraisinggame.persistence.FundraisingGameRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

data class FundraisingGamePermissions(
    val canConfigure: Boolean,
    val canOpen: Boolean,
    val canClose: Boolean,
    val canDrawWinner: Boolean,
)

@Service
class FundraisingGameService(
    private val repository: FundraisingGameRepository,
    private val campaignService: CampaignService,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    private val secureRandom = SecureRandom()

    fun getForManagement(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): FundraisingGame? {
        campaignService.get(organizationId, campaignId, currentUser)
        return repository.findByCampaign(campaignId)
    }

    fun permissionsFor(
        game: FundraisingGame,
        currentUser: CurrentUser,
    ): FundraisingGamePermissions {
        val membership = membershipService.requireActiveMembership(game.organizationId, currentUser)
        val manager = membership.role == MembershipRole.OWNER || membership.role == MembershipRole.ADMINISTRATOR
        val owner = membership.role == MembershipRole.OWNER
        val creatorOrManager = manager || game.createdByUserId == currentUser.userId
        val campaign = campaignService.get(game.organizationId, game.campaignId, currentUser)
        val campaignPermissions = campaignService.permissionsFor(campaign, currentUser)
        val canConfigure = game.status == FundraisingGameStatus.DRAFT && (campaignPermissions.canEdit || owner)
        val entryCount = repository.countEntries(game.id)
        return FundraisingGamePermissions(
            canConfigure = canConfigure,
            canOpen = game.status == FundraisingGameStatus.DRAFT && campaign.status == CampaignStatus.ACTIVE && creatorOrManager,
            canClose = game.status == FundraisingGameStatus.OPEN && creatorOrManager,
            canDrawWinner =
                owner &&
                    game.status == FundraisingGameStatus.CLOSED &&
                    game.gameType == FundraisingGameType.FREE_PRIZE_DRAWING &&
                    game.winnerEntryId == null &&
                    entryCount > 0,
        )
    }

    @Transactional
    fun create(
        organizationId: UUID,
        campaignId: UUID,
        gameType: FundraisingGameType,
        title: String,
        instructions: String?,
        prizeDescription: String?,
        maxEntries: Int?,
        entriesPerPerson: Int,
        rows: Int?,
        cols: Int?,
        currentUser: CurrentUser,
    ): FundraisingGame {
        val campaign = campaignService.get(organizationId, campaignId, currentUser)
        val campaignPermissions = campaignService.permissionsFor(campaign, currentUser)
        if (!campaignPermissions.canEdit) {
            throw ForbiddenException("FUNDRAISING_GAME_CREATE_DENIED", "You cannot add a game to this fundraiser.")
        }
        if (repository.findByCampaign(campaignId) != null) {
            throw ConflictException("FUNDRAISING_GAME_EXISTS", "This fundraiser already has a free game.")
        }
        val config = validateConfig(gameType, title, instructions, prizeDescription, maxEntries, entriesPerPerson, rows, cols)
        val game =
            repository.insert(
                organizationId,
                campaignId,
                currentUser.userId,
                gameType,
                title.trim(),
                instructions.clean(),
                prizeDescription.clean(),
                config.maxEntries,
                entriesPerPerson,
                config.rows,
                config.cols,
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "fundraising_game.created",
            "fundraising_game",
            game.id,
            teamId = campaign.teamId,
            summary = "Free fundraising game created",
        )
        return game
    }

    @Transactional
    fun update(
        organizationId: UUID,
        campaignId: UUID,
        title: String,
        instructions: String?,
        prizeDescription: String?,
        maxEntries: Int?,
        entriesPerPerson: Int,
        rows: Int?,
        cols: Int?,
        currentUser: CurrentUser,
    ): FundraisingGame {
        val game = requireGame(organizationId, campaignId)
        if (!permissionsFor(
                game,
                currentUser,
            ).canConfigure
        ) {
            throw ForbiddenException("FUNDRAISING_GAME_EDIT_DENIED", "This game can no longer be edited.")
        }
        val config = validateConfig(game.gameType, title, instructions, prizeDescription, maxEntries, entriesPerPerson, rows, cols)
        repository.updateDraft(
            game.id,
            organizationId,
            title.trim(),
            instructions.clean(),
            prizeDescription.clean(),
            config.maxEntries,
            entriesPerPerson,
            config.rows,
            config.cols,
        )
        auditService.record(
            currentUser.userId,
            organizationId,
            "fundraising_game.updated",
            "fundraising_game",
            game.id,
            summary = "Free fundraising game updated",
        )
        return requireGame(organizationId, campaignId)
    }

    @Transactional
    fun open(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): FundraisingGame {
        val game = requireGame(organizationId, campaignId)
        if (!permissionsFor(game, currentUser).canOpen) {
            throw ForbiddenException("FUNDRAISING_GAME_OPEN_DENIED", "The fundraiser must be active before this free game can open.")
        }
        repository.updateStatus(game.id, organizationId, FundraisingGameStatus.OPEN)
        auditService.record(
            currentUser.userId,
            organizationId,
            "fundraising_game.opened",
            "fundraising_game",
            game.id,
            summary = "Free fundraising game opened",
        )
        return requireGame(organizationId, campaignId)
    }

    @Transactional
    fun close(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): FundraisingGame {
        val game = requireGame(organizationId, campaignId)
        if (!permissionsFor(
                game,
                currentUser,
            ).canClose
        ) {
            throw ForbiddenException("FUNDRAISING_GAME_CLOSE_DENIED", "You cannot close this game.")
        }
        repository.updateStatus(game.id, organizationId, FundraisingGameStatus.CLOSED)
        auditService.record(
            currentUser.userId,
            organizationId,
            "fundraising_game.closed",
            "fundraising_game",
            game.id,
            summary = "Free fundraising game closed",
        )
        return requireGame(organizationId, campaignId)
    }

    @Transactional
    fun drawWinner(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): FundraisingGameEntry {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val game = requireGame(organizationId, campaignId)
        if (game.gameType !=
            FundraisingGameType.FREE_PRIZE_DRAWING
        ) {
            throw ValidationException("Random drawing is only available for a Free Prize Drawing.")
        }
        if (game.status != FundraisingGameStatus.CLOSED) throw ValidationException("Close the free drawing before selecting a winner.")
        if (game.winnerEntryId != null) return repository.findEntry(game.winnerEntryId, game.id)!!
        val entries = repository.listEntries(game.id)
        if (entries.isEmpty()) throw ValidationException("This drawing has no entries.")
        val winner = entries[secureRandom.nextInt(entries.size)]
        repository.markWinner(game.id, winner.id)
        auditService.record(
            currentUser.userId,
            organizationId,
            "fundraising_game.winner_selected",
            "fundraising_game",
            game.id,
            metadataJson = """{"entryId":"${winner.id}"}""",
            summary = "Free prize drawing winner selected",
        )
        return winner.copy(isWinner = true)
    }

    fun listEntries(
        organizationId: UUID,
        campaignId: UUID,
        currentUser: CurrentUser,
    ): List<FundraisingGameEntry> {
        val game = requireGame(organizationId, campaignId)
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.listEntries(game.id)
    }

    fun getPublicOrNull(slug: String): FundraisingGame? {
        val campaign = campaignService.getPublic(slug)
        return repository.findByCampaign(campaign.id)
    }

    fun getPublic(slug: String): FundraisingGame =
        getPublicOrNull(slug) ?: throw NotFoundException("FUNDRAISING_GAME_NOT_FOUND", "This fundraiser does not have a free game.")

    fun listPublicEntries(slug: String): List<FundraisingGameEntry> {
        val game = getPublic(slug)
        return repository.listEntries(game.id)
    }

    @Transactional
    fun enterPublic(
        slug: String,
        displayName: String,
        email: String,
        selectionKey: String?,
        selectionText: String?,
    ): FundraisingGameEntry {
        val campaign = campaignService.getPublic(slug)
        val initial =
            repository.findByCampaign(campaign.id)
                ?: throw NotFoundException("FUNDRAISING_GAME_NOT_FOUND", "This fundraiser does not have a free game.")
        val game =
            repository.findByIdForUpdate(initial.id)
                ?: throw NotFoundException("FUNDRAISING_GAME_NOT_FOUND", "This game could not be found.")
        if (game.status != FundraisingGameStatus.OPEN) throw ValidationException("This free game is not accepting entries right now.")
        val cleanName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.isEmpty() || cleanName.length > 120) throw ValidationException("Enter a display name using 120 characters or fewer.")
        if (!cleanEmail.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) ||
            cleanEmail.length > 254
        ) {
            throw ValidationException("Enter a valid email address.")
        }
        if (repository.countEntriesByEmail(game.id, cleanEmail) >= game.entriesPerPerson) {
            throw ConflictException("FREE_GAME_ENTRY_LIMIT", "You have already used the free entries allowed for this game.")
        }
        val total = repository.countEntries(game.id)
        if (game.maxEntries != null &&
            total >= game.maxEntries
        ) {
            throw ConflictException("FREE_GAME_FULL", "This free game has reached its entry limit.")
        }
        val normalizedSelection = normalizeSelection(game, selectionKey, selectionText)
        return try {
            repository.insertEntry(game.id, cleanName, cleanEmail, normalizedSelection.first, normalizedSelection.second)
        } catch (e: DuplicateKeyException) {
            throw ConflictException("FREE_GAME_SELECTION_TAKEN", "That selection was just taken. Choose another one.")
        }
    }

    private fun requireGame(
        organizationId: UUID,
        campaignId: UUID,
    ): FundraisingGame =
        repository.findByCampaign(campaignId)?.takeIf { it.organizationId == organizationId }
            ?: throw NotFoundException("FUNDRAISING_GAME_NOT_FOUND", "This fundraiser does not have a free game.")

    private data class Config(
        val maxEntries: Int?,
        val rows: Int?,
        val cols: Int?,
    )

    private fun validateConfig(
        gameType: FundraisingGameType,
        title: String,
        instructions: String?,
        prizeDescription: String?,
        maxEntries: Int?,
        entriesPerPerson: Int,
        rows: Int?,
        cols: Int?,
    ): Config {
        if (title.isBlank() || title.length > 160) throw ValidationException("Game title is required and must be 160 characters or fewer.")
        if ((instructions?.length ?: 0) > 3000) throw ValidationException("Game instructions must be 3,000 characters or fewer.")
        if ((prizeDescription?.length ?: 0) > 1000) throw ValidationException("Prize description must be 1,000 characters or fewer.")
        if (entriesPerPerson !in 1..20) throw ValidationException("Free entries per person must be between 1 and 20.")
        if (maxEntries != null &&
            maxEntries !in 1..100_000
        ) {
            throw ValidationException("Entry limit must be between 1 and 100,000, or unlimited.")
        }
        if (gameType == FundraisingGameType.BIG_GAME_SQUARES) {
            val r = rows ?: 10
            val c = cols ?: 10
            if (r !in 1..26 || c !in 1..26) throw ValidationException("Squares grids can be between 1 and 26 rows/columns.")
            return Config(r * c, r, c)
        }
        return Config(maxEntries, null, null)
    }

    private fun normalizeSelection(
        game: FundraisingGame,
        selectionKey: String?,
        selectionText: String?,
    ): Pair<String?, String?> =
        when (game.gameType) {
            FundraisingGameType.BIG_GAME_SQUARES -> {
                val key = selectionKey?.trim() ?: throw ValidationException("Choose an open square.")
                val match = Regex("^r(\\d+)c(\\d+)$").matchEntire(key) ?: throw ValidationException("Choose a valid square.")
                val row = match.groupValues[1].toInt()
                val col = match.groupValues[2].toInt()
                if (row !in 0 until (game.rows ?: 0) ||
                    col !in 0 until (game.cols ?: 0)
                ) {
                    throw ValidationException("Choose a valid square.")
                }
                key to null
            }
            FundraisingGameType.FREE_PRIZE_DRAWING -> null to null
            FundraisingGameType.BRACKET_CHALLENGE,
            FundraisingGameType.PREDICTION_CHALLENGE,
            FundraisingGameType.TRIVIA_CHALLENGE,
            -> {
                val text =
                    selectionText?.trim()?.takeIf { it.isNotEmpty() } ?: throw ValidationException("Enter your free-game pick or answer.")
                if (text.length > 1000) throw ValidationException("Your pick or answer must be 1,000 characters or fewer.")
                null to text
            }
        }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
