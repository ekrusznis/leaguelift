package com.rally26.communication.persistence

import com.rally26.common.web.PageRequest
import com.rally26.communication.domain.Announcement
import com.rally26.communication.domain.AnnouncementAudience
import com.rally26.communication.domain.AnnouncementKind
import com.rally26.communication.domain.AnnouncementRecipient
import com.rally26.communication.domain.AnnouncementRecipientCandidate
import com.rally26.communication.domain.AnnouncementRecipientType
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.domain.AnnouncementStatus
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.communication.domain.MyAnnouncement
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private val ANNOUNCEMENT_SELECT =
    """
    select a.*,
           case a.scope_type
             when 'ORGANIZATION' then o.name
             when 'TEAM' then t.name
             when 'TOURNAMENT' then tr.name
           end as scope_name,
           coalesce(ds.recipient_count, 0) as recipient_count,
           coalesce(ds.email_sent_count, 0) as email_sent_count,
           coalesce(ds.email_failed_count, 0) as email_failed_count,
           coalesce(ds.sms_sent_count, 0) as sms_sent_count,
           coalesce(ds.sms_failed_count, 0) as sms_failed_count
    from announcement a
    join organization o on o.id = a.organization_id
    left join team t on a.scope_type = 'TEAM' and t.id = a.scope_id and t.organization_id = a.organization_id
    left join tournament tr on a.scope_type = 'TOURNAMENT' and tr.id = a.scope_id and tr.organization_id = a.organization_id
    left join (
        select announcement_id, count(*) as recipient_count,
               count(*) filter (where email_status = 'SENT') as email_sent_count,
               count(*) filter (where email_status = 'FAILED') as email_failed_count,
               count(*) filter (where sms_status = 'SENT') as sms_sent_count,
               count(*) filter (where sms_status = 'FAILED') as sms_failed_count
        from announcement_recipient group by announcement_id
    ) ds on ds.announcement_id = a.id
    """.trimIndent()

