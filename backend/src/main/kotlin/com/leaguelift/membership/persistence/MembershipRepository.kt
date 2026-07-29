package com.leaguelift.membership.persistence

import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class MembershipRepository(private val jdbcClient: JdbcClient) {

	fun findActiveMembership(organizationId: UUID, userId: UUID): OrganizationMembership? =
		jdbcClient.sql(
			"""
			select id, organization_id, user_id, role, status, created_at, updated_at
			from organization_membership
			where organization_id = :organizationId and user_id = :userId and status = 'ACTIVE'
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("userId", userId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun listForOrganization(organizationId: UUID, offset: Int, limit: Int): List<OrganizationMembership> =
		jdbcClient.sql(
			"""
			select id, organization_id, user_id, role, status, created_at, updated_at
			from organization_membership
			where organization_id = :organizationId
			order by created_at asc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countForOrganization(organizationId: UUID): Long =
		jdbcClient.sql("select count(*) from organization_membership where organization_id = :organizationId")
			.param("organizationId", organizationId)
			.query(Long::class.java)
			.single()

	/**
	 * The first ACTIVE organization_membership for this user, in no particular
	 * cross-organization order (a user is only expected to belong to one
	 * organization in practice today). Used by dashboard role resolution
	 * (`DashboardContextService`) to determine Owner/Coach context without the
	 * caller already knowing which organization to ask about.
	 */
	fun findAnyActiveMembershipForUser(userId: UUID): OrganizationMembership? =
		jdbcClient.sql(
			"""
			select id, organization_id, user_id, role, status, created_at, updated_at
			from organization_membership
			where user_id = :userId and status = 'ACTIVE'
			order by created_at asc
			limit 1
			""".trimIndent(),
		)
			.param("userId", userId)
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
		jdbcClient.sql(
			"""
			select id, organization_id, user_id, role, status, created_at, updated_at
			from organization_membership
			where user_id = :userId and status = 'ACTIVE'
			order by created_at asc
			""".trimIndent(),
		)
			.param("userId", userId)
			.query(::mapRow)
			.list()

	fun findById(membershipId: UUID): OrganizationMembership? =
		jdbcClient.sql(
			"""
			select id, organization_id, user_id, role, status, created_at, updated_at
			from organization_membership
			where id = :membershipId
			""".trimIndent(),
		)
			.param("membershipId", membershipId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun existsForUser(organizationId: UUID, userId: UUID): Boolean =
		jdbcClient.sql(
			"""
			select exists(
				select 1 from organization_membership
				where organization_id = :organizationId and user_id = :userId and status = 'ACTIVE'
			)
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("userId", userId)
			.query(Boolean::class.java)
			.single()

	fun updateRole(membershipId: UUID, role: MembershipRole): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"update organization_membership set role = :role, updated_at = :now where id = :membershipId",
		)
			.param("role", role.name)
			.param("now", Timestamp.from(now))
			.param("membershipId", membershipId)
			.update()
	}

	fun revoke(membershipId: UUID): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"update organization_membership set status = 'REVOKED', updated_at = :now where id = :membershipId",
		)
			.param("now", Timestamp.from(now))
			.param("membershipId", membershipId)
			.update()
	}

	fun insert(organizationId: UUID, userId: UUID, role: MembershipRole): OrganizationMembership {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into organization_membership (id, organization_id, user_id, role, status, created_at, updated_at)
			values (:id, :organizationId, :userId, :role, 'ACTIVE', :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("userId", userId)
			.param("role", role.name)
			.param("now", Timestamp.from(now))
			.update()
		return OrganizationMembership(id, organizationId, userId, role, MembershipStatus.ACTIVE, now, now)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): OrganizationMembership =
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
