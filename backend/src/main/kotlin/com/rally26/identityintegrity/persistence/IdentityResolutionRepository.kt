package com.rally26.identityintegrity.persistence

import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.identityintegrity.domain.IdentityResolutionOperationStatus
import com.rally26.identityintegrity.domain.IdentityResolutionOperationType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class IdentityResolutionRepository(
    private val jdbcClient: JdbcClient,
) {
    fun lock(ref: IdentityRef) {
        val table =
            when (ref.kind) {
                DuplicateIdentityKind.APP_USER -> "app_user"
                DuplicateIdentityKind.GUARDIAN_SHELL -> "household_adult"
            }
        jdbcClient
            .sql("select id from $table where id = :id for update")
            .param("id", ref.id)
            .query { rs, _ -> rs.getObject("id", UUID::class.java) }
            .optional()
    }

    fun lockSupportAccess(accessId: UUID) {
        jdbcClient
            .sql("select id from platform_support_access where id = :id for update")
            .param("id", accessId)
            .query { rs, _ -> rs.getObject("id", UUID::class.java) }
            .optional()
    }

    fun lockAssociations(ref: IdentityRef) {
        when (ref.kind) {
            DuplicateIdentityKind.APP_USER -> {
                listOf(
                    "select id from organization_membership where user_id = :id for update",
                    "select id from role_assignment where user_id = :id for update",
                    "select id from guardian_relationship where user_id = :id for update",
                    "select id from message_thread_member where user_id = :id for update",
                    "select id from message_recipient where user_id = :id for update",
                    "select id from announcement_recipient where user_id = :id for update",
                    "select id from email_verification_token where user_id = :id for update",
                    "select id from password_reset_token where user_id = :id for update",
                ).forEach { sql ->
                    jdbcClient
                        .sql(sql)
                        .param("id", ref.id)
                        .query { rs, _ -> rs.getObject("id", UUID::class.java) }
                        .list()
                }
            }
            DuplicateIdentityKind.GUARDIAN_SHELL -> {
                jdbcClient
                    .sql("select id from guardian_relationship where household_adult_id = :id for update")
                    .param("id", ref.id)
                    .query { rs, _ -> rs.getObject("id", UUID::class.java) }
                    .list()
            }
        }
    }

    fun findCompletedBySource(ref: IdentityRef): ResolutionRecord? =
        jdbcClient
            .sql(
                """
                select id, operation_type, status, source_kind, source_id, target_kind, target_id,
                       organization_id, support_access_id, preview_hash, outcome_json::text as outcome_json,
                       recovery_json::text as recovery_json, completed_at
                from identity_resolution_operation
                where source_kind = :sourceKind and source_id = :sourceId and status = 'COMPLETED'
                order by completed_at desc
                limit 1
                """.trimIndent(),
            ).param("sourceKind", ref.kind.name)
            .param("sourceId", ref.id)
            .query { rs, _ ->
                ResolutionRecord(
                    id = rs.getObject("id", UUID::class.java),
                    operationType = IdentityResolutionOperationType.valueOf(rs.getString("operation_type")),
                    status = IdentityResolutionOperationStatus.valueOf(rs.getString("status")),
                    source =
                        IdentityRef(
                            DuplicateIdentityKind.valueOf(rs.getString("source_kind")),
                            rs.getObject("source_id", UUID::class.java),
                        ),
                    target =
                        IdentityRef(
                            DuplicateIdentityKind.valueOf(rs.getString("target_kind")),
                            rs.getObject("target_id", UUID::class.java),
                        ),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    supportAccessId = rs.getObject("support_access_id", UUID::class.java),
                    previewHash = rs.getString("preview_hash"),
                    outcomeJson = rs.getString("outcome_json"),
                    recoveryJson = rs.getString("recovery_json"),
                    completedAt = rs.getTimestamp("completed_at").toInstant(),
                )
            }.optional()
            .orElse(null)

    fun activeGuardianUserForAdult(adultId: UUID): UUID? =
        jdbcClient
            .sql(
                """
                select user_id
                from guardian_relationship
                where household_adult_id = :adultId and status = 'ACTIVE'
                order by created_at, id
                limit 1
                """.trimIndent(),
            ).param("adultId", adultId)
            .query { rs, _ -> rs.getObject("user_id", UUID::class.java) }
            .optional()
            .orElse(null)

    fun createGuardianRelationship(
        organizationId: UUID,
        householdId: UUID,
        adultId: UUID,
        userId: UUID,
        now: Instant,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into guardian_relationship
                    (id, organization_id, household_id, household_adult_id, user_id, status, created_at, updated_at)
                values
                    (:id, :organizationId, :householdId, :adultId, :userId, 'ACTIVE', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("adultId", adultId)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .update()
        return id
    }

    fun memberships(userId: UUID): List<MembershipRow> =
        jdbcClient
            .sql(
                """
                select id, organization_id, role, status
                from organization_membership
                where user_id = :userId
                order by organization_id, id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                MembershipRow(
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    role = rs.getString("role"),
                    status = rs.getString("status"),
                )
            }.list()

    fun moveMembership(
        id: UUID,
        targetUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update organization_membership set user_id = :targetUserId, updated_at = :now where id = :id")
            .param("targetUserId", targetUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun revokeMembership(
        id: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update organization_membership set status = 'REVOKED', updated_at = :now where id = :id and status <> 'REVOKED'")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun activeRoleAssignments(userId: UUID): List<RoleAssignmentRow> =
        jdbcClient
            .sql(
                """
                select id, organization_id, context_type, resource_id, role
                from role_assignment
                where user_id = :userId and status = 'ACTIVE' and context_type <> 'PLATFORM'
                order by organization_id, context_type, resource_id, role, id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                RoleAssignmentRow(
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    contextType = rs.getString("context_type"),
                    resourceId = rs.getObject("resource_id", UUID::class.java),
                    role = rs.getString("role"),
                )
            }.list()

    fun moveRoleAssignment(
        id: UUID,
        targetUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update role_assignment set user_id = :targetUserId, updated_at = :now where id = :id and status = 'ACTIVE'")
            .param("targetUserId", targetUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun revokeRoleAssignment(
        id: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update role_assignment set status = 'REVOKED', updated_at = :now where id = :id and status = 'ACTIVE'")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun activeGuardianRelationships(userId: UUID): List<GuardianRelationshipRow> =
        jdbcClient
            .sql(
                """
                select id, organization_id, household_id, household_adult_id
                from guardian_relationship
                where user_id = :userId and status = 'ACTIVE'
                order by organization_id, household_id, household_adult_id, id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                GuardianRelationshipRow(
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    householdAdultId = rs.getObject("household_adult_id", UUID::class.java),
                )
            }.list()

    fun moveGuardianRelationship(
        id: UUID,
        targetUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update guardian_relationship set user_id = :targetUserId, updated_at = :now where id = :id and status = 'ACTIVE'")
            .param("targetUserId", targetUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun revokeGuardianRelationship(
        id: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update guardian_relationship set status = 'REVOKED', updated_at = :now where id = :id and status = 'ACTIVE'")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun activeMessageThreadMemberships(userId: UUID): List<MessageThreadMembershipRow> =
        jdbcClient
            .sql(
                """
                select id, organization_id, thread_id, member_type, household_id, participant_id, access_reason, can_reply
                from message_thread_member
                where user_id = :userId and left_at is null
                order by thread_id, id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                MessageThreadMembershipRow(
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    threadId = rs.getObject("thread_id", UUID::class.java),
                    memberType = rs.getString("member_type"),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    participantId = rs.getObject("participant_id", UUID::class.java),
                    accessReason = rs.getString("access_reason"),
                    canReply = rs.getBoolean("can_reply"),
                )
            }.list()

    fun moveMessageThreadMembership(
        id: UUID,
        targetUserId: UUID,
    ): Int =
        jdbcClient
            .sql("update message_thread_member set user_id = :targetUserId where id = :id and left_at is null")
            .param("targetUserId", targetUserId)
            .param("id", id)
            .update()

    fun closeMessageThreadMembership(
        id: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql("update message_thread_member set left_at = :now where id = :id and left_at is null")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun visibleMessageRecipients(userId: UUID): List<InAppRecipientRow> =
        jdbcClient
            .sql(
                """
                select id, message_id as parent_id
                from message_recipient
                where user_id = :userId and in_app_visible = true
                order by message_id, id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                InAppRecipientRow(
                    id = rs.getObject("id", UUID::class.java),
                    parentId = rs.getObject("parent_id", UUID::class.java),
                )
            }.list()

    fun moveVisibleMessageRecipient(
        id: UUID,
        targetUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update message_recipient set user_id = :targetUserId, updated_at = :now where id = :id and in_app_visible = true",
            ).param("targetUserId", targetUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun visibleAnnouncementRecipients(userId: UUID): List<InAppRecipientRow> =
        jdbcClient
            .sql(
                """
                select id, announcement_id as parent_id
                from announcement_recipient
                where user_id = :userId and in_app_visible = true
                order by announcement_id, id
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                InAppRecipientRow(
                    id = rs.getObject("id", UUID::class.java),
                    parentId = rs.getObject("parent_id", UUID::class.java),
                )
            }.list()

    fun moveVisibleAnnouncementRecipient(
        id: UUID,
        targetUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update announcement_recipient set user_id = :targetUserId, updated_at = :now where id = :id and in_app_visible = true",
            ).param("targetUserId", targetUserId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()

    fun invalidateAuthenticationTokens(
        userId: UUID,
        now: Instant,
    ): Int {
        val timestamp = Timestamp.from(now)
        val verification =
            jdbcClient
                .sql(
                    "update email_verification_token set consumed_at = :now where user_id = :userId and consumed_at is null",
                ).param("now", timestamp)
                .param("userId", userId)
                .update()
        val reset =
            jdbcClient
                .sql(
                    "update password_reset_token set consumed_at = :now where user_id = :userId and consumed_at is null",
                ).param("now", timestamp)
                .param("userId", userId)
                .update()
        return verification + reset
    }

    fun retireSourceUser(
        sourceUserId: UUID,
        targetUserId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update app_user
                set status = 'SUSPENDED', merged_into_user_id = :targetUserId, merged_at = :now, updated_at = :now
                where id = :sourceUserId and merged_into_user_id is null
                """.trimIndent(),
            ).param("targetUserId", targetUserId)
            .param("now", Timestamp.from(now))
            .param("sourceUserId", sourceUserId)
            .update()

    fun insertCompleted(
        id: UUID,
        operationType: IdentityResolutionOperationType,
        source: IdentityRef,
        target: IdentityRef,
        organizationId: UUID,
        platformAdminUserId: UUID,
        supportAccessId: UUID,
        reason: String,
        previewHash: String,
        outcomeJson: String,
        recoveryJson: String,
        now: Instant,
    ) {
        jdbcClient
            .sql(
                """
                insert into identity_resolution_operation
                    (id, operation_type, status, source_kind, source_id, target_kind, target_id,
                     organization_id, platform_admin_user_id, support_access_id, reason, preview_hash,
                     outcome_json, recovery_json, created_at, completed_at)
                values
                    (:id, :operationType, 'COMPLETED', :sourceKind, :sourceId, :targetKind, :targetId,
                     :organizationId, :platformAdminUserId, :supportAccessId, :reason, :previewHash,
                     cast(:outcomeJson as jsonb), cast(:recoveryJson as jsonb), :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("operationType", operationType.name)
            .param("sourceKind", source.kind.name)
            .param("sourceId", source.id)
            .param("targetKind", target.kind.name)
            .param("targetId", target.id)
            .param("organizationId", organizationId)
            .param("platformAdminUserId", platformAdminUserId)
            .param("supportAccessId", supportAccessId)
            .param("reason", reason)
            .param("previewHash", previewHash)
            .param("outcomeJson", outcomeJson)
            .param("recoveryJson", recoveryJson)
            .param("now", Timestamp.from(now))
            .update()
    }
}

data class MembershipRow(
    val id: UUID,
    val organizationId: UUID,
    val role: String,
    val status: String,
)

data class RoleAssignmentRow(
    val id: UUID,
    val organizationId: UUID,
    val contextType: String,
    val resourceId: UUID,
    val role: String,
)

data class GuardianRelationshipRow(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val householdAdultId: UUID,
)

data class MessageThreadMembershipRow(
    val id: UUID,
    val organizationId: UUID,
    val threadId: UUID,
    val memberType: String,
    val householdId: UUID?,
    val participantId: UUID?,
    val accessReason: String,
    val canReply: Boolean,
) {
    fun sameAccessAs(other: MessageThreadMembershipRow): Boolean =
        memberType == other.memberType &&
            householdId == other.householdId &&
            participantId == other.participantId &&
            accessReason == other.accessReason &&
            canReply == other.canReply
}

data class InAppRecipientRow(
    val id: UUID,
    val parentId: UUID,
)

data class ResolutionRecord(
    val id: UUID,
    val operationType: IdentityResolutionOperationType,
    val status: IdentityResolutionOperationStatus,
    val source: IdentityRef,
    val target: IdentityRef,
    val organizationId: UUID,
    val supportAccessId: UUID,
    val previewHash: String,
    val outcomeJson: String,
    val recoveryJson: String,
    val completedAt: Instant,
)
