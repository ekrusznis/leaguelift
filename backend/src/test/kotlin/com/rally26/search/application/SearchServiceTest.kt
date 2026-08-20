package com.rally26.search.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.search.domain.SearchHit
import com.rally26.search.domain.SearchResultType
import com.rally26.search.persistence.SearchRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchServiceTest {
    private val searchRepository = mockk<SearchRepository>()
    private val membershipService = mockk<MembershipService>(relaxed = true)

    private val service = SearchService(searchRepository, membershipService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `searchOrganization returns nothing for a query shorter than 2 characters`() {
        val result = service.searchOrganization(orgId, "a", currentUser)

        assertEquals(emptyList(), result)
        verify(exactly = 0) { searchRepository.searchTeams(any(), any(), any()) }
    }

    @Test
    fun `searchOrganization requires manager role before querying`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } throws ForbiddenException("DENIED", "no")
        every { searchRepository.searchTeams(any(), any(), any()) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            service.searchOrganization(orgId, "smith", currentUser)
        }
    }

    @Test
    fun `searchOrganization denies a non-manager org member, such as a Viewer`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } throws
            ForbiddenException("MEMBERSHIP_MANAGEMENT_DENIED", "Only organization owners and administrators can manage members.")

        assertFailsWith<ForbiddenException> {
            service.searchOrganization(orgId, "smith", currentUser)
        }
        verify(exactly = 0) { searchRepository.searchTeams(any(), any(), any()) }
    }

    @Test
    fun `searchOrganization combines teams, participants, and households`() {
        val teamHit = SearchHit(SearchResultType.TEAM, UUID.randomUUID(), "U12 Blue", "Soccer")
        val participantHit = SearchHit(SearchResultType.PARTICIPANT, UUID.randomUUID(), "Maya Johnson", null)
        val householdHit = SearchHit(SearchResultType.HOUSEHOLD, UUID.randomUUID(), "Johnson Family", null)
        every { searchRepository.searchTeams(orgId, "johnson", 8) } returns listOf(teamHit)
        every { searchRepository.searchParticipants(orgId, "johnson", 8) } returns listOf(participantHit)
        every { searchRepository.searchHouseholds(orgId, "johnson", 8) } returns listOf(householdHit)

        val result = service.searchOrganization(orgId, "johnson", currentUser)

        assertEquals(listOf(teamHit, participantHit, householdHit), result)
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
