package com.rally26.identity.persistence

import com.rally26.identity.domain.AccountDeletionRequest
import com.rally26.identity.domain.AccountDeletionStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, user_id, status, requested_at, scheduled_for, canceled_at, completed_at, created_at, updated_at"

@Repository
class AccountDeletionRequestRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): AccountDeletionRequest? =
        jdbcClient
            .sql("select $COLUMNS from account_deletion_request where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findPendingForUser(userId: UUID): AccountDeletionRequest? =
        jdbcClient
            .sql("select $COLUMNS from account_deletion_request where user_id = :userId and status = 'PENDING'")
            .param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listPendingPastDue(now: Instant): List<AccountDeletionRequest> =
        jdbcClient
            .sql("select $COLUMNS from account_deletion_request where status = 'PENDING' and scheduled_for <= :now order by scheduled_for")
            .param("now", Timestamp.from(now))
            .query(::mapRow)
            .list()

    fun insert(
        userId: UUID,
        scheduledFor: Instant,
    ): AccountDeletionRequest {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into account_deletion_request (id, user_id, status, requested_at, scheduled_for, created_at, updated_at)
                values (:id, :userId, 'PENDING', :now, :scheduledFor, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("userId", userId)
            .param("scheduledFor", Timestamp.from(scheduledFor))
            .param("now", Timestamp.from(now))
            .update()
        return AccountDeletionRequest(
            id = id,
            userId = userId,
            status = AccountDeletionStatus.PENDING,
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
        status: AccountDeletionStatus,
        canceledAt: Instant? = null,
        completedAt: Instant? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update account_deletion_request
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
    ): AccountDeletionRequest =
        AccountDeletionRequest(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            status = AccountDeletionStatus.valueOf(rs.getString("status")),
            requestedAt = rs.getTimestamp("requested_at").toInstant(),
            scheduledFor = rs.getTimestamp("scheduled_for").toInstant(),
            canceledAt = rs.getTimestamp("canceled_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
