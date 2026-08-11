package com.rally26.messaging.persistence

import com.rally26.common.web.PageRequest
import com.rally26.messaging.domain.MessageModerationEvent
import com.rally26.messaging.domain.MessageModerationEventType
import com.rally26.messaging.domain.MessageSafetyReport
import com.rally26.messaging.domain.MessageSafetyReportReason
import com.rally26.messaging.domain.MessageSafetyReportStatus
import com.rally26.messaging.domain.MessageSafetyReportTarget
import com.rally26.messaging.domain.MessageScopeType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Phase 37 slice 37.4 — deterministic severity-based triage, replacing pure
 * reverse-chronological order in [MessageSafetyRepository.listForManagement]. Open
 * reports before reviewed/resolved ones, then the more urgent [MessageSafetyReportReason]
 * first, then oldest-first within the same priority tier so an urgent report doesn't sit
 * unseen behind newer ones of the same severity. No automated content signal (profanity/
 * tone detection) exists in this codebase — this is a real prioritization improvement
 * over FIFO, not a claim of automated content analysis.
 */
private const val TRIAGE_ORDER =
    """
    case msr.status when 'OPEN' then 1 when 'IN_REVIEW' then 2 else 3 end,
    case msr.reason
        when 'SAFETY_CONCERN' then 1
        when 'HARASSMENT' then 2
        when 'BULLYING' then 2
        when 'INAPPROPRIATE_CONTENT' then 3
        when 'SPAM' then 4
        else 5
    end,
    msr.created_at asc
    """

