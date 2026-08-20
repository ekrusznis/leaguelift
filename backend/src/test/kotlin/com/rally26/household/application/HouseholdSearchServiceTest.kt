package com.rally26.household.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.HouseholdSearchCriteria
import com.rally26.household.persistence.HouseholdSearchRepository
import com.rally26.membership.application.MembershipService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Security review (2026-08): this endpoint backs the org-manager-only "Households &
 * Athletes" page and previously only required active membership, letting any org member
 * (e.g. a Viewer) pull every household in the org. See [com.rally26.search.application.SearchService]'s
 * doc comment for the full finding.
 */
class HouseholdSearchServiceTest {
    private val repository = mockk<HouseholdSearchRepository>()
    private val membershipService = mockk<MembershipService>()
    private val service = HouseholdSearchService(repository, membershipService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "viewer@example.com", "Viewer")
    private val criteria = HouseholdSearchCriteria()

    @Test
    fun `search denies a non-manager org member`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } throws
            ForbiddenException("MEMBERSHIP_MANAGEMENT_DENIED", "Only organization owners and administrators can manage members.")

        assertFailsWith<ForbiddenException> {
            service.search(orgId, criteria, currentUser, 0, 25)
        }
        verify(exactly = 0) { repository.search(any(), any(), any(), any()) }
    }

    @Test
    fun `count denies a non-manager org member`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } throws
            ForbiddenException("MEMBERSHIP_MANAGEMENT_DENIED", "Only organization owners and administrators can manage members.")

        assertFailsWith<ForbiddenException> {
            service.count(orgId, criteria, currentUser)
        }
        verify(exactly = 0) { repository.count(any(), any()) }
    }

    @Test
    fun `search allows a manager and delegates to the repository`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns mockk()
        every { repository.search(orgId, criteria, 0, 25) } returns emptyList()

        service.search(orgId, criteria, currentUser, 0, 25)

        verify(exactly = 1) { repository.search(orgId, criteria, 0, 25) }
    }
}
