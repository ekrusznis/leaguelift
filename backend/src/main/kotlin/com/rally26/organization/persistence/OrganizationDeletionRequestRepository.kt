package com.rally26.organization.persistence

import com.rally26.organization.domain.OrganizationDeletionRequest
import com.rally26.organization.domain.OrganizationDeletionStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, requested_by_user_id, status, requested_at, scheduled_for, canceled_at, completed_at, created_at, updated_at"

@Repository
class OrganizationDeletionRequestRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): OrganizationDeletionRequest? =
        jdbcClient
            .sql("select $COLUMNS from organization_deletion_request where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findPendingForOrganization(organizationId: UUID): OrganizationDeletionRequest? =
        jdbcClient
            .sql("select $COLUMNS from organization_deletion_request where organization_id = :organizationId and status = 'PENDING'")
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listPendingPastDue(now: Instant): List<OrganizationDeletionRequest> =
        jdbcClient
            .sql(
                "select $COLUMNS from organization_deletion_request where status = 'PENDING' and scheduled_for <= :now order by scheduled_for",
            ).param("now", Timestamp.from(now))
            .query(::mapRow)
            .list()

    fun insert(
        organizationId: UUID,
        requestedByUserId: UUID,
        scheduledFor: Instant,
    ): OrganizationDeletionRequest {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into organization_deletion_request
                    (id, organization_id, requested_by_user_id, status, requested_at, scheduled_for, created_at, updated_at)
                values (:id, :organizationId, :requestedByUserId, 'PENDING', :now, :scheduledFor, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("requestedByUserId", requestedByUserId)
            .param("scheduledFor", Timestamp.from(scheduledFor))
            .param("now", Timestamp.from(now))
            .update()
        return OrganizationDeletionRequest(
            id = id,
            organizationId = organizationId,
            requestedByUserId = requestedByUserId,
            status = OrganizationDeletionStatus.PENDING,
            requestedAt = now,
            scheduledFor = scheduledFor,
            canceledAt = null,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun markStatus(
        id: UUID,
        status: OrganizationDeletionStatus,
        canceledAt: Instant? = null,
        completedAt: Instant? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update organization_deletion_request
                set status = :status,
                    canceled_at = coalesce(:canceledAt, canceled_at),
                    completed_at = coalesce(:completedAt, completed_at),
                    updated_at = :now
                where id = :id
                """.trimIndent(),
            ).param("status", status.name)
            .param("canceledAt", canceledAt?.let { Timestamp.from(it) })
            .param("completedAt", completedAt?.let { Timestamp.from(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OrganizationDeletionRequest =
        OrganizationDeletionRequest(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            requestedByUserId = rs.getObject("requested_by_user_id", UUID::class.java),
            status = OrganizationDeletionStatus.valueOf(rs.getString("status")),
            requestedAt = rs.getTimestamp("requested_at").toInstant(),
            scheduledFor = rs.getTimestamp("scheduled_for").toInstant(),
            canceledAt = rs.getTimestamp("canceled_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
