package com.rally26.search.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.search.domain.SearchHit
import com.rally26.search.persistence.SearchRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val MIN_QUERY_LENGTH = 2
private const val RESULTS_PER_CATEGORY = 8

/**
 * Global search (DESIGN-DOC.md section 13, Phase 7 completion): teams/participants/
 * households within one organization for an org member, or organizations platform-
 * wide for a platform administrator. Deliberately not offered to Parent/Coach/
 * Athlete dashboards — an org-wide search of every household would cross the
 * household-privacy boundary DESIGN-DOC.md section 1/10.2 draws for a guardian
 * ("cannot view unrelated households"); this is scoped to the same audience that
 * already sees every team/household in the org via the organization detail page.
 */
@Service
class SearchService(
    private val searchRepository: SearchRepository,
    private val membershipService: MembershipService,
) {
    fun searchOrganization(
        organizationId: UUID,
        query: String,
        currentUser: CurrentUser,
    ): List<SearchHit> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return searchRepository.searchTeams(organizationId, query, RESULTS_PER_CATEGORY) +
            searchRepository.searchParticipants(organizationId, query, RESULTS_PER_CATEGORY) +
            searchRepository.searchHouseholds(organizationId, query, RESULTS_PER_CATEGORY)
    }

    fun searchPlatform(
        query: String,
        currentUser: CurrentUser,
    ): List<SearchHit> {
        if (!currentUser.platformAdministrator) {
            throw ForbiddenException("PLATFORM_ACCESS_DENIED", "Only a platform administrator can search across organizations.")
        }
        if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        return searchRepository.searchOrganizations(query, RESULTS_PER_CATEGORY)
    }
}
