package com.rally26.authorization.persistence

import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.domain.RoleAssignmentStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, user_id, context_type, resource_id, role, status, granted_by, created_at, updated_at"

@Repository
class RoleAssignmentRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findActiveForUser(userId: UUID): List<RoleAssignment> =
        jdbcClient
            .sql("select $COLUMNS from role_assignment where user_id = :userId and status = 'ACTIVE'")
            .param("userId", userId)
            .query(::mapRow)
            .list()

    fun findActiveForUserAndContext(
        userId: UUID,
        contextType: RoleAssignmentContextType,
    ): List<RoleAssignment> =
        jdbcClient
            .sql(
                "select $COLUMNS from role_assignment where user_id = :userId and context_type = :contextType and status = 'ACTIVE'",
            ).param("userId", userId)
            .param("contextType", contextType.name)
            .query(::mapRow)
            .list()

    /** Whether *any* user already holds an active grant for this specific TEAM/TOURNAMENT/PARTICIPANT resource+role — e.g. checking a participant doesn't already have an athlete-self login before inviting a new one. */
    fun hasAnyActiveForResourceAndRole(
        contextType: RoleAssignmentContextType,
        resourceId: UUID,
        role: ResourceRole,
    ): Boolean =
        jdbcClient
            .sql(
                """
                select exists(
                    select 1 from role_assignment
                    where context_type = :contextType and resource_id = :resourceId and role = :role and status = 'ACTIVE'
                )
                """.trimIndent(),
            ).param("contextType", contextType.name)
            .param("resourceId", resourceId)
            .param("role", role.name)
            .query(Boolean::class.java)
            .single()

    /** The active grant (if any) this user holds for a specific TEAM/TOURNAMENT/PARTICIPANT resource. */
    fun findActiveForResource(
        userId: UUID,
        contextType: RoleAssignmentContextType,
        resourceId: UUID,
    ): RoleAssignment? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from role_assignment
                where user_id = :userId and context_type = :contextType and resource_id = :resourceId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("userId", userId)
            .param("contextType", contextType.name)
            .param("resourceId", resourceId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** The active PLATFORM grant (if any) for this user — resourceId/organizationId are always null for PLATFORM. */
    fun findActivePlatformGrant(userId: UUID): RoleAssignment? =
        jdbcClient
            .sql(
                "select $COLUMNS from role_assignment where user_id = :userId and context_type = 'PLATFORM' and role = 'PLATFORM_ADMIN' and status = 'ACTIVE'",
            ).param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Every active grant on a given resource — e.g. every coach assigned to a team. */
    fun listActiveForResource(
        contextType: RoleAssignmentContextType,
        resourceId: UUID,
    ): List<RoleAssignment> =
        jdbcClient
            .sql(
                "select $COLUMNS from role_assignment where context_type = :contextType and resource_id = :resourceId and status = 'ACTIVE'",
            ).param("contextType", contextType.name)
            .param("resourceId", resourceId)
            .query(::mapRow)
            .list()

    fun grant(
        organizationId: UUID?,
        userId: UUID,
        contextType: RoleAssignmentContextType,
        resourceId: UUID?,
        role: ResourceRole,
        grantedBy: UUID?,
    ): RoleAssignment {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into role_assignment (id, organization_id, user_id, context_type, resource_id, role, status, granted_by, created_at, updated_at)
                values (:id, :organizationId, :userId, :contextType, :resourceId, :role, 'ACTIVE', :grantedBy, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("contextType", contextType.name)
            .param("resourceId", resourceId)
            .param("role", role.name)
            .param("grantedBy", grantedBy)
            .param("now", Timestamp.from(now))
            .update()
        return RoleAssignment(id, organizationId, userId, contextType, resourceId, role, RoleAssignmentStatus.ACTIVE, grantedBy, now, now)
    }

    fun revoke(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("update role_assignment set status = 'REVOKED', updated_at = :now where id = :id")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): RoleAssignment =
        RoleAssignment(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            contextType = RoleAssignmentContextType.valueOf(rs.getString("context_type")),
            resourceId = rs.getObject("resource_id", UUID::class.java),
            role = ResourceRole.valueOf(rs.getString("role")),
            status = RoleAssignmentStatus.valueOf(rs.getString("status")),
            grantedBy = rs.getObject("granted_by", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
