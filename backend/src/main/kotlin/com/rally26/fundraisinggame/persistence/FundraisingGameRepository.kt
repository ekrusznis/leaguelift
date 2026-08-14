package com.rally26.fundraisinggame.persistence

import com.rally26.fundraisinggame.domain.FundraisingGame
import com.rally26.fundraisinggame.domain.FundraisingGameEntry
import com.rally26.fundraisinggame.domain.FundraisingGameStatus
import com.rally26.fundraisinggame.domain.FundraisingGameType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val GAME_COLUMNS =
    "id, organization_id, campaign_id, created_by_user_id, game_type, title, instructions, " +
        "prize_description, max_entries, entries_per_person, rows, cols, status, winner_entry_id, winner_selected_at, created_at, updated_at"
private const val ENTRY_COLUMNS = "id, game_id, display_name, email, selection_key, selection_text, is_winner, created_at"

@Repository
class FundraisingGameRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByCampaign(campaignId: UUID): FundraisingGame? =
        jdbcClient
            .sql("select $GAME_COLUMNS from fundraising_game where campaign_id = :campaignId")
            .param("campaignId", campaignId)
            .query(::mapGame)
            .optional()
            .orElse(null)

    fun findById(
        id: UUID,
        organizationId: UUID,
    ): FundraisingGame? =
        jdbcClient
            .sql("select $GAME_COLUMNS from fundraising_game where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapGame)
            .optional()
            .orElse(null)

    fun findByIdForUpdate(id: UUID): FundraisingGame? =
        jdbcClient
            .sql("select $GAME_COLUMNS from fundraising_game where id = :id for update")
            .param("id", id)
            .query(::mapGame)
            .optional()
            .orElse(null)

    fun insert(
        organizationId: UUID,
        campaignId: UUID,
        createdByUserId: UUID,
        gameType: FundraisingGameType,
        title: String,
        instructions: String?,
        prizeDescription: String?,
        maxEntries: Int?,
        entriesPerPerson: Int,
        rows: Int?,
        cols: Int?,
    ): FundraisingGame {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into fundraising_game
                (id, organization_id, campaign_id, created_by_user_id, game_type, title, instructions, prize_description, max_entries, entries_per_person, rows, cols, status, created_at, updated_at)
                values (:id, :organizationId, :campaignId, :createdByUserId, :gameType, :title, :instructions, :prizeDescription, :maxEntries, :entriesPerPerson, :rows, :cols, 'DRAFT', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("campaignId", campaignId)
            .param("createdByUserId", createdByUserId)
            .param("gameType", gameType.name)
            .param("title", title)
            .param("instructions", instructions)
            .param("prizeDescription", prizeDescription)
            .param("maxEntries", maxEntries)
            .param("entriesPerPerson", entriesPerPerson)
            .param("rows", rows)
            .param("cols", cols)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun updateDraft(
        id: UUID,
        organizationId: UUID,
        title: String,
        instructions: String?,
        prizeDescription: String?,
        maxEntries: Int?,
        entriesPerPerson: Int,
        rows: Int?,
        cols: Int?,
    ): Int =
        jdbcClient
            .sql(
                """
                update fundraising_game set title=:title, instructions=:instructions, prize_description=:prizeDescription,
                max_entries=:maxEntries, entries_per_person=:entriesPerPerson, rows=:rows, cols=:cols, updated_at=:now
                where id=:id and organization_id=:organizationId and status='DRAFT'
                """.trimIndent(),
            ).param("title", title)
            .param("instructions", instructions)
            .param("prizeDescription", prizeDescription)
            .param("maxEntries", maxEntries)
            .param("entriesPerPerson", entriesPerPerson)
            .param("rows", rows)
            .param("cols", cols)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: FundraisingGameStatus,
    ): Int =
        jdbcClient
            .sql("update fundraising_game set status=:status, updated_at=:now where id=:id and organization_id=:organizationId")
            .param(
                "status",
                status.name,
            ).param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun countEntries(gameId: UUID): Long =
        jdbcClient
            .sql("select count(*) from fundraising_game_entry where game_id=:gameId")
            .param("gameId", gameId)
            .query(Long::class.java)
            .single()

    fun countEntriesByEmail(
        gameId: UUID,
        email: String,
    ): Long =
        jdbcClient
            .sql("select count(*) from fundraising_game_entry where game_id=:gameId and lower(email)=lower(:email)")
            .param("gameId", gameId)
            .param("email", email)
            .query(Long::class.java)
            .single()

    fun insertEntry(
        gameId: UUID,
        displayName: String,
        email: String,
        selectionKey: String?,
        selectionText: String?,
    ): FundraisingGameEntry {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into fundraising_game_entry (id, game_id, display_name, email, selection_key, selection_text, created_at)
                values (:id, :gameId, :displayName, :email, :selectionKey, :selectionText, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("gameId", gameId)
            .param("displayName", displayName)
            .param("email", email.lowercase())
            .param("selectionKey", selectionKey)
            .param("selectionText", selectionText)
            .param("now", Timestamp.from(now))
            .update()
        return FundraisingGameEntry(id, gameId, displayName, email.lowercase(), selectionKey, selectionText, false, now)
    }

    fun listEntries(gameId: UUID): List<FundraisingGameEntry> =
        jdbcClient
            .sql("select $ENTRY_COLUMNS from fundraising_game_entry where game_id=:gameId order by created_at")
            .param("gameId", gameId)
            .query(::mapEntry)
            .list()

    fun findEntry(
        id: UUID,
        gameId: UUID,
    ): FundraisingGameEntry? =
        jdbcClient
            .sql("select $ENTRY_COLUMNS from fundraising_game_entry where id=:id and game_id=:gameId")
            .param("id", id)
            .param("gameId", gameId)
            .query(::mapEntry)
            .optional()
            .orElse(null)

    fun markWinner(
        gameId: UUID,
        entryId: UUID,
    ): Int {
        val now = Instant.now()
        jdbcClient.sql("update fundraising_game_entry set is_winner=false where game_id=:gameId").param("gameId", gameId).update()
        jdbcClient
            .sql("update fundraising_game_entry set is_winner=true where id=:entryId and game_id=:gameId")
            .param("entryId", entryId)
            .param("gameId", gameId)
            .update()
        return jdbcClient
            .sql(
                "update fundraising_game set winner_entry_id=:entryId, winner_selected_at=:now, status='CLOSED', updated_at=:now where id=:gameId",
            ).param("entryId", entryId)
            .param("now", Timestamp.from(now))
            .param("gameId", gameId)
            .update()
    }

    private fun mapGame(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ) = FundraisingGame(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        campaignId = rs.getObject("campaign_id", UUID::class.java),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        gameType = FundraisingGameType.valueOf(rs.getString("game_type")),
        title = rs.getString("title"),
        instructions = rs.getString("instructions"),
        prizeDescription = rs.getString("prize_description"),
        maxEntries = rs.getObject("max_entries", Integer::class.java)?.toInt(),
        entriesPerPerson = rs.getInt("entries_per_person"),
        rows = rs.getObject("rows", Integer::class.java)?.toInt(),
        cols = rs.getObject("cols", Integer::class.java)?.toInt(),
        status = FundraisingGameStatus.valueOf(rs.getString("status")),
        winnerEntryId = rs.getObject("winner_entry_id", UUID::class.java),
        winnerSelectedAt = rs.getTimestamp("winner_selected_at")?.toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapEntry(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ) = FundraisingGameEntry(
        id = rs.getObject("id", UUID::class.java),
        gameId = rs.getObject("game_id", UUID::class.java),
        displayName = rs.getString("display_name"),
        email = rs.getString("email"),
        selectionKey = rs.getString("selection_key"),
        selectionText = rs.getString("selection_text"),
        isWinner = rs.getBoolean("is_winner"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
