package com.rally26.team.application

import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamSearchCriteria
import com.rally26.team.persistence.TeamSearchRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Backs the org-manager-only "Teams" page (`Capabilities.ORG_TEAM_MANAGE`/`ORG_MANAGE`,
 * OWNER/ADMINISTRATOR only in the frontend). Security review (2026-08): previously only
 * required [MembershipService.requireActiveMembership] — any active member of any role
 * could pull every team in the org via direct API call. See [com.rally26.search.application.SearchService]'s
 * doc comment for the same finding on the global search feature.
 */
@Service
class TeamSearchService(
    private val repository: TeamSearchRepository,
    private val membershipService: MembershipService,
) {
    fun search(
        organizationId: UUID,
        criteria: TeamSearchCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Team> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.search(organizationId, criteria, offset, limit)
    }

    fun count(
        organizationId: UUID,
        criteria: TeamSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.count(organizationId, criteria)
    }
}
