package com.rally26.audit.persistence

import com.rally26.audit.domain.AuditHistoryQuery
import com.rally26.audit.domain.AuditHistorySortDirection
import com.rally26.audit.domain.AuditHistorySortField
import com.rally26.audit.domain.AuditResult
import com.rally26.testsupport.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditHistoryRepositoryIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    @Autowired
    private lateinit var historyRepository: AuditHistoryRepository

    @Test
    fun `history visibility follows athlete guardian coach owner and platform scopes in sql`() {
        val fixture = createFixture()

        val owner = historyRepository.search(fixture.ownerUser, false, AuditHistoryQuery(size = 50))
        assertEquals(setOf("ORG_A", "TEAM_A", "HOUSEHOLD_A", "GUARDIAN_SELF"), owner.map { it.action }.toSet())

        val coach = historyRepository.search(fixture.coachUser, false, AuditHistoryQuery(size = 50))
        assertEquals(setOf("TEAM_A", "HOUSEHOLD_A"), coach.map { it.action }.toSet())

        val guardian = historyRepository.search(fixture.guardianUser, false, AuditHistoryQuery(size = 50))
        assertEquals(setOf("HOUSEHOLD_A", "GUARDIAN_SELF"), guardian.map { it.action }.toSet())

        val athlete = historyRepository.search(fixture.athleteUser, false, AuditHistoryQuery(size = 50))
        assertEquals(setOf("HOUSEHOLD_A", "ATHLETE_SELF"), athlete.map { it.action }.toSet())

        val platform = historyRepository.search(fixture.ownerUser, true, AuditHistoryQuery(size = 50))
        assertEquals(6, platform.size)
        assertTrue(platform.any { it.action == "ORG_B" })
    }

    @Test
    fun `filter access grows with role and query filters never widen visibility`() {
        val fixture = createFixture()

        val athleteAccess = historyRepository.resolveFilterAccess(fixture.athleteUser, false)
        assertFalse(athleteAccess.canFilterUser)
        assertFalse(athleteAccess.canFilterTeam)
        assertFalse(athleteAccess.canFilterOrganization)

        val guardianAccess = historyRepository.resolveFilterAccess(fixture.guardianUser, false)
        assertTrue(guardianAccess.canFilterUser)
        assertFalse(guardianAccess.canFilterTeam)
        assertFalse(guardianAccess.canFilterOrganization)

        val coachAccess = historyRepository.resolveFilterAccess(fixture.coachUser, false)
        assertTrue(coachAccess.canFilterUser)
        assertTrue(coachAccess.canFilterTeam)
        assertFalse(coachAccess.canFilterOrganization)

        val ownerAccess = historyRepository.resolveFilterAccess(fixture.ownerUser, false)
        assertTrue(ownerAccess.canFilterUser)
        assertTrue(ownerAccess.canFilterTeam)
        assertTrue(ownerAccess.canFilterOrganization)

        val platformAccess = historyRepository.resolveFilterAccess(fixture.athleteUser, true)
        assertTrue(platformAccess.canFilterUser)
        assertTrue(platformAccess.canFilterTeam)
        assertTrue(platformAccess.canFilterOrganization)

        val coachFilteredToOtherOrg =
            historyRepository.search(
                fixture.coachUser,
                false,
                AuditHistoryQuery(organizationId = fixture.organizationB, size = 50),
            )
        assertTrue(coachFilteredToOtherOrg.isEmpty())
    }

    @Test
    fun `date action result keyword user and stable cursor sort filters work`() {
        val fixture = createFixture()

        val keyword =
            historyRepository.search(
                fixture.guardianUser,
                false,
                AuditHistoryQuery(keyword = "athlete profile", size = 50),
            )
        assertEquals(listOf("HOUSEHOLD_A"), keyword.map { it.action })

        val user =
            historyRepository.search(
                fixture.guardianUser,
                false,
                AuditHistoryQuery(userQuery = "Athlete $TEST_LAST_NAME", size = 50),
            )
        assertEquals(listOf("HOUSEHOLD_A"), user.map { it.action })

        val result =
            historyRepository.search(
                fixture.ownerUser,
                false,
                AuditHistoryQuery(result = AuditResult.DENIED, size = 50),
            )
        assertEquals(listOf("GUARDIAN_SELF"), result.map { it.action })

        val action =
            historyRepository.search(
                fixture.ownerUser,
                false,
                AuditHistoryQuery(action = "TEAM_A", size = 50),
            )
        assertEquals(listOf("TEAM_A"), action.map { it.action })

        val futureOnly =
            historyRepository.search(
                fixture.ownerUser,
                false,
                AuditHistoryQuery(from = Instant.now().plusSeconds(3600), size = 50),
            )
        assertTrue(futureOnly.isEmpty())

        val first =
            historyRepository.search(
                fixture.ownerUser,
                false,
                AuditHistoryQuery(
                    sortBy = AuditHistorySortField.ACTION,
                    direction = AuditHistorySortDirection.ASC,
                    size = 2,
                ),
            )
        assertEquals(3, first.size) // repository fetches size + 1 for service-level pagination
        val servicePage = first.take(2)
        val last = servicePage.last()
        val second =
            historyRepository.search(
                fixture.ownerUser,
                false,
                AuditHistoryQuery(
                    sortBy = AuditHistorySortField.ACTION,
                    direction = AuditHistorySortDirection.ASC,
                    size = 2,
                    cursor =
                        com.rally26.audit.domain.AuditHistoryCursor(
                            AuditHistorySortField.ACTION,
                            AuditHistorySortDirection.ASC,
                            last.action.lowercase(),
                            last.createdAt,
                            last.id,
                        ),
                ),
            )
        assertTrue(second.none { it.id in servicePage.map { page -> page.id } })
    }

    private fun createFixture(): Fixture {
        val token = UUID.randomUUID().toString().take(8)
        val organizationA = insertOrganization("History Org A $token", "history-a-$token")
        val organizationB = insertOrganization("History Org B $token", "history-b-$token")
        val owner = insertUser("Owner $token", "owner-$token@example.test")
        val coach = insertUser("Coach $token", "coach-$token@example.test")
        val guardian = insertUser("Guardian $token", "guardian-$token@example.test")
        val athlete = insertUser("Athlete $TEST_LAST_NAME", "athlete-$token@example.test")
        val outsider = insertUser("Outsider $token", "outsider-$token@example.test")
        val teamA = insertTeam(organizationA, "History Team A $token")
        val householdA = insertHousehold(organizationA, "History Household A $token")
        val adultA = insertAdult(organizationA, householdA, "Guardian", token)
        val participantA = insertParticipant(organizationA, householdA, "Athlete", TEST_LAST_NAME)

        insertMembership(organizationA, owner, "OWNER")
        insertRoleAssignment(organizationA, coach, "TEAM", teamA, "COACH_READ")
        insertGuardianRelationship(organizationA, householdA, adultA, guardian)
        insertRoleAssignment(organizationA, athlete, "PARTICIPANT", participantA, "ATHLETE_SELF")

        auditEventRepository.insert(
            outsider,
            organizationA,
            "ORG_A",
            "ORGANIZATION",
            organizationA,
            "{}",
            summary = "Organization profile changed",
        )
        auditEventRepository.insert(outsider, organizationA, "TEAM_A", "TEAM", teamA, "{}", summary = "Team schedule changed")
        auditEventRepository.insert(
            outsider,
            organizationA,
            "HOUSEHOLD_A",
            "PARTICIPANT",
            participantA,
            "{}",
            teamId = teamA,
            householdId = householdA,
            participantId = participantA,
            targetUserId = athlete,
            summary = "Athlete profile reviewed",
        )
        auditEventRepository.insert(
            guardian,
            organizationA,
            "GUARDIAN_SELF",
            "USER",
            guardian,
            "{}",
            householdId = householdA,
            result = AuditResult.DENIED,
            summary = "Guardian preference change denied",
        )
        auditEventRepository.insert(athlete, organizationB, "ATHLETE_SELF", "USER", athlete, "{}", summary = "Athlete signed in")
        auditEventRepository.insert(
            outsider,
            organizationB,
            "ORG_B",
            "ORGANIZATION",
            organizationB,
            "{}",
            summary = "Other organization changed",
        )

        return Fixture(organizationA, organizationB, owner, coach, guardian, athlete)
    }

    private fun insertOrganization(
        name: String,
        slug: String,
    ): UUID =
        UUID.randomUUID().also { id ->
            jdbcClient
                .sql(
                    "insert into organization (id, name, slug, organization_type, status) values (:id, :name, :slug, 'TRAVEL_CLUB', 'ACTIVE')",
                ).param("id", id)
                .param("name", name)
                .param("slug", slug)
                .update()
        }

    private fun insertUser(
        displayName: String,
        email: String,
    ): UUID =
        UUID.randomUUID().also { id ->
            jdbcClient
                .sql(
                    "insert into app_user (id, external_subject, email, display_name, status) values (:id, :subject, :email, :displayName, 'ACTIVE')",
                ).param("id", id)
                .param("subject", "history-$id")
                .param("email", email)
                .param("displayName", displayName)
                .update()
        }

    private fun insertTeam(
        organizationId: UUID,
        name: String,
    ): UUID =
        UUID.randomUUID().also { id ->
            jdbcClient
                .sql(
                    "insert into team (id, organization_id, name, sport, status) values (:id, :organizationId, :name, 'VOLLEYBALL', 'ACTIVE')",
                ).param("id", id)
                .param("organizationId", organizationId)
                .param("name", name)
                .update()
        }

    private fun insertHousehold(
        organizationId: UUID,
        displayName: String,
    ): UUID =
        UUID.randomUUID().also { id ->
            jdbcClient
                .sql(
                    "insert into household (id, organization_id, display_name, status) values (:id, :organizationId, :displayName, 'ACTIVE')",
                ).param("id", id)
                .param("organizationId", organizationId)
                .param("displayName", displayName)
                .update()
        }

    private fun insertAdult(
        organizationId: UUID,
        householdId: UUID,
        firstName: String,
        lastName: String,
    ): UUID =
        UUID.randomUUID().also { id ->
            jdbcClient
                .sql(
                    """
                    insert into household_adult (id, household_id, organization_id, first_name, last_name, is_primary, status)
                    values (:id, :householdId, :organizationId, :firstName, :lastName, true, 'ACTIVE')
                    """.trimIndent(),
                ).param("id", id)
                .param("householdId", householdId)
                .param("organizationId", organizationId)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .update()
        }

    private fun insertParticipant(
        organizationId: UUID,
        householdId: UUID,
        firstName: String,
        lastName: String,
    ): UUID =
        UUID.randomUUID().also { id ->
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
        }

    private fun insertMembership(
        organizationId: UUID,
        userId: UUID,
        role: String,
    ) {
        jdbcClient
            .sql(
                """
                insert into organization_membership (id, organization_id, user_id, role, status)
                values (:id, :organizationId, :userId, :role, 'ACTIVE')
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("role", role)
            .update()
    }

    private fun insertRoleAssignment(
        organizationId: UUID,
        userId: UUID,
        contextType: String,
        resourceId: UUID,
        role: String,
    ) {
        jdbcClient
            .sql(
                """
                insert into role_assignment (id, organization_id, user_id, context_type, resource_id, role, status)
                values (:id, :organizationId, :userId, :contextType, :resourceId, :role, 'ACTIVE')
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("contextType", contextType)
            .param("resourceId", resourceId)
            .param("role", role)
            .update()
    }

    private fun insertGuardianRelationship(
        organizationId: UUID,
        householdId: UUID,
        adultId: UUID,
        userId: UUID,
    ) {
        jdbcClient
            .sql(
                """
                insert into guardian_relationship (id, organization_id, household_id, household_adult_id, user_id, status)
                values (:id, :organizationId, :householdId, :adultId, :userId, 'ACTIVE')
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("adultId", adultId)
            .param("userId", userId)
            .update()
    }

    private data class Fixture(
        val organizationA: UUID,
        val organizationB: UUID,
        val ownerUser: UUID,
        val coachUser: UUID,
        val guardianUser: UUID,
        val athleteUser: UUID,
    )

    private companion object {
        const val TEST_LAST_NAME = "HistoryAthlete"
    }
}