@Repository
class MessageSafetyRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findReportableMessageForUser(
        messageId: UUID,
        userId: UUID,
    ): MessageSafetyReportTarget? =
        jdbcClient
            .sql(
                """
                select me.organization_id, me.thread_id, me.id as message_id, mt.scope_type, mt.scope_id
                  from message_entry me
                  join message_thread mt on mt.id = me.thread_id and mt.organization_id = me.organization_id
                 where me.id = :messageId
                   and (
                        me.sender_user_id = :userId
                        or exists (
                            select 1 from message_recipient mr
                             where mr.message_id = me.id and mr.user_id = :userId and mr.in_app_visible = true
                        )
                        or exists (
                            select 1 from message_thread_member mtm
                             where mtm.thread_id = me.thread_id and mtm.user_id = :userId and mtm.left_at is null
                        )
                   )
                """.trimIndent(),
            ).param("messageId", messageId)
            .param("userId", userId)
            .query { rs, _ ->
                MessageSafetyReportTarget(
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    threadId = rs.getObject("thread_id", UUID::class.java),
                    messageId = rs.getObject("message_id", UUID::class.java),
                    scopeType = MessageScopeType.valueOf(rs.getString("scope_type")),
                    scopeId = rs.getObject("scope_id", UUID::class.java),
                )
            }.optional()
            .orElse(null)

    fun findActiveReport(
        messageId: UUID,
        reporterUserId: UUID,
    ): MessageSafetyReport? =
        reportQuery(
            "where msr.message_id = :messageId and msr.reporter_user_id = :reporterUserId and msr.status in ('OPEN', 'IN_REVIEW')",
        ).param("messageId", messageId)
            .param("reporterUserId", reporterUserId)
            .query(::mapReport)
            .optional()
            .orElse(null)

    fun insertReport(
        target: MessageSafetyReportTarget,
        reporterUserId: UUID,
        reason: MessageSafetyReportReason,
        details: String?,
        now: Instant,
    ): MessageSafetyReport {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_safety_report
                    (id, organization_id, thread_id, message_id, reporter_user_id, reason, details, status, created_at, updated_at)
                values
                    (:id, :organizationId, :threadId, :messageId, :reporterUserId, :reason, :details, 'OPEN', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", target.organizationId)
            .param("threadId", target.threadId)
            .param("messageId", target.messageId)
            .param("reporterUserId", reporterUserId)
            .param("reason", reason.name)
            .param("details", details)
            .param("now", Timestamp.from(now))
            .update()
        return findReportById(id, target.organizationId) ?: error("Inserted message safety report was not found.")
    }

    fun findReportById(
        reportId: UUID,
        organizationId: UUID,
    ): MessageSafetyReport? =
        reportQuery("where msr.id = :reportId and msr.organization_id = :organizationId")
            .param("reportId", reportId)
            .param("organizationId", organizationId)
            .query(::mapReport)
            .optional()
            .orElse(null)

    fun listMine(
        userId: UUID,
        page: PageRequest,
    ): List<MessageSafetyReport> =
        reportQuery("where msr.reporter_user_id = :userId order by msr.created_at desc offset :offset limit :limit")
            .param("userId", userId)
            .param("offset", page.offset)
            .param("limit", page.size)
            .query(::mapReport)
            .list()

    fun countMine(userId: UUID): Long =
        jdbcClient
            .sql("select count(*) from message_safety_report where reporter_user_id = :userId")
            .param("userId", userId)
            .query(Long::class.java)
            .single()

    fun listForManagement(
        organizationId: UUID,
        scopeType: MessageScopeType?,
        scopeId: UUID?,
        status: MessageSafetyReportStatus?,
        page: PageRequest,
    ): List<MessageSafetyReport> {
        val filters = mutableListOf("msr.organization_id = :organizationId")
        if (scopeType != null) filters += "mt.scope_type = :scopeType"
        if (scopeId != null) filters += "mt.scope_id = :scopeId"
        if (status != null) filters += "msr.status = :status"
        var statement =
            reportQuery("where ${filters.joinToString(" and ")} order by $TRIAGE_ORDER offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("offset", page.offset)
                .param("limit", page.size)
        if (scopeType != null) statement = statement.param("scopeType", scopeType.name)
        if (scopeId != null) statement = statement.param("scopeId", scopeId)
        if (status != null) statement = statement.param("status", status.name)
        return statement.query(::mapReport).list()
    }

    fun countForManagement(
        organizationId: UUID,
        scopeType: MessageScopeType?,
        scopeId: UUID?,
        status: MessageSafetyReportStatus?,
    ): Long {
        val filters = mutableListOf("msr.organization_id = :organizationId")
        if (scopeType != null) filters += "mt.scope_type = :scopeType"
        if (scopeId != null) filters += "mt.scope_id = :scopeId"
        if (status != null) filters += "msr.status = :status"
        var statement =
            jdbcClient
                .sql(
                    """
                    select count(*)
                      from message_safety_report msr
                      join message_thread mt on mt.id = msr.thread_id and mt.organization_id = msr.organization_id
                     where ${filters.joinToString(" and ")}
                    """.trimIndent(),
                ).param("organizationId", organizationId)
        if (scopeType != null) statement = statement.param("scopeType", scopeType.name)
        if (scopeId != null) statement = statement.param("scopeId", scopeId)
        if (status != null) statement = statement.param("status", status.name)
        return statement.query(Long::class.java).single()
    }

    fun updateReportStatus(
        reportId: UUID,
        organizationId: UUID,
        status: MessageSafetyReportStatus,
        assignedToUserId: UUID,
        resolutionNote: String?,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_safety_report
                   set status = :status,
                       assigned_to_user_id = :assignedToUserId,
                       resolution_note = case when :resolved then :resolutionNote else resolution_note end,
                       resolved_at = case when :resolved then :now else null end,
                       updated_at = :now
                 where id = :reportId and organization_id = :organizationId
                """.trimIndent(),
            ).param("status", status.name)
            .param("assignedToUserId", assignedToUserId)
            .param("resolutionNote", resolutionNote)
            .param("resolved", status in setOf(MessageSafetyReportStatus.RESOLVED, MessageSafetyReportStatus.DISMISSED))
            .param("now", Timestamp.from(now))
            .param("reportId", reportId)
            .param("organizationId", organizationId)
            .update()

    fun insertModerationEvent(
        organizationId: UUID,
        reportId: UUID?,
        threadId: UUID,
        messageId: UUID?,
        actorUserId: UUID,
        eventType: MessageModerationEventType,
        note: String?,
        now: Instant,
    ) {
        jdbcClient
            .sql(
                """
                insert into message_moderation_event
                    (organization_id, report_id, thread_id, message_id, actor_user_id, event_type, note, created_at)
                values
                    (:organizationId, :reportId, :threadId, :messageId, :actorUserId, :eventType, :note, :now)
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("reportId", reportId)
            .param("threadId", threadId)
            .param("messageId", messageId)
            .param("actorUserId", actorUserId)
            .param("eventType", eventType.name)
            .param("note", note)
            .param("now", Timestamp.from(now))
            .update()
    }

    fun listModerationEvents(
        organizationId: UUID,
        reportId: UUID,
    ): List<MessageModerationEvent> =
        jdbcClient
            .sql(
                """
                select mme.*, au.display_name as actor_display_name
                  from message_moderation_event mme
                  join app_user au on au.id = mme.actor_user_id
                 where mme.organization_id = :organizationId and mme.report_id = :reportId
                 order by mme.created_at asc, mme.id asc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("reportId", reportId)
            .query(::mapModerationEvent)
            .list()

    fun lockThread(
        organizationId: UUID,
        threadId: UUID,
        actorUserId: UUID,
        reason: String,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_thread
                   set safety_locked_at = :now,
                       safety_locked_by_user_id = :actorUserId,
                       safety_lock_reason = :reason,
                       updated_at = :now
                 where id = :threadId and organization_id = :organizationId and safety_locked_at is null
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("actorUserId", actorUserId)
            .param("reason", reason)
            .param("threadId", threadId)
            .param("organizationId", organizationId)
            .update()

    fun unlockThread(
        organizationId: UUID,
        threadId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_thread
                   set safety_locked_at = null,
                       safety_locked_by_user_id = null,
                       safety_lock_reason = null,
                       updated_at = :now
                 where id = :threadId and organization_id = :organizationId and safety_locked_at is not null
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("threadId", threadId)
            .param("organizationId", organizationId)
            .update()

    private fun reportQuery(tail: String): JdbcClient.StatementSpec =
        jdbcClient.sql(
            """
            select msr.*, mt.title as thread_title, mt.safety_locked_at as thread_safety_locked_at,
                   mt.safety_lock_reason as thread_safety_lock_reason, me.body as message_body,
                   sender.display_name as message_sender_display_name,
                   reporter.display_name as reporter_display_name
              from message_safety_report msr
              join message_thread mt on mt.id = msr.thread_id and mt.organization_id = msr.organization_id
              join message_entry me on me.id = msr.message_id and me.thread_id = msr.thread_id and me.organization_id = msr.organization_id
              join app_user sender on sender.id = me.sender_user_id
              join app_user reporter on reporter.id = msr.reporter_user_id
              $tail
            """.trimIndent(),
        )

    private fun mapReport(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): MessageSafetyReport =
        MessageSafetyReport(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            threadId = rs.getObject("thread_id", UUID::class.java),
            threadTitle = rs.getString("thread_title"),
            messageId = rs.getObject("message_id", UUID::class.java),
            messageBody = rs.getString("message_body"),
            messageSenderDisplayName = rs.getString("message_sender_display_name"),
            reporterUserId = rs.getObject("reporter_user_id", UUID::class.java),
            reporterDisplayName = rs.getString("reporter_display_name"),
            reason = MessageSafetyReportReason.valueOf(rs.getString("reason")),
            details = rs.getString("details"),
            status = MessageSafetyReportStatus.valueOf(rs.getString("status")),
            assignedToUserId = rs.getObject("assigned_to_user_id", UUID::class.java),
            resolutionNote = rs.getString("resolution_note"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
            threadSafetyLockedAt = rs.getTimestamp("thread_safety_locked_at")?.toInstant(),
            threadSafetyLockReason = rs.getString("thread_safety_lock_reason"),
        )

    private fun mapModerationEvent(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): MessageModerationEvent =
        MessageModerationEvent(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            reportId = rs.getObject("report_id", UUID::class.java),
            threadId = rs.getObject("thread_id", UUID::class.java),
            messageId = rs.getObject("message_id", UUID::class.java),
            actorUserId = rs.getObject("actor_user_id", UUID::class.java),
            actorDisplayName = rs.getString("actor_display_name"),
            eventType = MessageModerationEventType.valueOf(rs.getString("event_type")),
            note = rs.getString("note"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
