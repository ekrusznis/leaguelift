package com.rally26.membership.persistence

import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipSearchCriteria
import com.rally26.membership.domain.MembershipSearchRow
import com.rally26.membership.domain.MembershipSearchSort
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class MembershipRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findActiveMembership(
        organizationId: UUID,
        userId: UUID,
    ): OrganizationMembership? =
        jdbcClient
            .sql(
                """
                select id, organization_id, user_id, role, status, created_at, updated_at
                from organization_membership
                where organization_id = :organizationId and user_id = :userId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listForOrganization(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<OrganizationMembership> =
        jdbcClient
            .sql(
                """
                select id, organization_id, user_id, role, status, created_at, updated_at
                from organization_membership
                where organization_id = :organizationId
                order by created_at asc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countForOrganization(organizationId: UUID): Long =
        jdbcClient
            .sql("select count(*) from organization_membership where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    /**
     * `/members/search` — the frontend's Members list has always called this endpoint
     * (`frontend/src/features/members/searchApi.ts`), but no backend mapping for it ever
     * existed (LR-027, same class as LR-016/018/020/025/026). Joins `app_user` directly
     * (rather than the N+1-per-row lookup [MembershipController.list] does) since a
     * keyword search against email/display name needs that join anyway.
     */
    fun search(
        organizationId: UUID,
        criteria: MembershipSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<MembershipSearchRow> {
        val built = buildSearchSql(organizationId, criteria, countOnly = false)
        var statement = jdbcClient.sql("${built.first} offset :offset limit :limit").param("offset", offset).param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapSearchRow).list()
    }

    fun countSearch(
        organizationId: UUID,
        criteria: MembershipSearchCriteria,
    ): Long {
        val built = buildSearchSql(organizationId, criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSearchSql(
        organizationId: UUID,
        criteria: MembershipSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from organization_membership om join app_user u on u.id = om.user_id"
                } else {
                    """select om.id, om.organization_id, om.user_id, om.role, om.status, om.created_at, om.updated_at,
                       u.email as user_email, u.display_name as user_display_name
                       from organization_membership om join app_user u on u.id = om.user_id"""
                },
            )
        sql.append(" where om.organization_id = :organizationId")
        val params = linkedMapOf<String, Any>("organizationId" to organizationId)

        criteria.role?.let {
            sql.append(" and om.role = :role")
            params["role"] = it.name
        }
        criteria.status?.let {
            sql.append(" and om.status = :status")
            params["status"] = it.name
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(" and (lower(coalesce(u.display_name, '')) like :keyword or lower(coalesce(u.email, '')) like :keyword)")
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    MembershipSearchSort.NAME_ASC -> " order by lower(coalesce(u.display_name, '')) asc, om.created_at asc"
                    MembershipSearchSort.NAME_DESC -> " order by lower(coalesce(u.display_name, '')) desc, om.created_at desc"
                    MembershipSearchSort.ROLE_ASC -> " order by om.role asc, lower(coalesce(u.display_name, '')) asc"
                    MembershipSearchSort.NEWEST -> " order by om.created_at desc"
                    MembershipSearchSort.OLDEST -> " order by om.created_at asc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun mapSearchRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): MembershipSearchRow =
        MembershipSearchRow(
            membership = mapRow(rs, rowNum),
            userEmail = rs.getString("user_email"),
            userDisplayName = rs.getString("user_display_name"),
        )

    /**
     * Every active OWNER/ADMINISTRATOR in this organization, unpaginated — used by
     * [com.rally26.authorization.application.AuthorizationService.listTeamStaffUserIds]
     * to resolve the inherited-team-manager half of a notification recipient set (Phase
     * 10 slice 4, ADR-029), not [listForOrganization]'s paginated member-list use case.
     */
    fun listActiveManagers(organizationId: UUID): List<OrganizationMembership> =
        jdbcClient
            .sql(
                """
                select id, organization_id, user_id, role, status, created_at, updated_at
                from organization_membership
                where organization_id = :organizationId and status = 'ACTIVE' and role in ('OWNER', 'ADMINISTRATOR')
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    /**
     * The first ACTIVE organization_membership for this user, in no particular
     * cross-organization order (a user is only expected to belong to one
     * organization in practice today). Used by dashboard role resolution
     * (`DashboardContextService`) to determine Owner/Coach context without the
     * caller already knowing which organization to ask about.
     */
    fun findAnyActiveMembershipForUser(userId: UUID): OrganizationMembership? =
        jdbcClient
            .sql(
                """
                select id, organization_id, user_id, role, status, created_at, updated_at
                from organization_membership
                where user_id = :userId and status = 'ACTIVE'
                order by created_at asc
                limit 1
                """.trimIndent(),
            ).param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /**
     * Every ACTIVE organization_membership for this user, across all organizations —
     * unlike [findAnyActiveMembershipForUser] (which returns just one, for dashboard
     * routing), this backs `GET /me/contexts` (AuthorizationService), which must
     * enumerate every ORGANIZATION context a user holds.
     */
    fun listActiveForUser(userId: UUID): List<OrganizationMembership> =
        jdbcClient
            .sql(
                """
                select id, organization_id, user_id, role, status, created_at, updated_at
                from organization_membership
                where user_id = :userId and status = 'ACTIVE'
                order by created_at asc
                """.trimIndent(),
            ).param("userId", userId)
            .query(::mapRow)
            .list()

    fun findById(membershipId: UUID): OrganizationMembership? =
        jdbcClient
            .sql(
                """
                select id, organization_id, user_id, role, status, created_at, updated_at
                from organization_membership
                where id = :membershipId
                """.trimIndent(),
            ).param("membershipId", membershipId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun existsForUser(
        organizationId: UUID,
        userId: UUID,
    ): Boolean =
        jdbcClient
            .sql(
                """
                select exists(
                	select 1 from organization_membership
                	where organization_id = :organizationId and user_id = :userId and status = 'ACTIVE'
                )
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("userId", userId)
            .query(Boolean::class.java)
            .single()

    fun updateRole(
        membershipId: UUID,
        role: MembershipRole,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                "update organization_membership set role = :role, updated_at = :now where id = :membershipId",
            ).param("role", role.name)
            .param("now", Timestamp.from(now))
            .param("membershipId", membershipId)
            .update()
    }

    fun revoke(membershipId: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                "update organization_membership set status = 'REVOKED', updated_at = :now where id = :membershipId",
            ).param("now", Timestamp.from(now))
            .param("membershipId", membershipId)
            .update()
    }

    fun insert(
        organizationId: UUID,
        userId: UUID,
        role: MembershipRole,
    ): OrganizationMembership {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into organization_membership (id, organization_id, user_id, role, status, created_at, updated_at)
                values (:id, :organizationId, :userId, :role, 'ACTIVE', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("role", role.name)
            .param("now", Timestamp.from(now))
            .update()
        return OrganizationMembership(id, organizationId, userId, role, MembershipStatus.ACTIVE, now, now)
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OrganizationMembership =
        OrganizationMembership(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            role = MembershipRole.valueOf(rs.getString("role")),
            status = MembershipStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
