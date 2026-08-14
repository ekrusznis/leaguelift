package com.rally26.fundraising.application

import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionListCriteria
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.fundraising.persistence.ContributionSearchRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ContributionSearchService(
    private val searchRepository: ContributionSearchRepository,
    private val contributionRepository: ContributionRepository,
    private val campaignService: CampaignService,
) {
    fun search(
        organizationId: UUID,
        campaignId: UUID,
        criteria: ContributionListCriteria,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Contribution> {
        campaignService.get(organizationId, campaignId, currentUser)
        return searchRepository
            .searchIds(organizationId, campaignId, criteria, offset, limit)
            .mapNotNull(contributionRepository::findById)
    }

    fun count(
        organizationId: UUID,
        campaignId: UUID,
        criteria: ContributionListCriteria,
        currentUser: CurrentUser,
    ): Long {
        campaignService.get(organizationId, campaignId, currentUser)
        return searchRepository.count(organizationId, campaignId, criteria)
    }
}
