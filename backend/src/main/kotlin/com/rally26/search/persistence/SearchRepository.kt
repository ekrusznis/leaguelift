package com.rally26.search.persistence

import com.rally26.search.domain.SearchHit
import com.rally26.search.domain.SearchResultType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/** Escapes ILIKE wildcards in user input so a search for e.g. "50%" or "a_b" doesn't behave like a wildcard pattern. */
private fun likePattern(query: String): String = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/**
 * Global search (DESIGN-DOC.md section 13, Phase 7 completion) — a dedicated
 * read-model repository rather than adding search methods to team/household/
 * participant/organization's own repositories, to keep this isolated from their
 * existing, well-tested CRUD queries.
 */
@Repository
class SearchRepository(
    private val jdbcClient: JdbcClient,
) {
    /**
     * `teamIds = null` means unrestricted (Owner/Administrator, or the platform-admin
     * bypass) — every other caller must pass a (possibly empty) team scope, computed by
     * [resolveTeamScope]. An empty set deliberately still runs the query (rather than
     * short-circuiting) since `= any('{}')` correctly matches nothing in Postgres — this
     * keeps one code path instead of two.
     */
    fun searchTeams(
        organizationId: UUID,
        query: String,
        limit: Int,
        teamIds: Set<UUID>?,
    ): List<SearchHit> =
        jdbcClient
            .sql(
                """
                select id, name, sport from team
                where organization_id = :organizationId and status <> 'ARCHIVED' and name ilike :pattern escape '\'
                  and (:unrestricted or id = any(:teamIds))
                order by name asc
                limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("pattern", likePattern(query))
            .param("unrestricted", teamIds == null)
            .param("teamIds", (teamIds ?: emptySet()).toTypedArray())
            .param("limit", limit)
            .query { rs, _ ->
                SearchHit(SearchResultType.TEAM, rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("sport"))
            }.list()

    /** A team-scoped caller only sees teammates — participants who share an active roster spot on one of [teamIds] with them. See [searchTeams] for the `teamIds = null` (unrestricted) convention. */
    fun searchParticipants(
        organizationId: UUID,
        query: String,
        limit: Int,
        teamIds: Set<UUID>?,
    ): List<SearchHit> =
        jdbcClient
            .sql(
                """
                select id, first_name, last_name from participant p
                where p.organization_id = :organizationId and p.status = 'ACTIVE'
                  and (p.first_name || ' ' || p.last_name) ilike :pattern escape '\'
                  and (
                    :unrestricted
                    or exists (
                        select 1 from participant_team pt
                        where pt.participant_id = p.id and pt.status = 'ACTIVE' and pt.team_id = any(:teamIds)
                    )
                  )
                order by last_name asc, first_name asc
                limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("pattern", likePattern(query))
            .param("unrestricted", teamIds == null)
            .param("teamIds", (teamIds ?: emptySet()).toTypedArray())
            .param("limit", limit)
            .query { rs, _ ->
                SearchHit(
                    SearchResultType.PARTICIPANT,
                    rs.getObject("id", UUID::class.java),
                    "${rs.getString("first_name")} ${rs.getString("last_name")}",
                    null,
                )
            }.list()

    /**
     * Matches on the household's own display name or any active adult's name/email — a
     * family is usually found by a parent's name, not the household record's own label.
     * A team-scoped caller only sees households with at least one participant rostered
     * on one of [teamIds] — a teammate's family, not every family in the org. See
     * [searchTeams] for the `teamIds = null` (unrestricted) convention.
     */
    fun searchHouseholds(
        organizationId: UUID,
        query: String,
        limit: Int,
        teamIds: Set<UUID>?,
    ): List<SearchHit> =
        jdbcClient
            .sql(
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
                  and (
                    :unrestricted
                    or exists (
                        select 1 from participant p2
                        join participant_team pt on pt.participant_id = p2.id and pt.status = 'ACTIVE'
                        where p2.household_id = h.id and pt.team_id = any(:teamIds)
                    )
                  )
                order by h.display_name asc
                limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("pattern", likePattern(query))
            .param("unrestricted", teamIds == null)
            .param("teamIds", (teamIds ?: emptySet()).toTypedArray())
            .param("limit", limit)
            .query { rs, _ ->
                SearchHit(SearchResultType.HOUSEHOLD, rs.getObject("id", UUID::class.java), rs.getString("display_name"), null)
            }.list()

    /**
     * Every team a user is associated with in this organization, regardless of how —
     * coaching it (`role_assignment(TEAM)`), playing on it as the athlete themselves
     * (`role_assignment(PARTICIPANT)` -> `participant_team`), or guardian of a household
     * with a participant on it (`guardian_relationship` -> `participant` ->
     * `participant_team`). A single UNION so a user holding more than one of these roles
     * (e.g. a coach who is also a guardian elsewhere in the org) gets the combined scope,
     * not just one path's.
     */
    fun resolveTeamScope(
        organizationId: UUID,
        userId: UUID,
    ): Set<UUID> =
        jdbcClient
            .sql(
                """
                select distinct team_id from (
                    select ra.resource_id as team_id
                    from role_assignment ra
                    where ra.user_id = :userId and ra.context_type = 'TEAM' and ra.status = 'ACTIVE'
                      and ra.organization_id = :organizationId

                    union

                    select pt.team_id
                    from role_assignment ra
                    join participant_team pt on pt.participant_id = ra.resource_id and pt.status = 'ACTIVE'
                    where ra.user_id = :userId and ra.context_type = 'PARTICIPANT' and ra.status = 'ACTIVE'
                      and ra.organization_id = :organizationId

                    union

                    select pt.team_id
                    from guardian_relationship gr
                    join participant p on p.household_id = gr.household_id
                    join participant_team pt on pt.participant_id = p.id and pt.status = 'ACTIVE'
                    where gr.user_id = :userId and gr.status = 'ACTIVE'
                      and gr.organization_id = :organizationId
                ) scoped_teams
                """.trimIndent(),
            ).param("userId", userId)
            .param("organizationId", organizationId)
            .query(UUID::class.java)
            .list()
            .filterNotNull()
            .toSet()

    /** Platform-admin-only — organizations across the whole platform, not scoped to one organization. */
    fun searchOrganizations(
        query: String,
        limit: Int,
    ): List<SearchHit> =
        jdbcClient
            .sql(
                """
                select id, name, slug from organization
                where status <> 'ARCHIVED' and name ilike :pattern escape '\'
                order by name asc
                limit :limit
                """.trimIndent(),
            ).param("pattern", likePattern(query))
            .param("limit", limit)
            .query { rs, _ ->
                SearchHit(
                    SearchResultType.ORGANIZATION,
                    rs.getObject("id", UUID::class.java),
                    rs.getString("name"),
                    "/" + rs.getString("slug"),
                )
            }.list()
}
