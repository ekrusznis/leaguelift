package com.rally26.identityintegrity.persistence

import com.rally26.testsupport.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentityResolutionCurrentAccessGuardIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Test
    fun `merged users cannot regain current access while historical attribution remains`() {
        val token = UUID.randomUUID().toString().take(8)
        val organizationId = insertOrganization("Guard Test $token", "guard-test-$token")
        val sourceUserId = insertUser("source-$token@example.test")
        val targetUserId = insertUser("target-$token@example.test")
        val teamId = insertTeam(organizationId, "Guard Team $token")
        val householdId = insertHousehold(organizationId, "Guard Household $token")
        val adultId = insertAdult(organizationId, householdId, token)
        val threadId = insertConversationThread(organizationId, teamId, targetUserId, token)
        val messageId = insertMessage(organizationId, threadId, sourceUserId, token)
        val announcementId = insertAnnouncement(organizationId, targetUserId, token)
        val auditId = insertHistoricalAudit(organizationId, sourceUserId)

        jdbcClient
            .sql(
                """
                update app_user
                set status = 'SUSPENDED', merged_into_user_id = :targetUserId, merged_at = now(), updated_at = now()
                where id = :sourceUserId
                """.trimIndent(),
            ).param("targetUserId", targetUserId)
            .param("sourceUserId", sourceUserId)
            .update()

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql(
                    """
                    insert into organization_membership (organization_id, user_id, role, status)
                    values (:organizationId, :userId, 'VIEWER', 'ACTIVE')
                    """.trimIndent(),
                ).param("organizationId", organizationId)
                .param("userId", sourceUserId)
                .update()
        }

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql(
                    """
                    insert into role_assignment (organization_id, user_id, context_type, resource_id, role, status)
                    values (:organizationId, :userId, 'TEAM', :teamId, 'COACH_READ', 'ACTIVE')
                    """.trimIndent(),
                ).param("organizationId", organizationId)
                .param("userId", sourceUserId)
                .param("teamId", teamId)
                .update()
        }

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql(
                    """
                    insert into guardian_relationship
                        (organization_id, household_id, household_adult_id, user_id, status)
                    values (:organizationId, :householdId, :adultId, :userId, 'ACTIVE')
                    """.trimIndent(),
                ).param("organizationId", organizationId)
                .param("householdId", householdId)
                .param("adultId", adultId)
                .param("userId", sourceUserId)
                .update()
        }

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql(
                    """
                    insert into message_thread_member
                        (organization_id, thread_id, user_id, member_type, household_id, display_name, access_reason, can_reply)
                    values (:organizationId, :threadId, :userId, 'GUARDIAN', :householdId, 'Merged Guardian', 'TARGETED', true)
                    """.trimIndent(),
                ).param("organizationId", organizationId)
                .param("threadId", threadId)
                .param("userId", sourceUserId)
                .param("householdId", householdId)
                .update()
        }

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql(
                    """
                    insert into message_recipient
                        (organization_id, message_id, recipient_key, recipient_type, user_id, display_name,
                         access_reason, in_app_visible, email_status, sms_status)
                    values (:organizationId, :messageId, :recipientKey, 'GUARDIAN', :userId, 'Merged Guardian',
                            'TARGETED', true, 'NONE', 'NONE')
                    """.trimIndent(),
                ).param("organizationId", organizationId)
                .param("messageId", messageId)
                .param("recipientKey", "merged-$token")
                .param("userId", sourceUserId)
                .update()
        }

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql(
                    """
                    insert into announcement_recipient
                        (announcement_id, organization_id, recipient_key, recipient_type, user_id, display_name,
                         in_app_visible, email_status, sms_status)
                    values (:announcementId, :organizationId, :recipientKey, 'GUARDIAN', :userId, 'Merged Guardian',
                            true, 'NONE', 'NONE')
                    """.trimIndent(),
                ).param("announcementId", announcementId)
                .param("organizationId", organizationId)
                .param("recipientKey", "merged-$token")
                .param("userId", sourceUserId)
                .update()
        }

        val senderUserId =
            jdbcClient
                .sql("select sender_user_id from message_entry where id = :id")
                .param("id", messageId)
                .query { rs, _ -> rs.getObject("sender_user_id", UUID::class.java) }
                .single()
        val auditActorUserId =
            jdbcClient
                .sql("select actor_user_id from audit_event where id = :id")
                .param("id", auditId)
                .query { rs, _ -> rs.getObject("actor_user_id", UUID::class.java) }
                .single()
        assertEquals(sourceUserId, senderUserId)
        assertEquals(sourceUserId, auditActorUserId)
    }

    private fun insertOrganization(
        name: String,
        slug: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into organization (id, name, slug, organization_type, status)
                values (:id, :name, :slug, 'TRAVEL_CLUB', 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("name", name)
            .param("slug", slug)
            .update()
        return id
    }

    private fun insertUser(email: String): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into app_user (id, email, display_name, status)
                values (:id, :email, 'Identity Guard User', 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("email", email)
            .update()
        return id
    }

    private fun insertTeam(
        organizationId: UUID,
        name: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into team (id, organization_id, name, sport, status)
                values (:id, :organizationId, :name, 'VOLLEYBALL', 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .update()
        return id
    }

    private fun insertHousehold(
        organizationId: UUID,
        displayName: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into household (id, organization_id, display_name, status)
                values (:id, :organizationId, :displayName, 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("displayName", displayName)
            .update()
        return id
    }

    private fun insertAdult(
        organizationId: UUID,
        householdId: UUID,
        token: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into household_adult (id, household_id, organization_id, first_name, last_name, email, status)
                values (:id, :householdId, :organizationId, 'Guard', 'Adult', :email, 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("householdId", householdId)
            .param("organizationId", organizationId)
            .param("email", "adult-$token@example.test")
            .update()
        return id
    }

    private fun insertConversationThread(
        organizationId: UUID,
        teamId: UUID,
        createdBy: UUID,
        token: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_thread
                    (id, organization_id, scope_type, scope_id, thread_type, idempotency_key, title, audience,
                     email_enabled, sms_enabled, status, created_by_user_id)
                values
                    (:id, :organizationId, 'TEAM', :teamId, 'CONVERSATION', :key, 'Guard thread', 'SELECTED',
                     false, false, 'OPEN', :createdBy)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("key", "guard-thread-$token")
            .param("createdBy", createdBy)
            .update()
        return id
    }

    private fun insertMessage(
        organizationId: UUID,
        threadId: UUID,
        sender: UUID,
        token: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_entry
                    (id, organization_id, thread_id, sender_user_id, idempotency_key, body, sent_at)
                values (:id, :organizationId, :threadId, :sender, :key, 'Historical sender stays source', now())
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("threadId", threadId)
            .param("sender", sender)
            .param("key", "guard-message-$token")
            .update()
        return id
    }

    private fun insertAnnouncement(
        organizationId: UUID,
        createdBy: UUID,
        token: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into announcement
                    (id, organization_id, scope_type, scope_id, kind, title, body, audience, status,
                     email_enabled, sms_enabled, created_by_user_id)
                values
                    (:id, :organizationId, 'ORGANIZATION', :organizationId, 'GENERAL', :title,
                     'Identity guard announcement body.', 'ALL', 'DRAFT', false, false, :createdBy)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("title", "Guard notice $token")
            .param("createdBy", createdBy)
            .update()
        return id
    }

    private fun insertHistoricalAudit(
        organizationId: UUID,
        actorUserId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into audit_event (id, actor_user_id, actor_type, organization_id, action, entity_type, entity_id, summary)
                values (:id, :actorUserId, 'USER', :organizationId, 'IDENTITY_GUARD_HISTORY', 'USER', :entityId, 'Historical attribution')
                """.trimIndent(),
            ).param("id", id)
            .param("actorUserId", actorUserId)
            .param("organizationId", organizationId)
            .param("entityId", actorUserId)
            .update()
        return id
    }
}
