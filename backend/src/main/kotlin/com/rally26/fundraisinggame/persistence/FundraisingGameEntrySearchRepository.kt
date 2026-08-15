package com.rally26.fundraisinggame.persistence

import com.rally26.fundraisinggame.domain.FundraisingGameEntryListCriteria
import com.rally26.fundraisinggame.domain.FundraisingGameEntryListSort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FundraisingGameEntrySearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun searchIds(
        gameId: UUID,
        criteria: FundraisingGameEntryListCriteria,
        offset: Int,
        limit: Int,
    ): List<UUID> {
        val built = buildSql(criteria, countOnly = false)

        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("gameId", gameId)
                .param("offset", offset)
                .param("limit", limit)

        built.second.forEach { (name, value) ->
            statement = statement.param(name, value)
        }

        return statement
            .query(UUID::class.java)
            .list()
            .filterNotNull()
    }

    fun count(
        gameId: UUID,
        criteria: FundraisingGameEntryListCriteria,
    ): Long {
        val built = buildSql(criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first).param("gameId", gameId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSql(
        criteria: FundraisingGameEntryListCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from fundraising_game_entry e where e.game_id = :gameId"
                } else {
                    "select e.id from fundraising_game_entry e where e.game_id = :gameId"
                },
            )
        val params = linkedMapOf<String, Any>()

        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                """
                and (
                   lower(e.display_name) like :keyword
                   or lower(e.email) like :keyword
                   or lower(coalesce(e.selection_key, '')) like :keyword
                   or lower(coalesce(e.selection_text, '')) like :keyword
                )
                """.trimIndent(),
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }
        if (criteria.winnerOnly) sql.append(" and e.is_winner = true")

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    FundraisingGameEntryListSort.NEWEST -> " order by e.created_at desc"
                    FundraisingGameEntryListSort.OLDEST -> " order by e.created_at asc"
                    FundraisingGameEntryListSort.NAME_ASC -> " order by lower(e.display_name) asc, e.created_at desc"
                },
            )
        }
        return sql.toString() to params
    }
}
