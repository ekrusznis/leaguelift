package com.rally26.fundraising.application

import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignListCriteria
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.CampaignSearchRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CampaignSearchService(
    private val searchRepository: CampaignSearchRepository,
    private val campaignRepository: CampaignRepository,
    private val membershipService: MembershipService,
) {
    fun search(
        organizationId: UUID,
        criteria: CampaignListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Campaign> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return searchRepository
            .searchIds(organizationId, criteria, offset, limit)
            .mapNotNull { campaignRepository.findById(it, organizationId) }
    }

    fun count(
        organizationId: UUID,
        criteria: CampaignListCriteria,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return searchRepository.count(organizationId, criteria)
    }
}