@Repository
class AnnouncementRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Announcement? =
        jdbcClient
            .sql(
                "$ANNOUNCEMENT_SELECT where a.id = :id and a.organization_id = :organizationId",
            ).param("id", id)
            .param("organizationId", organizationId)
            .query(::mapAnnouncement)
            .optional()
            .orElse(null)

    fun findBySourceKey(
        organizationId: UUID,
        sourceKey: String,
    ): Announcement? =
        jdbcClient
            .sql(
                "$ANNOUNCEMENT_SELECT where a.organization_id = :organizationId and a.source_key = :sourceKey",
            ).param("organizationId", organizationId)
            .param("sourceKey", sourceKey)
            .query(::mapAnnouncement)
            .optional()
            .orElse(null)

    fun insert(
        organizationId: UUID,
        scopeType: AnnouncementScopeType,
        scopeId: UUID,
        kind: AnnouncementKind,
        relatedEntityType: String?,
        relatedEntityId: UUID?,
        targetHouseholdId: UUID?,
        sourceKey: String?,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        createdByUserId: UUID,
    ): Announcement {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into announcement
                    (id, organization_id, scope_type, scope_id, kind, related_entity_type, related_entity_id,
                     target_household_id, source_key, title, body, audience, status, email_enabled, sms_enabled,
                     created_by_user_id, created_at, updated_at)
                values
                    (:id, :organizationId, :scopeType, :scopeId, :kind, :relatedEntityType, :relatedEntityId,
                     :targetHouseholdId, :sourceKey, :title, :body, :audience, 'DRAFT', :emailEnabled, :smsEnabled,
                     :createdByUserId, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("scopeType", scopeType.name)
            .param("scopeId", scopeId)
            .param("kind", kind.name)
            .param("relatedEntityType", relatedEntityType)
            .param("relatedEntityId", relatedEntityId)
            .param("targetHouseholdId", targetHouseholdId)
            .param("sourceKey", sourceKey)
            .param("title", title)
            .param("body", body)
            .param("audience", audience.name)
            .param("emailEnabled", emailEnabled)
            .param("smsEnabled", smsEnabled)
            .param("createdByUserId", createdByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun updateDraft(
        id: UUID,
        organizationId: UUID,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
    ): Int =
        jdbcClient
            .sql(
                """
                update announcement set title = :title, body = :body, audience = :audience,
                    email_enabled = :emailEnabled, sms_enabled = :smsEnabled, updated_at = now()
                where id = :id and organization_id = :organizationId and status = 'DRAFT'
                """.trimIndent(),
            ).param("title", title)
            .param("body", body)
            .param("audience", audience.name)
            .param("emailEnabled", emailEnabled)
            .param("smsEnabled", smsEnabled)
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun publish(
        id: UUID,
        organizationId: UUID,
        userId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update announcement set status = 'PUBLISHED', published_by_user_id = :userId,
                    published_at = :now, updated_at = :now
                where id = :id and organization_id = :organizationId and status = 'DRAFT'
                """.trimIndent(),
            ).param("userId", userId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun archive(
        id: UUID,
        organizationId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update announcement set status = 'ARCHIVED', archived_at = :now, updated_at = :now
                where id = :id and organization_id = :organizationId and status <> 'ARCHIVED'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun listForManagement(
        organizationId: UUID,
        scopeType: AnnouncementScopeType?,
        scopeId: UUID?,
        status: AnnouncementStatus?,
        page: PageRequest,
    ): List<Announcement> =
        jdbcClient
            .sql(
                """
                $ANNOUNCEMENT_SELECT
                where a.organization_id = :organizationId
                  and (:scopeType::text is null or a.scope_type = :scopeType)
                  and (:scopeId::uuid is null or a.scope_id = :scopeId)
                  and (:status::text is null or a.status = :status)
                order by a.created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("scopeType", scopeType?.name)
            .param("scopeId", scopeId)
            .param("status", status?.name)
            .param("offset", page.offset)
            .param("limit", page.size)
            .query(::mapAnnouncement)
            .list()

    fun countForManagement(
        organizationId: UUID,
        scopeType: AnnouncementScopeType?,
        scopeId: UUID?,
        status: AnnouncementStatus?,
    ): Long =
        jdbcClient
            .sql(
                """
                select count(*) from announcement a
                where a.organization_id = :organizationId
                  and (:scopeType::text is null or a.scope_type = :scopeType)
                  and (:scopeId::uuid is null or a.scope_id = :scopeId)
                  and (:status::text is null or a.status = :status)
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("scopeType", scopeType?.name)
            .param("scopeId", scopeId)
            .param("status", status?.name)
            .query(Long::class.java)
            .single()

    fun insertRecipient(
        announcementId: UUID,
        organizationId: UUID,
        recipientKey: String,
        candidate: AnnouncementRecipientCandidate,
        inAppVisible: Boolean,
        emailStatus: DeliveryStatus,
        smsStatus: DeliveryStatus,
    ) {
        jdbcClient
            .sql(
                """
                insert into announcement_recipient
                    (id, announcement_id, organization_id, recipient_key, recipient_type, user_id, household_id,
                     display_name, email, phone, in_app_visible, email_status, sms_status, created_at, updated_at)
                values
                    (:id, :announcementId, :organizationId, :recipientKey, :recipientType, :userId, :householdId,
                     :displayName, :email, :phone, :inAppVisible, :emailStatus, :smsStatus, now(), now())
                on conflict (announcement_id, recipient_key) do nothing
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("announcementId", announcementId)
            .param("organizationId", organizationId)
            .param("recipientKey", recipientKey)
            .param("recipientType", candidate.recipientType.name)
            .param("userId", candidate.userId)
            .param("householdId", candidate.householdId)
            .param("displayName", candidate.displayName)
            .param("email", candidate.email)
            .param("phone", candidate.phone)
            .param("inAppVisible", inAppVisible)
            .param("emailStatus", emailStatus.name)
            .param("smsStatus", smsStatus.name)
            .update()
    }

    fun listDeliveries(announcementId: UUID): List<AnnouncementRecipient> =
        jdbcClient
            .sql(
                """
                select id, announcement_id, recipient_key, recipient_type, user_id, household_id, display_name, email, phone,
                       in_app_visible, email_status, sms_status, read_at, last_error
                from announcement_recipient
                where announcement_id = :announcementId
                  and (email_status in ('PENDING', 'FAILED') or sms_status in ('PENDING', 'FAILED'))
                order by created_at asc
                """.trimIndent(),
            ).param("announcementId", announcementId)
            .query(::mapRecipient)
            .list()

    fun markEmailSent(
        recipientId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update announcement_recipient set email_status = 'SENT', email_sent_at = :now, last_error = null, updated_at = :now where id = :id",
            ).param("now", Timestamp.from(now))
            .param("id", recipientId)
            .update()

    fun markSmsSent(
        recipientId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update announcement_recipient set sms_status = 'SENT', sms_sent_at = :now, last_error = null, updated_at = :now where id = :id",
            ).param("now", Timestamp.from(now))
            .param("id", recipientId)
            .update()

    fun markEmailFailed(
        recipientId: UUID,
        error: String,
    ): Int =
        jdbcClient
            .sql(
                "update announcement_recipient set email_status = 'FAILED', last_error = :error, updated_at = now() where id = :id",
            ).param("error", error.take(1000))
            .param("id", recipientId)
            .update()

    fun markSmsFailed(
        recipientId: UUID,
        error: String,
    ): Int =
        jdbcClient
            .sql(
                "update announcement_recipient set sms_status = 'FAILED', last_error = :error, updated_at = now() where id = :id",
            ).param("error", error.take(1000))
            .param("id", recipientId)
            .update()

    fun listMineSafe(
        userId: UUID,
        page: PageRequest,
    ): List<MyAnnouncement> =
        jdbcClient
            .sql(
                """
                select a.*, mine.id as recipient_id, mine.read_at,
                       case a.scope_type when 'ORGANIZATION' then o.name when 'TEAM' then t.name when 'TOURNAMENT' then tr.name end as scope_name,
                       coalesce(ds.recipient_count, 0) as recipient_count,
                       coalesce(ds.email_sent_count, 0) as email_sent_count,
                       coalesce(ds.email_failed_count, 0) as email_failed_count,
                       coalesce(ds.sms_sent_count, 0) as sms_sent_count,
                       coalesce(ds.sms_failed_count, 0) as sms_failed_count
                from announcement a
                join announcement_recipient mine on mine.announcement_id = a.id
                join organization o on o.id = a.organization_id
                left join team t on a.scope_type = 'TEAM' and t.id = a.scope_id and t.organization_id = a.organization_id
                left join tournament tr on a.scope_type = 'TOURNAMENT' and tr.id = a.scope_id and tr.organization_id = a.organization_id
                left join (
                    select announcement_id, count(*) as recipient_count,
                           count(*) filter (where email_status = 'SENT') as email_sent_count,
                           count(*) filter (where email_status = 'FAILED') as email_failed_count,
                           count(*) filter (where sms_status = 'SENT') as sms_sent_count,
                           count(*) filter (where sms_status = 'FAILED') as sms_failed_count
                    from announcement_recipient group by announcement_id
                ) ds on ds.announcement_id = a.id
                where mine.user_id = :userId and mine.in_app_visible = true and a.status = 'PUBLISHED'
                order by a.published_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("userId", userId)
            .param("offset", page.offset)
            .param("limit", page.size)
            .query {
                rs,
                _,
                ->
                MyAnnouncement(
                    mapAnnouncement(rs, 0),
                    rs.getObject("recipient_id", UUID::class.java),
                    rs.getTimestamp("read_at")?.toInstant(),
                )
            }.list()

    fun countMine(userId: UUID): Long =
        jdbcClient
            .sql(
                "select count(*) from announcement_recipient ar join announcement a on a.id = ar.announcement_id where ar.user_id = :userId and ar.in_app_visible = true and a.status = 'PUBLISHED'",
            ).param("userId", userId)
            .query(Long::class.java)
            .single()

    fun markRead(
        announcementId: UUID,
        userId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update announcement_recipient set read_at = coalesce(read_at, :now), updated_at = :now where announcement_id = :announcementId and user_id = :userId and in_app_visible = true",
            ).param("now", Timestamp.from(now))
            .param("announcementId", announcementId)
            .param("userId", userId)
            .update()

    fun listOrganizationStaff(organizationId: UUID): List<AnnouncementRecipientCandidate> =
        jdbcClient
            .sql(
                """
                select distinct au.id as user_id, au.display_name, au.email
                from organization_membership om join app_user au on au.id = om.user_id and au.status = 'ACTIVE'
                where om.organization_id = :organizationId and om.status = 'ACTIVE'
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ -> candidate(rs, AnnouncementRecipientType.STAFF) }
            .list()

    fun listTeamStaff(
        organizationId: UUID,
        teamId: UUID,
    ): List<AnnouncementRecipientCandidate> =
        jdbcClient
            .sql(
                """
                select distinct au.id as user_id, au.display_name, au.email
                from app_user au
                where au.status = 'ACTIVE' and (
                    exists (select 1 from role_assignment ra where ra.user_id = au.id and ra.organization_id = :organizationId
                            and ra.context_type = 'TEAM' and ra.resource_id = :teamId and ra.status = 'ACTIVE')
                    or exists (select 1 from organization_membership om where om.user_id = au.id and om.organization_id = :organizationId
                               and om.status = 'ACTIVE' and om.role in ('OWNER', 'ADMINISTRATOR'))
                )
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("teamId", teamId)
            .query {
                rs,
                _,
                ->
                candidate(rs, AnnouncementRecipientType.STAFF)
            }.list()

    fun listTournamentStaff(
        organizationId: UUID,
        tournamentId: UUID,
    ): List<AnnouncementRecipientCandidate> =
        jdbcClient
            .sql(
                """
                select distinct au.id as user_id, au.display_name, au.email
                from app_user au
                where au.status = 'ACTIVE' and (
                    exists (select 1 from role_assignment ra where ra.user_id = au.id and ra.organization_id = :organizationId
                            and ra.context_type = 'TOURNAMENT' and ra.resource_id = :tournamentId and ra.status = 'ACTIVE')
                    or exists (select 1 from organization_membership om where om.user_id = au.id and om.organization_id = :organizationId
                               and om.status = 'ACTIVE' and om.role in ('OWNER', 'ADMINISTRATOR'))
                )
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("tournamentId", tournamentId)
            .query {
                rs,
                _,
                ->
                candidate(rs, AnnouncementRecipientType.STAFF)
            }.list()

    fun listOrganizationGuardians(organizationId: UUID): List<AnnouncementRecipientCandidate> = guardiansQuery(organizationId, null, null)

    fun listTeamGuardians(
        organizationId: UUID,
        teamId: UUID,
    ): List<AnnouncementRecipientCandidate> = guardiansQuery(organizationId, teamId, null)

    fun listHouseholdGuardians(
        organizationId: UUID,
        householdId: UUID,
    ): List<AnnouncementRecipientCandidate> = guardiansQuery(organizationId, null, householdId)

    fun listUnacknowledgedDocumentGuardians(
        organizationId: UUID,
        householdId: UUID,
        mediaAssignmentId: UUID,
    ): List<AnnouncementRecipientCandidate> =
        jdbcClient
            .sql(
                """
                select distinct ha.id as adult_id, gr.user_id, h.id as household_id,
                       trim(ha.first_name || ' ' || ha.last_name) as display_name,
                       case when h.email_reminders_opt_out then null else coalesce(nullif(trim(ha.email), ''), nullif(trim(h.contact_email), ''), au.email) end as email,
                       case when h.sms_reminders_opt_in then coalesce(nullif(trim(ha.phone), ''), nullif(trim(h.contact_phone), '')) else null end as phone
                from household h
                join household_adult ha on ha.household_id = h.id and ha.organization_id = h.organization_id and ha.status = 'ACTIVE'
                left join guardian_relationship gr on gr.household_adult_id = ha.id and gr.status = 'ACTIVE'
                left join app_user au on au.id = gr.user_id and au.status = 'ACTIVE'
                left join document_acknowledgment da
                  on da.media_assignment_id = :mediaAssignmentId and da.household_adult_id = ha.id
                where h.organization_id = :organizationId and h.id = :householdId and h.status = 'ACTIVE' and da.id is null
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("mediaAssignmentId", mediaAssignmentId)
            .query { rs, _ ->
                AnnouncementRecipientCandidate(
                    recipientType = AnnouncementRecipientType.GUARDIAN,
                    userId = rs.getObject("user_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    phone = rs.getString("phone"),
                )
            }.list()

    private fun guardiansQuery(
        organizationId: UUID,
        teamId: UUID?,
        householdId: UUID?,
    ): List<AnnouncementRecipientCandidate> {
        val teamClause =
            if (teamId == null) {
                ""
            } else {
                """
                and exists (
                    select 1 from participant p join participant_team pt on pt.participant_id = p.id and pt.status = 'ACTIVE'
                    where p.household_id = h.id and p.status = 'ACTIVE' and pt.team_id = :teamId
                )
                """.trimIndent()
            }
        val householdClause = if (householdId == null) "" else "and h.id = :householdId"
        var statement =
            jdbcClient
                .sql(
                    """
                    select distinct ha.id as adult_id, gr.user_id, h.id as household_id,
                           trim(ha.first_name || ' ' || ha.last_name) as display_name,
                           case when h.email_reminders_opt_out then null else coalesce(nullif(trim(ha.email), ''), nullif(trim(h.contact_email), ''), au.email) end as email,
                           case when h.sms_reminders_opt_in then coalesce(nullif(trim(ha.phone), ''), nullif(trim(h.contact_phone), '')) else null end as phone
                    from household h
                    join household_adult ha on ha.household_id = h.id and ha.organization_id = h.organization_id and ha.status = 'ACTIVE'
                    left join guardian_relationship gr on gr.household_adult_id = ha.id and gr.status = 'ACTIVE'
                    left join app_user au on au.id = gr.user_id and au.status = 'ACTIVE'
                    where h.organization_id = :organizationId and h.status = 'ACTIVE'
                      $householdClause
                      $teamClause
                    """.trimIndent(),
                ).param("organizationId", organizationId)
        if (teamId != null) statement = statement.param("teamId", teamId)
        if (householdId != null) statement = statement.param("householdId", householdId)
        return statement
            .query { rs, _ ->
                AnnouncementRecipientCandidate(
                    recipientType = AnnouncementRecipientType.GUARDIAN,
                    userId = rs.getObject("user_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    phone = rs.getString("phone"),
                )
            }.list()
    }

    fun listOrganizationAthletes(organizationId: UUID): List<AnnouncementRecipientCandidate> = athletesQuery(organizationId, null)

    fun listTeamAthletes(
        organizationId: UUID,
        teamId: UUID,
    ): List<AnnouncementRecipientCandidate> = athletesQuery(organizationId, teamId)

    private fun athletesQuery(
        organizationId: UUID,
        teamId: UUID?,
    ): List<AnnouncementRecipientCandidate> {
        val teamJoin =
            if (teamId ==
                null
            ) {
                ""
            } else {
                "join participant_team pt on pt.participant_id = p.id and pt.status = 'ACTIVE' and pt.team_id = :teamId"
            }
        var statement =
            jdbcClient
                .sql(
                    """
                    select distinct au.id as user_id, au.display_name, au.email
                    from role_assignment ra
                    join participant p on p.id = ra.resource_id and p.organization_id = ra.organization_id and p.status = 'ACTIVE'
                    $teamJoin
                    join app_user au on au.id = ra.user_id and au.status = 'ACTIVE'
                    where ra.organization_id = :organizationId and ra.context_type = 'PARTICIPANT' and ra.status = 'ACTIVE'
                    """.trimIndent(),
                ).param("organizationId", organizationId)
        if (teamId != null) statement = statement.param("teamId", teamId)
        return statement.query { rs, _ -> candidate(rs, AnnouncementRecipientType.ATHLETE) }.list()
    }

    private fun candidate(
        rs: java.sql.ResultSet,
        type: AnnouncementRecipientType,
    ) = AnnouncementRecipientCandidate(
        recipientType = type,
        userId = rs.getObject("user_id", UUID::class.java),
        householdId = null,
        displayName = rs.getString("display_name"),
        email = rs.getString("email"),
        phone = null,
    )

    private fun mapAnnouncement(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): Announcement =
        Announcement(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            scopeType = AnnouncementScopeType.valueOf(rs.getString("scope_type")),
            scopeId = rs.getObject("scope_id", UUID::class.java),
            scopeName = rs.getString("scope_name"),
            kind = AnnouncementKind.valueOf(rs.getString("kind")),
            relatedEntityType = rs.getString("related_entity_type"),
            relatedEntityId = rs.getObject("related_entity_id", UUID::class.java),
            targetHouseholdId = rs.getObject("target_household_id", UUID::class.java),
            sourceKey = rs.getString("source_key"),
            title = rs.getString("title"),
            body = rs.getString("body"),
            audience = AnnouncementAudience.valueOf(rs.getString("audience")),
            status = AnnouncementStatus.valueOf(rs.getString("status")),
            emailEnabled = rs.getBoolean("email_enabled"),
            smsEnabled = rs.getBoolean("sms_enabled"),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            publishedByUserId = rs.getObject("published_by_user_id", UUID::class.java),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            archivedAt = rs.getTimestamp("archived_at")?.toInstant(),
            recipientCount = rs.getLong("recipient_count"),
            emailSentCount = rs.getLong("email_sent_count"),
            emailFailedCount = rs.getLong("email_failed_count"),
            smsSentCount = rs.getLong("sms_sent_count"),
            smsFailedCount = rs.getLong("sms_failed_count"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )

    private fun mapRecipient(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ) = AnnouncementRecipient(
        id = rs.getObject("id", UUID::class.java),
        announcementId = rs.getObject("announcement_id", UUID::class.java),
        recipientKey = rs.getString("recipient_key"),
        recipientType = AnnouncementRecipientType.valueOf(rs.getString("recipient_type")),
        userId = rs.getObject("user_id", UUID::class.java),
        householdId = rs.getObject("household_id", UUID::class.java),
        displayName = rs.getString("display_name"),
        email = rs.getString("email"),
        phone = rs.getString("phone"),
        inAppVisible = rs.getBoolean("in_app_visible"),
        emailStatus = DeliveryStatus.valueOf(rs.getString("email_status")),
        smsStatus = DeliveryStatus.valueOf(rs.getString("sms_status")),
        readAt = rs.getTimestamp("read_at")?.toInstant(),
        lastError = rs.getString("last_error"),
    )
}
