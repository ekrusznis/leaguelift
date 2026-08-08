package com.rally26.audit.persistence

import com.rally26.audit.domain.AuditResult
import com.rally26.testsupport.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuditEventRepositoryIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    @Test
    fun `audit parent is partitioned and august rows route into august partition`() {
        val relkind =
            jdbcClient
                .sql("select relkind from pg_class where relname = 'audit_event'")
                .query(String::class.java)
                .single()
        assertEquals("p", relkind)

        val partitionExists =
            jdbcClient
                .sql("select exists(select 1 from pg_class where relname = 'audit_event_2026_08')")
                .query(Boolean::class.java)
                .single()
        assertTrue(partitionExists)

        val eventId = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into audit_event (
                    id, action, entity_type, entity_id, summary, created_at
                ) values (
                    :id, 'PARTITION_TEST', 'SYSTEM', :entityId, 'partition test',
                    timestamptz '2026-08-15 12:00:00+00'
                )
                """.trimIndent(),
            ).param("id", eventId)
            .param("entityId", UUID.randomUUID())
            .update()

        val physicalTable =
            jdbcClient
                .sql("select tableoid::regclass::text from audit_event where id = :id")
                .param("id", eventId)
                .query(String::class.java)
                .single()
        assertEquals("audit_event_2026_08", physicalTable)
    }

    @Test
    fun `scoped audit events round trip and invalid cross organization scopes fail`() {
        val token = UUID.randomUUID().toString().take(8)
        val organizationA = insertOrganization("Audit A $token", "audit-a-$token")
        val organizationB = insertOrganization("Audit B $token", "audit-b-$token")
        val actorUser = insertUser("actor-$token@example.test")
        val targetUser = insertUser("target-$token@example.test")
        val teamA = insertTeam(organizationA, "Team A $token")
        val teamB = insertTeam(organizationB, "Team B $token")
        val householdA = insertHousehold(organizationA, "Household A $token")
        val participantA = insertParticipant(organizationA, householdA, "Athlete", token)
        val correlationId = UUID.randomUUID()

        auditEventRepository.insert(
            actorUserId = actorUser,
            organizationId = organizationA,
            action = "PARTICIPANT_UPDATED",
            entityType = "PARTICIPANT",
            entityId = participantA,
            metadataJson = "{}",
            teamId = teamA,
            householdId = householdA,
            participantId = participantA,
            targetUserId = targetUser,
            result = AuditResult.SUCCESS,
            summary = "Updated athlete profile",
            correlationId = correlationId,
        )

        val event = assertNotNull(auditEventRepository.listRecentForOrganization(organizationA, 10).firstOrNull())
        assertEquals(teamA, event.teamId)
        assertEquals(householdA, event.householdId)
        assertEquals(participantA, event.participantId)
        assertEquals(targetUser, event.targetUserId)
        assertEquals(correlationId, event.correlationId)
        assertEquals("Updated athlete profile", event.summary)

        assertFailsWith<DataAccessException> {
            auditEventRepository.insert(
                actorUserId = actorUser,
                organizationId = organizationA,
                action = "INVALID_SCOPE",
                entityType = "TEAM",
                entityId = teamB,
                metadataJson = "{}",
                teamId = teamB,
            )
        }
    }

    @Test
    fun `audit rows are database enforced append only`() {
        val eventId = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into audit_event (id, action, entity_type, entity_id, summary)
                values (:id, 'IMMUTABLE_TEST', 'SYSTEM', :entityId, 'immutable test')
                """.trimIndent(),
            ).param("id", eventId)
            .param("entityId", UUID.randomUUID())
            .update()

        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql("update audit_event set summary = 'changed' where id = :id")
                .param("id", eventId)
                .update()
        }
        assertFailsWith<DataAccessException> {
            jdbcClient
                .sql("delete from audit_event where id = :id")
                .param("id", eventId)
                .update()
        }
    }

    private fun insertOrganization(name: String, slug: String): UUID {
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
                insert into app_user (id, external_subject, email, display_name, status)
                values (:id, :subject, :email, 'Audit Test User', 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("subject", "audit-test-$id")
            .param("email", email)
            .update()
        return id
    }

    private fun insertTeam(organizationId: UUID, name: String): UUID {
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

    private fun insertHousehold(organizationId: UUID, displayName: String): UUID {
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

    private fun insertParticipant(
        organizationId: UUID,
        householdId: UUID,
        firstName: String,
        lastName: String,
    ): UUID {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into participant (id, organization_id, household_id, first_name, last_name, status)
                values (:id, :organizationId, :householdId, :firstName, :lastName, 'ACTIVE')
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("firstName", firstName)
            .param("lastName", lastName)
            .update()
        return id
    }
}
