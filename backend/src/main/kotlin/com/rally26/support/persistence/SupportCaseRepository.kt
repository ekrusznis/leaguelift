package com.rally26.support.persistence

import com.rally26.common.web.PageRequest
import com.rally26.support.domain.SupportCase
import com.rally26.support.domain.SupportCaseCategory
import com.rally26.support.domain.SupportCasePriority
import com.rally26.support.domain.SupportCaseStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val CASE_COLUMNS = """
sc.id, sc.idempotency_key, sc.organization_id, o.name as organization_name,
sc.requester_user_id, sc.requester_name, sc.requester_email, sc.category, sc.priority,
sc.subject, sc.description, sc.status, sc.assigned_platform_user_id,
assigned.display_name as assigned_platform_user_name, sc.resolution, sc.closed_at,
sc.created_at, sc.updated_at
"""

@Repository
class SupportCaseRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(
        idempotencyKey: String,
        organizationId: UUID?,
        requesterUserId: UUID?,
        requesterName: String,
        requesterEmail: String,
        category: SupportCaseCategory,
        subject: String,
        description: String,
    ): SupportCase =
        jdbcClient
            .sql(
                """
                insert into support_case
                    (id, idempotency_key, organization_id, requester_user_id, requester_name, requester_email,
                     category, priority, subject, description, status, created_at, updated_at)
                values
                    (:id, :key, :organizationId, :requesterUserId, :requesterName, :requesterEmail,
                     :category, 'NORMAL', :subject, :description, 'OPEN', now(), now())
                returning id
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("key", idempotencyKey)
            .param("organizationId", organizationId)
            .param("requesterUserId", requesterUserId)
            .param("requesterName", requesterName)
            .param("requesterEmail", requesterEmail)
            .param("category", category.name)
            .param("subject", subject)
            .param("description", description)
            .query(UUID::class.java)
            .single()
            .let { findById(it)!! }

    fun findById(id: UUID): SupportCase? =
        base("where sc.id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByIdempotencyKey(key: String): SupportCase? =
        base("where sc.idempotency_key = :key")
            .param("key", key)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listForRequester(
        userId: UUID,
        page: PageRequest,
    ): List<SupportCase> =
        base("where sc.requester_user_id = :userId order by sc.created_at desc limit :limit offset :offset")
            .param("userId", userId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(::mapRow)
            .list()

    fun countForRequester(userId: UUID): Long =
        jdbcClient
            .sql("select count(*) from support_case where requester_user_id = :userId")
            .param("userId", userId)
            .query(Long::class.java)
            .single()

    fun findForRequester(
        id: UUID,
        userId: UUID,
    ): SupportCase? =
        base("where sc.id = :id and sc.requester_user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listPlatform(
        query: String?,
        status: SupportCaseStatus?,
        priority: SupportCasePriority?,
        category: SupportCaseCategory?,
        organizationId: UUID?,
        page: PageRequest,
    ): List<SupportCase> {
        val where = platformWhere(query, status, priority, category, organizationId)
        var spec =
            base(
                "${where.sql} order by case when sc.priority = 'URGENT' then 0 when sc.priority = 'HIGH' then 1 when sc.priority = 'NORMAL' then 2 else 3 end, sc.created_at desc limit :limit offset :offset",
            ).param("limit", page.size)
                .param("offset", page.offset)
        spec = bindPlatform(spec, query, status, priority, category, organizationId)
        return spec.query(::mapRow).list()
    }

    fun countPlatform(
        query: String?,
        status: SupportCaseStatus?,
        priority: SupportCasePriority?,
        category: SupportCaseCategory?,
        organizationId: UUID?,
    ): Long {
        val where = platformWhere(query, status, priority, category, organizationId)
        var spec = jdbcClient.sql("select count(*) from support_case sc left join organization o on o.id = sc.organization_id ${where.sql}")
        spec = bindPlatform(spec, query, status, priority, category, organizationId)
        return spec.query(Long::class.java).single()
    }

    fun updatePlatform(
        id: UUID,
        status: SupportCaseStatus,
        priority: SupportCasePriority,
        assignedPlatformUserId: UUID?,
        resolution: String?,
        closedAt: Instant?,
    ): Int =
        jdbcClient
            .sql(
                """
                update support_case set status = :status, priority = :priority,
                    assigned_platform_user_id = :assigned, resolution = :resolution,
                    closed_at = :closedAt, updated_at = now()
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .param("status", status.name)
            .param("priority", priority.name)
            .param("assigned", assignedPlatformUserId)
            .param("resolution", resolution)
            .param("closedAt", closedAt?.let(Timestamp::from))
            .update()

    fun isActivePlatformAdmin(userId: UUID): Boolean =
        jdbcClient
            .sql(
                "select exists(select 1 from role_assignment where user_id = :userId and context_type = 'PLATFORM' and role = 'PLATFORM_ADMIN' and status = 'ACTIVE')",
            ).param("userId", userId)
            .query(Boolean::class.java)
            .single()

    private fun base(suffix: String): JdbcClient.StatementSpec =
        jdbcClient.sql(
            "select $CASE_COLUMNS from support_case sc left join organization o on o.id = sc.organization_id left join app_user assigned on assigned.id = sc.assigned_platform_user_id $suffix",
        )

    private data class FilterSql(
        val sql: String,
    )

    private fun platformWhere(
        query: String?,
        status: SupportCaseStatus?,
        priority: SupportCasePriority?,
        category: SupportCaseCategory?,
        organizationId: UUID?,
    ): FilterSql {
        val clauses = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            clauses +=
                "(lower(sc.subject) like :query or lower(sc.requester_name) like :query or lower(sc.requester_email) like :query or lower(coalesce(o.name, '')) like :query)"
        }
        if (status != null) clauses += "sc.status = :status"
        if (priority != null) clauses += "sc.priority = :priority"
        if (category != null) clauses += "sc.category = :category"
        if (organizationId != null) clauses += "sc.organization_id = :organizationId"
        return FilterSql(if (clauses.isEmpty()) "" else "where ${clauses.joinToString(" and ")}")
    }

    private fun bindPlatform(
        initial: JdbcClient.StatementSpec,
        query: String?,
        status: SupportCaseStatus?,
        priority: SupportCasePriority?,
        category: SupportCaseCategory?,
        organizationId: UUID?,
    ): JdbcClient.StatementSpec {
        var spec = initial
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (status != null) spec = spec.param("status", status.name)
        if (priority != null) spec = spec.param("priority", priority.name)
        if (category != null) spec = spec.param("category", category.name)
        if (organizationId != null) spec = spec.param("organizationId", organizationId)
        return spec
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ) = SupportCase(
        id = rs.getObject("id", UUID::class.java),
        idempotencyKey = rs.getString("idempotency_key"),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        organizationName = rs.getString("organization_name"),
        requesterUserId = rs.getObject("requester_user_id", UUID::class.java),
        requesterName = rs.getString("requester_name"),
        requesterEmail = rs.getString("requester_email"),
        category = SupportCaseCategory.valueOf(rs.getString("category")),
        priority = SupportCasePriority.valueOf(rs.getString("priority")),
        subject = rs.getString("subject"),
        description = rs.getString("description"),
        status = SupportCaseStatus.valueOf(rs.getString("status")),
        assignedPlatformUserId = rs.getObject("assigned_platform_user_id", UUID::class.java),
        assignedPlatformUserName = rs.getString("assigned_platform_user_name"),
        resolution = rs.getString("resolution"),
        closedAt = rs.getTimestamp("closed_at")?.toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
