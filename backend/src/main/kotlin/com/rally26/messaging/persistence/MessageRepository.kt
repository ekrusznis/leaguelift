package com.rally26.messaging.persistence

import com.rally26.common.web.PageRequest
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.messaging.domain.BroadcastMessage
import com.rally26.messaging.domain.ConversationContact
import com.rally26.messaging.domain.GuardianObserverLink
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageAudience
import com.rally26.messaging.domain.MessageRecipient
import com.rally26.messaging.domain.MessageRecipientCandidate
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageScopeType
import com.rally26.messaging.domain.MessageThread
import com.rally26.messaging.domain.MessageThreadMember
import com.rally26.messaging.domain.MessageThreadMemberCandidate
import com.rally26.messaging.domain.MessageThreadStatus
import com.rally26.messaging.domain.MessageThreadType
import com.rally26.messaging.domain.MyBroadcastMessage
import com.rally26.messaging.domain.MyMessageThread
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class MessageRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findThreadById(
        id: UUID,
        organizationId: UUID,
    ): MessageThread? =
        threadQuery("where mt.id = :id and mt.organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapThread)
            .optional()
            .orElse(null)

    fun findThreadByIdForUser(
        id: UUID,
        userId: UUID,
    ): MessageThread? =
        threadQuery(
            """
            where mt.id = :id
              and (
                exists (
                    select 1 from message_thread_member mtm
                     where mtm.thread_id = mt.id and mtm.user_id = :userId and mtm.left_at is null
                )
                or exists (
                    select 1
                      from message_entry me join message_recipient mr on mr.message_id = me.id
                     where me.thread_id = mt.id and mr.user_id = :userId and mr.in_app_visible = true
                )
              )
            """.trimIndent(),
        ).param("id", id)
            .param("userId", userId)
            .query(::mapThread)
            .optional()
            .orElse(null)

    fun findThreadByIdempotencyKey(
        organizationId: UUID,
        key: String,
    ): MessageThread? =
        threadQuery("where mt.organization_id = :organizationId and mt.idempotency_key = :key")
            .param("organizationId", organizationId)
            .param("key", key)
            .query(::mapThread)
            .optional()
            .orElse(null)

    fun insertThread(
        organizationId: UUID,
        scopeType: MessageScopeType,
        scopeId: UUID,
        idempotencyKey: String,
        title: String,
        audience: MessageAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        createdByUserId: UUID,
    ): MessageThread {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_thread
                    (id, organization_id, scope_type, scope_id, thread_type, idempotency_key, title, audience,
                     email_enabled, sms_enabled, status, created_by_user_id)
                values
                    (:id, :organizationId, :scopeType, :scopeId, 'BROADCAST', :idempotencyKey, :title, :audience,
                     :emailEnabled, :smsEnabled, 'OPEN', :createdByUserId)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("scopeType", scopeType.name)
            .param("scopeId", scopeId)
            .param("idempotencyKey", idempotencyKey)
            .param("title", title)
            .param("audience", audience.name)
            .param("emailEnabled", emailEnabled)
            .param("smsEnabled", smsEnabled)
            .param("createdByUserId", createdByUserId)
            .update()
        return findThreadById(id, organizationId) ?: error("Inserted message thread was not found.")
    }

    fun insertConversationThread(
        organizationId: UUID,
        teamId: UUID,
        idempotencyKey: String,
        title: String,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        createdByUserId: UUID,
    ): MessageThread {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_thread
                    (id, organization_id, scope_type, scope_id, thread_type, idempotency_key, title, audience,
                     email_enabled, sms_enabled, status, created_by_user_id)
                values
                    (:id, :organizationId, 'TEAM', :teamId, 'CONVERSATION', :idempotencyKey, :title, 'SELECTED',
                     :emailEnabled, :smsEnabled, 'OPEN', :createdByUserId)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("idempotencyKey", idempotencyKey)
            .param("title", title)
            .param("emailEnabled", emailEnabled)
            .param("smsEnabled", smsEnabled)
            .param("createdByUserId", createdByUserId)
            .update()
        return findThreadById(id, organizationId) ?: error("Inserted conversation thread was not found.")
    }

    fun archiveThread(
        id: UUID,
        organizationId: UUID,
        archivedByUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_thread
                   set status = 'ARCHIVED', archived_by_user_id = :archivedByUserId, archived_at = :now, updated_at = :now
                 where id = :id and organization_id = :organizationId and status = 'OPEN'
                """.trimIndent(),
            ).param("archivedByUserId", archivedByUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun listForManagement(
        organizationId: UUID,
        scopeType: MessageScopeType?,
        scopeId: UUID?,
        status: MessageThreadStatus?,
        page: PageRequest,
    ): List<MessageThread> {
        val filters = mutableListOf("mt.organization_id = :organizationId")
        if (scopeType != null) filters += "mt.scope_type = :scopeType"
        if (scopeId != null) filters += "mt.scope_id = :scopeId"
        if (status != null) filters += "mt.status = :status"
        var statement =
            threadQuery("where ${filters.joinToString(" and ")} order by mt.updated_at desc offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("offset", page.offset)
                .param("limit", page.size)
        if (scopeType != null) statement = statement.param("scopeType", scopeType.name)
        if (scopeId != null) statement = statement.param("scopeId", scopeId)
        if (status != null) statement = statement.param("status", status.name)
        return statement.query(::mapThread).list()
    }

    fun countForManagement(
        organizationId: UUID,
        scopeType: MessageScopeType?,
        scopeId: UUID?,
        status: MessageThreadStatus?,
    ): Long {
        val filters = mutableListOf("organization_id = :organizationId")
        if (scopeType != null) filters += "scope_type = :scopeType"
        if (scopeId != null) filters += "scope_id = :scopeId"
        if (status != null) filters += "status = :status"
        var statement =
            jdbcClient
                .sql("select count(*) from message_thread where ${filters.joinToString(" and ")}")
                .param("organizationId", organizationId)
        if (scopeType != null) statement = statement.param("scopeType", scopeType.name)
        if (scopeId != null) statement = statement.param("scopeId", scopeId)
        if (status != null) statement = statement.param("status", status.name)
        return statement.query(Long::class.java).single()
    }

    fun findMessageByIdempotencyKey(
        threadId: UUID,
        key: String,
    ): BroadcastMessage? =
        messageQuery("where me.thread_id = :threadId and me.idempotency_key = :key")
            .param("threadId", threadId)
            .param("key", key)
            .query(::mapMessage)
            .optional()
            .orElse(null)

    fun insertMessage(
        organizationId: UUID,
        threadId: UUID,
        senderUserId: UUID,
        idempotencyKey: String,
        body: String,
        sentAt: Instant,
    ): BroadcastMessage {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_entry
                    (id, organization_id, thread_id, sender_user_id, idempotency_key, body, sent_at)
                values (:id, :organizationId, :threadId, :senderUserId, :idempotencyKey, :body, :sentAt)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("threadId", threadId)
            .param("senderUserId", senderUserId)
            .param("idempotencyKey", idempotencyKey)
            .param("body", body)
            .param("sentAt", Timestamp.from(sentAt))
            .update()
        touchThread(threadId, organizationId, sentAt)
        return findMessageById(id, organizationId) ?: error("Inserted broadcast message was not found.")
    }

    fun findMessageById(
        id: UUID,
        organizationId: UUID,
    ): BroadcastMessage? =
        messageQuery("where me.id = :id and me.organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapMessage)
            .optional()
            .orElse(null)

    fun insertRecipient(
        messageId: UUID,
        organizationId: UUID,
        recipientKey: String,
        candidate: MessageRecipientCandidate,
        inAppVisible: Boolean,
        emailStatus: DeliveryStatus,
        smsStatus: DeliveryStatus,
    ): Int =
        jdbcClient
            .sql(
                """
                insert into message_recipient
                    (organization_id, message_id, recipient_key, recipient_type, user_id, household_id, display_name,
                     email, phone, access_reason, in_app_visible, email_status, sms_status)
                values
                    (:organizationId, :messageId, :recipientKey, :recipientType, :userId, :householdId, :displayName,
                     :email, :phone, :accessReason, :inAppVisible, :emailStatus, :smsStatus)
                on conflict (message_id, recipient_key) do nothing
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("messageId", messageId)
            .param("recipientKey", recipientKey)
            .param("recipientType", candidate.recipientType.name)
            .param("userId", candidate.userId)
            .param("householdId", candidate.householdId)
            .param("displayName", candidate.displayName)
            .param("email", candidate.email)
            .param("phone", candidate.phone)
            .param("accessReason", candidate.accessReason.name)
            .param("inAppVisible", inAppVisible)
            .param("emailStatus", emailStatus.name)
            .param("smsStatus", smsStatus.name)
            .update()

    fun listDeliveries(messageId: UUID): List<MessageRecipient> =
        jdbcClient
            .sql(
                """
                select id, message_id, recipient_key, recipient_type, user_id, household_id, display_name, email, phone,
                       access_reason, in_app_visible, email_status, sms_status, read_at, last_error
                  from message_recipient
                 where message_id = :messageId
                   and (email_status in ('PENDING', 'FAILED') or sms_status in ('PENDING', 'FAILED'))
                 order by created_at asc
                """.trimIndent(),
            ).param("messageId", messageId)
            .query(::mapRecipient)
            .list()

    fun markEmailSent(
        recipientId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update message_recipient set email_status = 'SENT', email_sent_at = :now, last_error = null, updated_at = :now where id = :id",
            ).param("now", Timestamp.from(now))
            .param("id", recipientId)
            .update()

    fun markSmsSent(
        recipientId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update message_recipient set sms_status = 'SENT', sms_sent_at = :now, last_error = null, updated_at = :now where id = :id",
            ).param("now", Timestamp.from(now))
            .param("id", recipientId)
            .update()

    fun markEmailFailed(
        recipientId: UUID,
        error: String,
    ): Int =
        jdbcClient
            .sql("update message_recipient set email_status = 'FAILED', last_error = :error, updated_at = now() where id = :id")
            .param("error", error.take(1000))
            .param("id", recipientId)
            .update()

    fun markSmsFailed(
        recipientId: UUID,
        error: String,
    ): Int =
        jdbcClient
            .sql("update message_recipient set sms_status = 'FAILED', last_error = :error, updated_at = now() where id = :id")
            .param("error", error.take(1000))
            .param("id", recipientId)
            .update()

    fun listMessagesForManagement(
        organizationId: UUID,
        threadId: UUID,
        page: PageRequest,
    ): List<BroadcastMessage> =
        messageQuery(
            "where me.organization_id = :organizationId and me.thread_id = :threadId order by me.sent_at asc offset :offset limit :limit",
        ).param("organizationId", organizationId)
            .param("threadId", threadId)
            .param("offset", page.offset)
            .param("limit", page.size)
            .query(::mapMessage)
            .list()

    fun countMessagesForManagement(
        organizationId: UUID,
        threadId: UUID,
    ): Long =
        jdbcClient
            .sql("select count(*) from message_entry where organization_id = :organizationId and thread_id = :threadId")
            .param("organizationId", organizationId)
            .param("threadId", threadId)
            .query(Long::class.java)
            .single()

    fun listConversationContacts(
        organizationId: UUID,
        teamId: UUID,
    ): List<ConversationContact> =
        jdbcClient
            .sql(
                """
                select distinct gr.user_id, 'GUARDIAN' as recipient_type, h.id as household_id,
                       null::uuid as participant_id,
                       trim(ha.first_name || ' ' || ha.last_name) as display_name
                  from participant_team pt
                  join participant p on p.id = pt.participant_id and p.organization_id = pt.organization_id and p.status = 'ACTIVE'
                  join household h on h.id = p.household_id and h.organization_id = p.organization_id and h.status = 'ACTIVE'
                  join guardian_relationship gr on gr.organization_id = h.organization_id and gr.household_id = h.id and gr.status = 'ACTIVE'
                  join app_user au on au.id = gr.user_id and au.status = 'ACTIVE'
                  join household_adult ha on ha.id = gr.household_adult_id and ha.organization_id = gr.organization_id and ha.status = 'ACTIVE'
                 where pt.organization_id = :organizationId and pt.team_id = :teamId and pt.status = 'ACTIVE'
                union
                select distinct ra.user_id, 'ATHLETE' as recipient_type, p.household_id,
                       p.id as participant_id, au.display_name
                  from participant_team pt
                  join participant p on p.id = pt.participant_id and p.organization_id = pt.organization_id and p.status = 'ACTIVE'
                  join role_assignment ra on ra.organization_id = p.organization_id and ra.context_type = 'PARTICIPANT'
                                         and ra.resource_id = p.id and ra.status = 'ACTIVE'
                  join app_user au on au.id = ra.user_id and au.status = 'ACTIVE'
                 where pt.organization_id = :organizationId and pt.team_id = :teamId and pt.status = 'ACTIVE'
                 order by display_name, recipient_type
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("teamId", teamId)
            .query { rs, _ ->
                ConversationContact(
                    userId = rs.getObject("user_id", UUID::class.java),
                    recipientType = MessageRecipientType.valueOf(rs.getString("recipient_type")),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    participantId = rs.getObject("participant_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                )
            }.list()

    fun listGuardianObserverLinks(
        organizationId: UUID,
        teamId: UUID,
    ): List<GuardianObserverLink> =
        jdbcClient
            .sql(
                """
                select distinct athlete_ra.user_id as athlete_user_id, gr.user_id as guardian_user_id,
                       gr.household_id, trim(ha.first_name || ' ' || ha.last_name) as display_name
                  from participant_team pt
                  join participant p on p.id = pt.participant_id and p.organization_id = pt.organization_id and p.status = 'ACTIVE'
                  join role_assignment athlete_ra on athlete_ra.organization_id = p.organization_id
                         and athlete_ra.context_type = 'PARTICIPANT' and athlete_ra.resource_id = p.id and athlete_ra.status = 'ACTIVE'
                  join app_user athlete_user on athlete_user.id = athlete_ra.user_id and athlete_user.status = 'ACTIVE'
                  join guardian_relationship gr on gr.organization_id = p.organization_id and gr.household_id = p.household_id and gr.status = 'ACTIVE'
                  join app_user guardian_user on guardian_user.id = gr.user_id and guardian_user.status = 'ACTIVE'
                  join household_adult ha on ha.id = gr.household_adult_id and ha.organization_id = gr.organization_id and ha.status = 'ACTIVE'
                 where pt.organization_id = :organizationId and pt.team_id = :teamId and pt.status = 'ACTIVE'
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("teamId", teamId)
            .query { rs, _ ->
                GuardianObserverLink(
                    athleteUserId = rs.getObject("athlete_user_id", UUID::class.java),
                    member =
                        MessageThreadMemberCandidate(
                            userId = rs.getObject("guardian_user_id", UUID::class.java),
                            memberType = MessageRecipientType.GUARDIAN,
                            householdId = rs.getObject("household_id", UUID::class.java),
                            participantId = null,
                            displayName = rs.getString("display_name"),
                            accessReason = MessageAccessReason.GUARDIAN_VISIBILITY,
                            canReply = false,
                        ),
                )
            }.list()

    fun insertThreadMember(
        organizationId: UUID,
        threadId: UUID,
        candidate: MessageThreadMemberCandidate,
        joinedAt: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                insert into message_thread_member
                    (organization_id, thread_id, user_id, member_type, household_id, participant_id,
                     display_name, access_reason, can_reply, joined_at)
                values
                    (:organizationId, :threadId, :userId, :memberType, :householdId, :participantId,
                     :displayName, :accessReason, :canReply, :joinedAt)
                on conflict do nothing
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("threadId", threadId)
            .param("userId", candidate.userId)
            .param("memberType", candidate.memberType.name)
            .param("householdId", candidate.householdId)
            .param("participantId", candidate.participantId)
            .param("displayName", candidate.displayName)
            .param("accessReason", candidate.accessReason.name)
            .param("canReply", candidate.canReply)
            .param("joinedAt", Timestamp.from(joinedAt))
            .update()

    fun listActiveThreadMembers(threadId: UUID): List<MessageThreadMember> =
        jdbcClient
            .sql(
                """
                select id, organization_id, thread_id, user_id, member_type, household_id, participant_id,
                       display_name, access_reason, can_reply, joined_at, left_at
                  from message_thread_member
                 where thread_id = :threadId and left_at is null
                 order by joined_at, display_name
                """.trimIndent(),
            ).param("threadId", threadId)
            .query(::mapThreadMember)
            .list()

    fun findActiveThreadMember(
        threadId: UUID,
        userId: UUID,
    ): MessageThreadMember? =
        jdbcClient
            .sql(
                """
                select id, organization_id, thread_id, user_id, member_type, household_id, participant_id,
                       display_name, access_reason, can_reply, joined_at, left_at
                  from message_thread_member
                 where thread_id = :threadId and user_id = :userId and left_at is null
                """.trimIndent(),
            ).param("threadId", threadId)
            .param("userId", userId)
            .query(::mapThreadMember)
            .optional()
            .orElse(null)

    fun listConversationRecipients(
        threadId: UUID,
        senderUserId: UUID,
    ): List<MessageRecipientCandidate> =
        jdbcClient
            .sql(
                """
                select distinct mtm.member_type, mtm.user_id, mtm.household_id, mtm.display_name, mtm.access_reason,
                       case
                         when mtm.access_reason = 'GUARDIAN_VISIBILITY' then null
                         when mtm.member_type = 'GUARDIAN' then
                           case when h.email_reminders_opt_out then null
                                else coalesce(nullif(trim(ha.email), ''), nullif(trim(h.contact_email), ''), au.email) end
                         else au.email
                       end as email,
                       case
                         when mtm.access_reason = 'TARGETED' and mtm.member_type = 'GUARDIAN' and h.sms_reminders_opt_in
                           then coalesce(nullif(trim(ha.phone), ''), nullif(trim(h.contact_phone), ''))
                         else null
                       end as phone
                  from message_thread_member mtm
                  join app_user au on au.id = mtm.user_id and au.status = 'ACTIVE'
                  left join household h on h.id = mtm.household_id and h.organization_id = mtm.organization_id and h.status = 'ACTIVE'
                  left join guardian_relationship gr on mtm.member_type = 'GUARDIAN' and gr.organization_id = mtm.organization_id
                         and gr.household_id = mtm.household_id and gr.user_id = mtm.user_id and gr.status = 'ACTIVE'
                  left join household_adult ha on ha.id = gr.household_adult_id and ha.organization_id = gr.organization_id and ha.status = 'ACTIVE'
                 where mtm.thread_id = :threadId and mtm.left_at is null and mtm.user_id <> :senderUserId
                """.trimIndent(),
            ).param("threadId", threadId)
            .param("senderUserId", senderUserId)
            .query { rs, _ ->
                MessageRecipientCandidate(
                    recipientType = MessageRecipientType.valueOf(rs.getString("member_type")),
                    userId = rs.getObject("user_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    phone = rs.getString("phone"),
                    accessReason = MessageAccessReason.valueOf(rs.getString("access_reason")),
                )
            }.list()

    fun listMine(
        userId: UUID,
        page: PageRequest,
    ): List<MyMessageThread> =
        jdbcClient
            .sql(
                """
                with mine_threads as (
                    select distinct me.thread_id
                      from message_entry me join message_recipient mr on mr.message_id = me.id
                     where mr.user_id = :userId and mr.in_app_visible = true
                    union
                    select mtm.thread_id
                      from message_thread_member mtm
                     where mtm.user_id = :userId and mtm.left_at is null
                ), mine as (
                    select me.thread_id,
                           count(*) filter (where mr.user_id = :userId and mr.in_app_visible = true and mr.read_at is null) as unread_count,
                           max(me.sent_at) as last_message_at,
                           (array_agg(me.body order by me.sent_at desc))[1] as last_message_preview,
                           bool_or(mr.user_id = :userId and mr.access_reason = 'TARGETED') as has_targeted_copy
                      from message_entry me
                      join mine_threads x on x.thread_id = me.thread_id
                      left join message_recipient mr on mr.message_id = me.id and mr.user_id = :userId and mr.in_app_visible = true
                     group by me.thread_id
                ), member_access as (
                    select thread_id, can_reply, access_reason
                      from message_thread_member
                     where user_id = :userId and left_at is null
                )
                select mt.*,
                       case mt.scope_type when 'ORGANIZATION' then o.name when 'TEAM' then t.name end as scope_name,
                       case when mt.thread_type = 'CONVERSATION' then coalesce(ms.member_count, 0)
                            else coalesce(stats.recipient_count, 0) end as recipient_count,
                       coalesce(stats.message_count, 0) as message_count,
                       mine.unread_count, mine.last_message_at, mine.last_message_preview,
                       coalesce(member_access.can_reply, false) as can_reply,
                       coalesce(member_access.access_reason,
                                case when mine.has_targeted_copy then 'TARGETED' else 'GUARDIAN_VISIBILITY' end) as mine_access_reason
                  from message_thread mt
                  join organization o on o.id = mt.organization_id
                  left join team t on mt.scope_type = 'TEAM' and t.id = mt.scope_id and t.organization_id = mt.organization_id
                  join mine on mine.thread_id = mt.id
                  left join member_access on member_access.thread_id = mt.id
                  left join (
                    select me.thread_id, count(distinct me.id) as message_count, count(distinct mr.recipient_key) as recipient_count
                      from message_entry me left join message_recipient mr on mr.message_id = me.id
                     group by me.thread_id
                  ) stats on stats.thread_id = mt.id
                  left join (
                    select thread_id, count(*) as member_count
                      from message_thread_member where left_at is null group by thread_id
                  ) ms on ms.thread_id = mt.id
                 order by mine.last_message_at desc
                 offset :offset limit :limit
                """.trimIndent(),
            ).param("userId", userId)
            .param("offset", page.offset)
            .param("limit", page.size)
            .query { rs, _ ->
                MyMessageThread(
                    thread = mapThread(rs, 0),
                    unreadCount = rs.getLong("unread_count"),
                    lastMessageAt = rs.getTimestamp("last_message_at").toInstant(),
                    lastMessagePreview = rs.getString("last_message_preview"),
                    canReply = rs.getBoolean("can_reply"),
                    accessReason = MessageAccessReason.valueOf(rs.getString("mine_access_reason")),
                )
            }.list()

    fun countMine(userId: UUID): Long =
        jdbcClient
            .sql(
                """
                select count(*) from (
                    select distinct me.thread_id
                      from message_entry me join message_recipient mr on mr.message_id = me.id
                     where mr.user_id = :userId and mr.in_app_visible = true
                    union
                    select mtm.thread_id from message_thread_member mtm
                     where mtm.user_id = :userId and mtm.left_at is null
                ) mine
                """.trimIndent(),
            ).param("userId", userId)
            .query(Long::class.java)
            .single()

    fun listMyMessages(
        userId: UUID,
        threadId: UUID,
        page: PageRequest,
    ): List<MyBroadcastMessage> =
        jdbcClient
            .sql(
                """
                select me.*, au.display_name as sender_display_name,
                       coalesce(ds.recipient_count, 0) as recipient_count,
                       coalesce(ds.email_sent_count, 0) as email_sent_count,
                       coalesce(ds.email_failed_count, 0) as email_failed_count,
                       coalesce(ds.sms_sent_count, 0) as sms_sent_count,
                       coalesce(ds.sms_failed_count, 0) as sms_failed_count,
                       case when me.sender_user_id = :userId then me.sent_at else mr.read_at end as read_at,
                       coalesce(mr.access_reason, mtm.access_reason) as access_reason
                  from message_entry me
                  join app_user au on au.id = me.sender_user_id
                  left join message_thread_member mtm on mtm.thread_id = me.thread_id and mtm.user_id = :userId and mtm.left_at is null
                  left join message_recipient mr on mr.message_id = me.id and mr.user_id = :userId and mr.in_app_visible = true
                  left join (
                    select message_id, count(*) as recipient_count,
                           count(*) filter (where email_status = 'SENT') as email_sent_count,
                           count(*) filter (where email_status = 'FAILED') as email_failed_count,
                           count(*) filter (where sms_status = 'SENT') as sms_sent_count,
                           count(*) filter (where sms_status = 'FAILED') as sms_failed_count
                      from message_recipient group by message_id
                  ) ds on ds.message_id = me.id
                 where me.thread_id = :threadId
                   and (mtm.id is not null or mr.id is not null)
                 order by me.sent_at asc
                 offset :offset limit :limit
                """.trimIndent(),
            ).param("userId", userId)
            .param("threadId", threadId)
            .param("offset", page.offset)
            .param("limit", page.size)
            .query { rs, _ ->
                MyBroadcastMessage(
                    mapMessage(rs, 0),
                    rs.getTimestamp("read_at")?.toInstant(),
                    MessageAccessReason.valueOf(rs.getString("access_reason")),
                )
            }.list()

    fun countMyMessages(
        userId: UUID,
        threadId: UUID,
    ): Long =
        jdbcClient
            .sql(
                """
                select count(*)
                  from message_entry me
                  left join message_thread_member mtm on mtm.thread_id = me.thread_id and mtm.user_id = :userId and mtm.left_at is null
                  left join message_recipient mr on mr.message_id = me.id and mr.user_id = :userId and mr.in_app_visible = true
                 where me.thread_id = :threadId and (mtm.id is not null or mr.id is not null)
                """.trimIndent(),
            ).param("threadId", threadId)
            .param("userId", userId)
            .query(Long::class.java)
            .single()

    fun markRead(
        messageId: UUID,
        userId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_recipient set read_at = coalesce(read_at, :now), updated_at = :now
                 where message_id = :messageId and user_id = :userId and in_app_visible = true
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("messageId", messageId)
            .param("userId", userId)
            .update()

    fun listGuardianVisibilityCandidatesForActivatedAthletes(
        organizationId: UUID,
        teamId: UUID?,
    ): List<MessageRecipientCandidate> {
        val teamClause =
            if (teamId ==
                null
            ) {
                ""
            } else {
                "join participant_team pt on pt.participant_id = p.id and pt.organization_id = p.organization_id and pt.status = 'ACTIVE' and pt.team_id = :teamId"
            }
        var statement =
            jdbcClient
                .sql(
                    """
                    select distinct gr.user_id, gr.household_id,
                           trim(ha.first_name || ' ' || ha.last_name) as display_name
                      from role_assignment ra
                      join app_user athlete_user on athlete_user.id = ra.user_id and athlete_user.status = 'ACTIVE'
                      join participant p on p.id = ra.resource_id and p.organization_id = ra.organization_id and p.status = 'ACTIVE'
                      $teamClause
                      join guardian_relationship gr on gr.organization_id = p.organization_id and gr.household_id = p.household_id and gr.status = 'ACTIVE'
                      join app_user guardian_user on guardian_user.id = gr.user_id and guardian_user.status = 'ACTIVE'
                      join household_adult ha on ha.id = gr.household_adult_id and ha.organization_id = gr.organization_id and ha.status = 'ACTIVE'
                     where ra.organization_id = :organizationId
                       and ra.context_type = 'PARTICIPANT' and ra.status = 'ACTIVE'
                    """.trimIndent(),
                ).param("organizationId", organizationId)
        if (teamId != null) statement = statement.param("teamId", teamId)
        return statement
            .query { rs, _ ->
                MessageRecipientCandidate(
                    recipientType = MessageRecipientType.GUARDIAN,
                    userId = rs.getObject("user_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = null,
                    phone = null,
                    accessReason = MessageAccessReason.GUARDIAN_VISIBILITY,
                )
            }.list()
    }

    private fun touchThread(
        threadId: UUID,
        organizationId: UUID,
        now: Instant,
    ) {
        jdbcClient
            .sql("update message_thread set updated_at = :now where id = :threadId and organization_id = :organizationId")
            .param("now", Timestamp.from(now))
            .param("threadId", threadId)
            .param("organizationId", organizationId)
            .update()
    }

    private fun threadQuery(tail: String): JdbcClient.StatementSpec =
        jdbcClient.sql(
            """
            select mt.*,
                   case mt.scope_type when 'ORGANIZATION' then o.name when 'TEAM' then t.name end as scope_name,
                   coalesce(stats.message_count, 0) as message_count,
                   case when mt.thread_type = 'CONVERSATION' then coalesce(ms.member_count, 0)
                        else coalesce(stats.recipient_count, 0) end as recipient_count
              from message_thread mt
              join organization o on o.id = mt.organization_id
              left join team t on mt.scope_type = 'TEAM' and t.id = mt.scope_id and t.organization_id = mt.organization_id
              left join (
                select me.thread_id, count(distinct me.id) as message_count, count(distinct mr.recipient_key) as recipient_count
                  from message_entry me left join message_recipient mr on mr.message_id = me.id
                 group by me.thread_id
              ) stats on stats.thread_id = mt.id
              left join (
                select thread_id, count(*) as member_count from message_thread_member where left_at is null group by thread_id
              ) ms on ms.thread_id = mt.id
              $tail
            """.trimIndent(),
        )

    private fun messageQuery(tail: String): JdbcClient.StatementSpec =
        jdbcClient.sql(
            """
            select me.*, au.display_name as sender_display_name,
                   coalesce(ds.recipient_count, 0) as recipient_count,
                   coalesce(ds.email_sent_count, 0) as email_sent_count,
                   coalesce(ds.email_failed_count, 0) as email_failed_count,
                   coalesce(ds.sms_sent_count, 0) as sms_sent_count,
                   coalesce(ds.sms_failed_count, 0) as sms_failed_count
              from message_entry me
              join app_user au on au.id = me.sender_user_id
              left join (
                select message_id, count(*) as recipient_count,
                       count(*) filter (where email_status = 'SENT') as email_sent_count,
                       count(*) filter (where email_status = 'FAILED') as email_failed_count,
                       count(*) filter (where sms_status = 'SENT') as sms_sent_count,
                       count(*) filter (where sms_status = 'FAILED') as sms_failed_count
                  from message_recipient group by message_id
              ) ds on ds.message_id = me.id
              $tail
            """.trimIndent(),
        )

    private fun mapThread(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): MessageThread =
        MessageThread(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            scopeType = MessageScopeType.valueOf(rs.getString("scope_type")),
            scopeId = rs.getObject("scope_id", UUID::class.java),
            scopeName = rs.getString("scope_name"),
            threadType = MessageThreadType.valueOf(rs.getString("thread_type")),
            title = rs.getString("title"),
            audience = MessageAudience.valueOf(rs.getString("audience")),
            emailEnabled = rs.getBoolean("email_enabled"),
            smsEnabled = rs.getBoolean("sms_enabled"),
            status = MessageThreadStatus.valueOf(rs.getString("status")),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            archivedAt = rs.getTimestamp("archived_at")?.toInstant(),
            messageCount = rs.getLong("message_count"),
            recipientCount = rs.getLong("recipient_count"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            safetyLockedAt = rs.getTimestamp("safety_locked_at")?.toInstant(),
            safetyLockReason = rs.getString("safety_lock_reason"),
        )

    private fun mapMessage(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): BroadcastMessage =
        BroadcastMessage(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            threadId = rs.getObject("thread_id", UUID::class.java),
            senderUserId = rs.getObject("sender_user_id", UUID::class.java),
            senderDisplayName = rs.getString("sender_display_name"),
            body = rs.getString("body"),
            sentAt = rs.getTimestamp("sent_at").toInstant(),
            recipientCount = rs.getLong("recipient_count"),
            emailSentCount = rs.getLong("email_sent_count"),
            emailFailedCount = rs.getLong("email_failed_count"),
            smsSentCount = rs.getLong("sms_sent_count"),
            smsFailedCount = rs.getLong("sms_failed_count"),
        )

    private fun mapThreadMember(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): MessageThreadMember =
        MessageThreadMember(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            threadId = rs.getObject("thread_id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            memberType = MessageRecipientType.valueOf(rs.getString("member_type")),
            householdId = rs.getObject("household_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            displayName = rs.getString("display_name"),
            accessReason = MessageAccessReason.valueOf(rs.getString("access_reason")),
            canReply = rs.getBoolean("can_reply"),
            joinedAt = rs.getTimestamp("joined_at").toInstant(),
            leftAt = rs.getTimestamp("left_at")?.toInstant(),
        )

    private fun mapRecipient(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): MessageRecipient =
        MessageRecipient(
            id = rs.getObject("id", UUID::class.java),
            messageId = rs.getObject("message_id", UUID::class.java),
            recipientKey = rs.getString("recipient_key"),
            recipientType = MessageRecipientType.valueOf(rs.getString("recipient_type")),
            userId = rs.getObject("user_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            displayName = rs.getString("display_name"),
            email = rs.getString("email"),
            phone = rs.getString("phone"),
            accessReason = MessageAccessReason.valueOf(rs.getString("access_reason")),
            inAppVisible = rs.getBoolean("in_app_visible"),
            emailStatus = DeliveryStatus.valueOf(rs.getString("email_status")),
            smsStatus = DeliveryStatus.valueOf(rs.getString("sms_status")),
            readAt = rs.getTimestamp("read_at")?.toInstant(),
            lastError = rs.getString("last_error"),
        )
}
