package com.rally26.team.application

import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamSearchCriteria
import com.rally26.team.persistence.TeamSearchRepository
import org.springframework.stereotype.Service
import java.util.UUID

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
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.search(organizationId, criteria, offset, limit)
    }

    fun count(
        organizationId: UUID,
        criteria: TeamSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.count(organizationId, criteria)
    }
}
