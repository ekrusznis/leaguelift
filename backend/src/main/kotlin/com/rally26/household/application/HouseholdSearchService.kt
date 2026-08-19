package com.rally26.household.application

import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdSearchCriteria
import com.rally26.household.persistence.HouseholdSearchRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class HouseholdSearchService(
    private val repository: HouseholdSearchRepository,
    private val membershipService: MembershipService,
) {
    fun search(
        organizationId: UUID,
        criteria: HouseholdSearchCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Household> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.search(organizationId, criteria, offset, limit)
    }

    fun count(
        organizationId: UUID,
        criteria: HouseholdSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.count(organizationId, criteria)
    }
}
