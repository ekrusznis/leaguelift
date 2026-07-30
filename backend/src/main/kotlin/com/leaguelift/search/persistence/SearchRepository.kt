package com.leaguelift.search.persistence

import com.leaguelift.search.domain.SearchHit
import com.leaguelift.search.domain.SearchResultType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/** Escapes ILIKE wildcards in user input so a search for e.g. "50%" or "a_b" doesn't behave like a wildcard pattern. */
private fun likePattern(query: String): String =
	"%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/**
 * Global search (DESIGN-DOC.md section 13, Phase 7 completion) — a dedicated
 * read-model repository rather than adding search methods to team/household/
 * participant/organization's own repositories, to keep this isolated from their
 * existing, well-tested CRUD queries.
 */
@Repository
class SearchRepository(private val jdbcClient: JdbcClient) {

	fun searchTeams(organizationId: UUID, query: String, limit: Int): List<SearchHit> =
		jdbcClient.sql(
			"""
			select id, name, sport from team
			where organization_id = :organizationId and status <> 'ARCHIVED' and name ilike :pattern escape '\'
			order by name asc
			limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("pattern", likePattern(query))
			.param("limit", limit)
			.query { rs, _ ->
				SearchHit(SearchResultType.TEAM, rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("sport"))
			}
			.list()

	fun searchParticipants(organizationId: UUID, query: String, limit: Int): List<SearchHit> =
		jdbcClient.sql(
			"""
			select id, first_name, last_name from participant
			where organization_id = :organizationId and status = 'ACTIVE'
			  and (first_name || ' ' || last_name) ilike :pattern escape '\'
			order by last_name asc, first_name asc
			limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("pattern", likePattern(query))
			.param("limit", limit)
			.query { rs, _ ->
				SearchHit(
					SearchResultType.PARTICIPANT,
					rs.getObject("id", UUID::class.java),
					"${rs.getString("first_name")} ${rs.getString("last_name")}",
					null,
				)
			}
			.list()

	/** Matches on the household's own display name or any active adult's name/email — a family is usually found by a parent's name, not the household record's own label. */
	fun searchHouseholds(organizationId: UUID, query: String, limit: Int): List<SearchHit> =
		jdbcClient.sql(
			"""
			select distinct h.id, h.display_name from household h
			left join household_adult ha on ha.household_id = h.id and ha.status = 'ACTIVE'
			where h.organization_id = :organizationId and h.status = 'ACTIVE'
			  and (
			    h.display_name ilike :pattern escape '\'
			    or ha.first_name ilike :pattern escape '\'
			    or ha.last_name ilike :pattern escape '\'
			    or ha.email ilike :pattern escape '\'
			  )
			order by h.display_name asc
			limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("pattern", likePattern(query))
			.param("limit", limit)
			.query { rs, _ ->
				SearchHit(SearchResultType.HOUSEHOLD, rs.getObject("id", UUID::class.java), rs.getString("display_name"), null)
			}
			.list()

	/** Platform-admin-only — organizations across the whole platform, not scoped to one organization. */
	fun searchOrganizations(query: String, limit: Int): List<SearchHit> =
		jdbcClient.sql(
			"""
			select id, name, slug from organization
			where status <> 'ARCHIVED' and name ilike :pattern escape '\'
			order by name asc
			limit :limit
			""".trimIndent(),
		)
			.param("pattern", likePattern(query))
			.param("limit", limit)
			.query { rs, _ ->
				SearchHit(SearchResultType.ORGANIZATION, rs.getObject("id", UUID::class.java), rs.getString("name"), "/" + rs.getString("slug"))
			}
			.list()
}
