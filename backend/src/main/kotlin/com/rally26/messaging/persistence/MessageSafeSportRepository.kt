package com.rally26.messaging.persistence

import com.rally26.messaging.domain.GuardianMessagingParticipant
import com.rally26.messaging.domain.MessageContactRestriction
import com.rally26.messaging.domain.MessageContactRestrictionKind
import com.rally26.messaging.domain.MessageContactRestrictionStatus
import com.rally26.messaging.domain.MessageRetentionMode
import com.rally26.messaging.domain.MessageSafeSportPolicy
import com.rally26.messaging.domain.MessageSafeSportReviewStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class MessageSafeSportRepository(
    private val jdbcClient: JdbcClient,
) {
    fun ensurePolicy(organizationId: UUID) {
        jdbcClient
            .sql("insert into message_safe_sport_policy (organization_id) values (:organizationId) on conflict do nothing")
            .param("organizationId", organizationId)
            .update()
    }

    fun findPolicy(organizationId: UUID): MessageSafeSportPolicy? =
        jdbcClient
            .sql("select * from message_safe_sport_policy where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(::mapPolicy)
            .optional()
            .orElse(null)

    fun updatePolicy(
        organizationId: UUID,
        reviewStatus: MessageSafeSportReviewStatus,
        athleteMessagingEnabled: Boolean,
        reviewReference: String?,
        reviewerUserId: UUID?,
        reviewedAt: Instant?,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_safe_sport_policy
                   set review_status = :reviewStatus,
                       athlete_messaging_enabled = :athleteMessagingEnabled,
                       review_reference = :reviewReference,
                       reviewed_by_user_id = :reviewerUserId,
                       reviewed_at = :reviewedAt,
                       updated_at = :now
                 where organization_id = :organizationId
                """.trimIndent(),
            ).param("reviewStatus", reviewStatus.name)
            .param("athleteMessagingEnabled", athleteMessagingEnabled)
            .param("reviewReference", reviewReference)
            .param("reviewerUserId", reviewerUserId)
            .param("reviewedAt", reviewedAt?.let(Timestamp::from))
            .param("now", Timestamp.from(now))
            .param("organizationId", organizationId)
            .update()

    fun listGuardianParticipants(userId: UUID): List<GuardianMessagingParticipant> =
        jdbcClient
            .sql(
                """
                select distinct p.organization_id, p.id as participant_id,
                       trim(p.first_name || ' ' || p.last_name) as display_name
                  from guardian_relationship gr
                  join participant p on p.organization_id = gr.organization_id and p.household_id = gr.household_id and p.status = 'ACTIVE'
                 where gr.user_id = :userId and gr.status = 'ACTIVE'
                 order by display_name
                """.trimIndent(),
            ).param("userId", userId)
            .query {
                rs,
                _,
                ->
                GuardianMessagingParticipant(
                    rs.getObject("organization_id", UUID::class.java),
                    rs.getObject("participant_id", UUID::class.java),
                    rs.getString("display_name"),
                )
            }.list()

    fun isGuardianForParticipant(
        userId: UUID,
        organizationId: UUID,
        participantId: UUID,
    ): Boolean =
        jdbcClient
            .sql(
                """
                select exists(
                    select 1 from guardian_relationship gr
                    join participant p on p.organization_id = gr.organization_id and p.household_id = gr.household_id
                     where gr.user_id = :userId and gr.organization_id = :organizationId
                       and p.id = :participantId and gr.status = 'ACTIVE' and p.status = 'ACTIVE'
                )
                """.trimIndent(),
            ).param("userId", userId)
            .param("organizationId", organizationId)
            .param("participantId", participantId)
            .query(Boolean::class.java)
            .single()

    fun insertRestriction(
        organizationId: UUID,
        participantId: UUID,
        requestedByUserId: UUID,
        kind: MessageContactRestrictionKind,
        note: String?,
        now: Instant,
    ): MessageContactRestriction {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_contact_restriction
                    (id, organization_id, participant_id, requested_by_user_id, kind, note, created_at)
                values (:id, :organizationId, :participantId, :requestedByUserId, :kind, :note, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("participantId", participantId)
            .param("requestedByUserId", requestedByUserId)
            .param("kind", kind.name)
            .param("note", note)
            .param("now", Timestamp.from(now))
            .update()
        return findRestriction(id) ?: error("Inserted message contact restriction was not found.")
    }

    fun findRestriction(id: UUID): MessageContactRestriction? =
        restrictionQuery("where mcr.id = :id")
            .param("id", id)
            .query(::mapRestriction)
            .optional()
            .orElse(null)

    fun findActiveRestriction(
        organizationId: UUID,
        participantId: UUID,
        kind: MessageContactRestrictionKind,
    ): MessageContactRestriction? =
        restrictionQuery(
            "where mcr.organization_id = :organizationId and mcr.participant_id = :participantId and mcr.kind = :kind and mcr.status = 'ACTIVE'",
        ).param("organizationId", organizationId)
            .param("participantId", participantId)
            .param("kind", kind.name)
            .query(::mapRestriction)
            .optional()
            .orElse(null)

    fun listMine(userId: UUID): List<MessageContactRestriction> =
        restrictionQuery("where mcr.requested_by_user_id = :userId order by mcr.created_at desc")
            .param("userId", userId)
            .query(::mapRestriction)
            .list()

    fun liftRestriction(
        id: UUID,
        userId: UUID,
        note: String,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update message_contact_restriction
                   set status = 'LIFTED', lifted_at = :now, lifted_by_user_id = :userId, lift_note = :note
                 where id = :id and requested_by_user_id = :userId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("userId", userId)
            .param("note", note)
            .param("id", id)
            .update()

    fun listRestrictedAthleteUserIds(
        organizationId: UUID,
        teamId: UUID?,
        includeAdultOnly: Boolean = true,
    ): Set<UUID> {
        val teamJoin =
            if (teamId ==
                null
            ) {
                ""
            } else {
                "join participant_team pt on pt.organization_id = p.organization_id and pt.participant_id = p.id and pt.team_id = :teamId and pt.status = 'ACTIVE'"
            }
        val kindClause = if (includeAdultOnly) "and mcr.kind in ('ADULT_TO_MINOR', 'ALL_MESSAGING')" else "and mcr.kind = 'ALL_MESSAGING'"
        var query =
            jdbcClient
                .sql(
                    """
                    select distinct ra.user_id
                      from message_contact_restriction mcr
                      join participant p on p.id = mcr.participant_id and p.organization_id = mcr.organization_id and p.status = 'ACTIVE'
                      $teamJoin
                      join role_assignment ra on ra.organization_id = p.organization_id and ra.context_type = 'PARTICIPANT'
                                             and ra.resource_id = p.id and ra.status = 'ACTIVE'
                     where mcr.organization_id = :organizationId and mcr.status = 'ACTIVE' $kindClause
                    """.trimIndent(),
                ).param("organizationId", organizationId)
        if (teamId != null) query = query.param("teamId", teamId)
        return query
            .query(UUID::class.java)
            .list()
            .filterNotNull()
            .toSet()
    }

    private fun restrictionQuery(tail: String): JdbcClient.StatementSpec =
        jdbcClient.sql(
            """
            select mcr.*, trim(p.first_name || ' ' || p.last_name) as participant_display_name
              from message_contact_restriction mcr
              join participant p on p.id = mcr.participant_id and p.organization_id = mcr.organization_id
              $tail
            """.trimIndent(),
        )

    private fun mapPolicy(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ) = MessageSafeSportPolicy(
        organizationId = rs.getObject("organization_id", UUID::class.java),
        reviewStatus = MessageSafeSportReviewStatus.valueOf(rs.getString("review_status")),
        athleteMessagingEnabled = rs.getBoolean("athlete_messaging_enabled"),
        reviewReference = rs.getString("review_reference"),
        reviewedByUserId = rs.getObject("reviewed_by_user_id", UUID::class.java),
        reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
        retentionMode = MessageRetentionMode.valueOf(rs.getString("retention_mode")),
        guardianVisibilityRequired = rs.getBoolean("guardian_visibility_required"),
        adultMinorOpenTransparentRequired = rs.getBoolean("adult_minor_open_transparent_required"),
        parentDiscontinueRequestsEnforced = rs.getBoolean("parent_discontinue_requests_enforced"),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapRestriction(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ) = MessageContactRestriction(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        participantId = rs.getObject("participant_id", UUID::class.java),
        participantDisplayName = rs.getString("participant_display_name"),
        requestedByUserId = rs.getObject("requested_by_user_id", UUID::class.java),
        kind = MessageContactRestrictionKind.valueOf(rs.getString("kind")),
        note = rs.getString("note"),
        status = MessageContactRestrictionStatus.valueOf(rs.getString("status")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        liftedAt = rs.getTimestamp("lifted_at")?.toInstant(),
        liftedByUserId = rs.getObject("lifted_by_user_id", UUID::class.java),
        liftNote = rs.getString("lift_note"),
    )
}
