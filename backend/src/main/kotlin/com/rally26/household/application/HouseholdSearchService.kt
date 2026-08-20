package com.rally26.household.application

import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdSearchCriteria
import com.rally26.household.persistence.HouseholdSearchRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Backs the org-manager-only "Households & Athletes" page (`Capabilities.ORG_MANAGE`,
 * OWNER/ADMINISTRATOR only in the frontend). Security review (2026-08): previously only
 * required [MembershipService.requireActiveMembership] — any active member of any role
 * could pull every household in the org via direct API call. See [com.rally26.search.application.SearchService]'s
 * doc comment for the same finding on the global search feature.
 */
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
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.search(organizationId, criteria, offset, limit)
    }

    fun count(
        organizationId: UUID,
        criteria: HouseholdSearchCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.count(organizationId, criteria)
    }
}
