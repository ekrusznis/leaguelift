package com.rally26.search.application

import com.rally26.authorization.domain.GuardianRelationship
import com.rally26.authorization.domain.GuardianRelationshipStatus
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.domain.RoleAssignmentStatus
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.search.domain.SearchHit
import com.rally26.search.domain.SearchResultType
import com.rally26.search.persistence.SearchRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchServiceTest {
    private val searchRepository = mockk<SearchRepository>()
    private val membershipService = mockk<MembershipService>()
    private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
    private val roleAssignmentRepository = mockk<RoleAssignmentRepository>()

    private val service = SearchService(searchRepository, membershipService, guardianRelationshipRepository, roleAssignmentRepository)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    private fun membership(role: MembershipRole) =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = role,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `searchOrganization returns nothing for a query shorter than 2 characters`() {
        every { membershipService.hasManagerRole(orgId, currentUser) } returns true

        val result = service.searchOrganization(orgId, "a", currentUser)

        assertEquals(emptyList(), result)
        verify(exactly = 0) { searchRepository.searchTeams(any(), any(), any(), any()) }
    }

    @Test
    fun `searchOrganization denies a caller with no real connection to the organization`() {
        every { membershipService.hasManagerRole(orgId, currentUser) } returns false
        every { membershipService.requireActiveMembership(orgId, currentUser) } throws
            ForbiddenException("ORGANIZATION_ACCESS_DENIED", "You do not have access to this organization.")
        every { guardianRelationshipRepository.findActiveForUser(currentUser.userId) } returns emptyList()
        every {
            roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.PARTICIPANT)
        } returns emptyList()

        assertFailsWith<ForbiddenException> {
            service.searchOrganization(orgId, "smith", currentUser)
        }
        verify(exactly = 0) { searchRepository.searchTeams(any(), any(), any(), any()) }
    }

    @Test
    fun `searchOrganization gives an Owner unrestricted (null-scope) results`() {
        val teamHit = SearchHit(SearchResultType.TEAM, UUID.randomUUID(), "U12 Blue", "Soccer")
        val participantHit = SearchHit(SearchResultType.PARTICIPANT, UUID.randomUUID(), "Maya Johnson", null)
        val householdHit = SearchHit(SearchResultType.HOUSEHOLD, UUID.randomUUID(), "Johnson Family", null)
        every { membershipService.hasManagerRole(orgId, currentUser) } returns true
        every { searchRepository.searchTeams(orgId, "johnson", 8, null) } returns listOf(teamHit)
        every { searchRepository.searchParticipants(orgId, "johnson", 8, null) } returns listOf(participantHit)
        every { searchRepository.searchHouseholds(orgId, "johnson", 8, null) } returns listOf(householdHit)

        val result = service.searchOrganization(orgId, "johnson", currentUser)

        assertEquals(listOf(teamHit, participantHit, householdHit), result)
        verify(exactly = 0) { roleAssignmentRepository.findActiveForUserAndContext(any(), any()) }
    }

    @Test
    fun `searchOrganization scopes a Coach to the teams they coach`() {
        val coachUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")
        val teamId = UUID.randomUUID()
        val teamHit = SearchHit(SearchResultType.TEAM, teamId, "U12 Blue", "Soccer")
        every { membershipService.hasManagerRole(orgId, coachUser) } returns false
        every { membershipService.requireActiveMembership(orgId, coachUser) } returns membership(MembershipRole.TEAM_ADMINISTRATOR)
        every { guardianRelationshipRepository.findActiveForUser(coachUser.userId) } returns emptyList()
        every {
            roleAssignmentRepository.findActiveForUserAndContext(coachUser.userId, RoleAssignmentContextType.PARTICIPANT)
        } returns emptyList()
        every { searchRepository.resolveTeamScope(orgId, coachUser.userId) } returns setOf(teamId)
        every { searchRepository.searchTeams(orgId, "blue", 8, setOf(teamId)) } returns listOf(teamHit)
        every { searchRepository.searchParticipants(orgId, "blue", 8, setOf(teamId)) } returns emptyList()
        every { searchRepository.searchHouseholds(orgId, "blue", 8, setOf(teamId)) } returns emptyList()

        val result = service.searchOrganization(orgId, "blue", coachUser)

        assertEquals(listOf(teamHit), result)
    }

    @Test
    fun `searchOrganization returns empty results, not a 403, for a real guardian with no team scope yet`() {
        val guardianUser = CurrentUser(UUID.randomUUID(), "parent@example.com", "Parent")
        every { membershipService.hasManagerRole(orgId, guardianUser) } returns false
        every { membershipService.requireActiveMembership(orgId, guardianUser) } throws
            ForbiddenException("ORGANIZATION_ACCESS_DENIED", "no membership row")
        every { guardianRelationshipRepository.findActiveForUser(guardianUser.userId) } returns
            listOf(
                GuardianRelationship(
                    id = UUID.randomUUID(),
                    organizationId = orgId,
                    householdId = UUID.randomUUID(),
                    householdAdultId = UUID.randomUUID(),
                    userId = guardianUser.userId,
                    status = GuardianRelationshipStatus.ACTIVE,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        every {
            roleAssignmentRepository.findActiveForUserAndContext(guardianUser.userId, RoleAssignmentContextType.PARTICIPANT)
        } returns emptyList()
        every { searchRepository.resolveTeamScope(orgId, guardianUser.userId) } returns emptySet()
        every { searchRepository.searchTeams(orgId, "smith", 8, emptySet()) } returns emptyList()
        every { searchRepository.searchParticipants(orgId, "smith", 8, emptySet()) } returns emptyList()
        every { searchRepository.searchHouseholds(orgId, "smith", 8, emptySet()) } returns emptyList()

        val result = service.searchOrganization(orgId, "smith", guardianUser)

        assertEquals(emptyList(), result)
    }

    @Test
    fun `searchOrganization scopes a linked Athlete via their own PARTICIPANT role assignment`() {
        val athleteUser = CurrentUser(UUID.randomUUID(), "athlete@example.com", "Athlete")
        val teamId = UUID.randomUUID()
        val participantHit = SearchHit(SearchResultType.PARTICIPANT, UUID.randomUUID(), "Teammate Jones", null)
        every { membershipService.hasManagerRole(orgId, athleteUser) } returns false
        every { membershipService.requireActiveMembership(orgId, athleteUser) } throws
            ForbiddenException("ORGANIZATION_ACCESS_DENIED", "no membership row")
        every { guardianRelationshipRepository.findActiveForUser(athleteUser.userId) } returns emptyList()
        every {
            roleAssignmentRepository.findActiveForUserAndContext(athleteUser.userId, RoleAssignmentContextType.PARTICIPANT)
        } returns
            listOf(
                RoleAssignment(
                    id = UUID.randomUUID(),
                    organizationId = orgId,
                    userId = athleteUser.userId,
                    contextType = RoleAssignmentContextType.PARTICIPANT,
                    resourceId = UUID.randomUUID(),
                    role = ResourceRole.ATHLETE_SELF,
                    status = RoleAssignmentStatus.ACTIVE,
                    grantedBy = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        every { searchRepository.resolveTeamScope(orgId, athleteUser.userId) } returns setOf(teamId)
        every { searchRepository.searchTeams(orgId, "jones", 8, setOf(teamId)) } returns emptyList()
        every { searchRepository.searchParticipants(orgId, "jones", 8, setOf(teamId)) } returns listOf(participantHit)
        every { searchRepository.searchHouseholds(orgId, "jones", 8, setOf(teamId)) } returns emptyList()

        val result = service.searchOrganization(orgId, "jones", athleteUser)

        assertEquals(listOf(participantHit), result)
    }

    @Test
    fun `searchPlatform rejects a non-platform-administrator`() {
        assertFailsWith<ForbiddenException> {
            service.searchPlatform("acme", currentUser)
        }
    }

    @Test
    fun `searchPlatform searches organizations for a platform administrator`() {
        val adminUser = currentUser.copy(platformAdministrator = true)
        val orgHit = SearchHit(SearchResultType.ORGANIZATION, UUID.randomUUID(), "Acme Sports", "/acme-sports")
        every { searchRepository.searchOrganizations("acme", 8) } returns listOf(orgHit)

        val result = service.searchPlatform("acme", adminUser)

        assertEquals(listOf(orgHit), result)
    }
}
