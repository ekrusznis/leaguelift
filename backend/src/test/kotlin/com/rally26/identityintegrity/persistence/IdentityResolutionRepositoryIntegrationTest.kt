package com.rally26.identityintegrity.persistence

import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.testsupport.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdentityResolutionRepositoryIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var repository: IdentityResolutionRepository

    @Autowired
    private lateinit var duplicateIdentityRepository: DuplicateIdentityRepository

    @Test
    fun `repository moves current messaging access and closes equivalent duplicate thread membership`() {
        val token = UUID.randomUUID().toString().take(8)
        val organizationId = insertOrganization("Resolution Repo $token", "resolution-repo-$token")
        val sourceUserId = insertUser("source-repo-$token@example.test")
        val targetUserId = insertUser("target-repo-$token@example.test")
        val teamId = insertTeam(organizationId, "Resolution Team $token")
        val householdId = insertHousehold(organizationId, "Resolution Household $token")
        val moveThreadId = insertConversationThread(organizationId, teamId, targetUserId, "move-$token")
        val dedupeThreadId = insertConversationThread(organizationId, teamId, targetUserId, "dedupe-$token")

        val moveMemberId = insertThreadMember(organizationId, moveThreadId, sourceUserId, householdId)
        val sourceDedupeMemberId = insertThreadMember(organizationId, dedupeThreadId, sourceUserId, householdId)
        insertThreadMember(organizationId, dedupeThreadId, targetUserId, householdId)

        val sourceRows = repository.activeMessageThreadMemberships(sourceUserId).associateBy { it.threadId }
        val targetRows = repository.activeMessageThreadMemberships(targetUserId).associateBy { it.threadId }
        val sourceDedupe = assertNotNull(sourceRows[dedupeThreadId])
        val targetDedupe = assertNotNull(targetRows[dedupeThreadId])
        assertTrue(sourceDedupe.sameAccessAs(targetDedupe))

        assertEquals(1, repository.moveMessageThreadMembership(moveMemberId, targetUserId))
        assertEquals(1, repository.closeMessageThreadMembership(sourceDedupeMemberId, Instant.now()))

        val movedUser =
            jdbcClient
                .sql("select user_id from message_thread_member where id = :id")
                .param("id", moveMemberId)
                .query { rs, _ -> rs.getObject("user_id", UUID::class.java) }
                .single()
        val closedAt =
            jdbcClient
                .sql("select left_at from message_thread_member where id = :id")
                .param("id", sourceDedupeMemberId)
                .query { rs, _ -> rs.getTimestamp("left_at") }
                .single()
        assertEquals(targetUserId, movedUser)
        assertNotNull(closedAt)

        val messageId = insertMessage(organizationId, moveThreadId, sourceUserId, token)
        val messageRecipientId = insertMessageRecipient(organizationId, messageId, sourceUserId, "message-$token")
        val announcementId = insertAnnouncement(organizationId, targetUserId, token)
        val announcementRecipientId = insertAnnouncementRecipient(organizationId, announcementId, sourceUserId, "announcement-$token")

        val dependencyInventory = duplicateIdentityRepository.dependencyInventory(IdentityRef(DuplicateIdentityKind.APP_USER, sourceUserId))
        assertTrue(dependencyInventory.any { it.tableName == "message_entry" && it.columnName == "sender_user_id" && it.historical })
        assertTrue(dependencyInventory.any { it.tableName == "message_thread_member" && it.columnName == "user_id" && !it.historical })
        assertTrue(dependencyInventory.any { it.tableName == "message_recipient" && it.columnName == "user_id" && !it.historical })
        assertTrue(dependencyInventory.any { it.tableName == "announcement_recipient" && it.columnName == "user_id" && !it.historical })

        val sourceMessageAccess = repository.visibleMessageRecipients(sourceUserId)
        assertTrue(sourceMessageAccess.any { it.id == messageRecipientId && it.parentId == messageId })
        assertEquals(1, repository.moveVisibleMessageRecipient(messageRecipientId, targetUserId, Instant.now()))
        assertTrue(repository.visibleMessageRecipients(targetUserId).any { it.id == messageRecipientId })

        assertTrue(
            repository.visibleAnnouncementRecipients(sourceUserId).any {
                it.id == announcementRecipientId &&
                    it.parentId == announcementId
            },
        )
        assertEquals(1, repository.moveVisibleAnnouncementRecipient(announcementRecipientId, targetUserId, Instant.now()))
        assertTrue(repository.visibleAnnouncementRecipients(targetUserId).any { it.id == announcementRecipientId })
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
                values (:id, :email, 'Resolution Repo User', 'ACTIVE')
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

    private fun insertConversationThread(
        organizationId: UUID,
        teamId: UUID,
        createdBy: UUID,
        keySuffix: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_thread
                    (id, organization_id, scope_type, scope_id, thread_type, idempotency_key, title, audience,
                     email_enabled, sms_enabled, status, created_by_user_id)
                values
                    (:id, :organizationId, 'TEAM', :teamId, 'CONVERSATION', :key, 'Resolution thread', 'SELECTED',
                     false, false, 'OPEN', :createdBy)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("key", "resolution-thread-$keySuffix")
            .param("createdBy", createdBy)
            .update()
        return id
    }

    private fun insertThreadMember(
        organizationId: UUID,
        threadId: UUID,
        userId: UUID,
        householdId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_thread_member
                    (id, organization_id, thread_id, user_id, member_type, household_id, display_name, access_reason, can_reply)
                values
                    (:id, :organizationId, :threadId, :userId, 'GUARDIAN', :householdId, 'Resolution Guardian', 'TARGETED', true)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("threadId", threadId)
            .param("userId", userId)
            .param("householdId", householdId)
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
                values (:id, :organizationId, :threadId, :sender, :key, 'Resolution repository message', now())
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("threadId", threadId)
            .param("sender", sender)
            .param("key", "resolution-message-$token")
            .update()
        return id
    }

    private fun insertMessageRecipient(
        organizationId: UUID,
        messageId: UUID,
        userId: UUID,
        key: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into message_recipient
                    (id, organization_id, message_id, recipient_key, recipient_type, user_id, display_name,
                     access_reason, in_app_visible, email_status, sms_status)
                values
                    (:id, :organizationId, :messageId, :key, 'GUARDIAN', :userId, 'Resolution Guardian',
                     'TARGETED', true, 'NONE', 'NONE')
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("messageId", messageId)
            .param("key", key)
            .param("userId", userId)
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
                     'Resolution repository announcement body.', 'ALL', 'DRAFT', false, false, :createdBy)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("title", "Resolution notice $token")
            .param("createdBy", createdBy)
            .update()
        return id
    }

    private fun insertAnnouncementRecipient(
        organizationId: UUID,
        announcementId: UUID,
        userId: UUID,
        key: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into announcement_recipient
                    (id, announcement_id, organization_id, recipient_key, recipient_type, user_id, display_name,
                     in_app_visible, email_status, sms_status)
                values
                    (:id, :announcementId, :organizationId, :key, 'GUARDIAN', :userId, 'Resolution Guardian',
                     true, 'NONE', 'NONE')
                """.trimIndent(),
            ).param("id", id)
            .param("announcementId", announcementId)
            .param("organizationId", organizationId)
            .param("key", key)
            .param("userId", userId)
            .update()
        return id
    }
}
