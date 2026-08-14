package com.rally26.fundraising.domain

import java.util.UUID

data class CampaignListCriteria(
    val keyword: String? = null,
    val status: CampaignStatus? = null,
    val campaignType: CampaignType? = null,
    val templateKey: FundraiserTemplateKey? = null,
    val teamId: UUID? = null,
    val sort: CampaignListSort = CampaignListSort.NEWEST,
)

enum class CampaignListSort {
    NEWEST,
    NAME_ASC,
    START_DATE_ASC,
    END_DATE_ASC,
    RAISED_DESC,
    GOAL_DESC,
}
