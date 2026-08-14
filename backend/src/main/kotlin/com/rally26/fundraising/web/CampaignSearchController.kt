package com.rally26.fundraising.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.fundraising.application.CampaignSearchService
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import com.rally26.fundraising.domain.CampaignListCriteria
import com.rally26.fundraising.domain.CampaignListSort
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.FundraiserTemplateKey
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/campaigns/search")
class CampaignSearchController(
    private val searchService: CampaignSearchService,
    private val campaignService: CampaignService,
    private val contributionService: ContributionService,
) {
    @GetMapping
    fun search(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) campaignType: String?,
        @RequestParam(required = false) templateKey: String?,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(defaultValue = "NEWEST") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<CampaignResponse> {
        validatePage(page, size)
        val criteria =
            CampaignListCriteria(
                keyword = q,
                status = enumValueOrNull<CampaignStatus>(status, "campaign status"),
                campaignType = enumValueOrNull<CampaignType>(campaignType, "campaign type"),
                templateKey = enumValueOrNull<FundraiserTemplateKey>(templateKey, "fundraiser template"),
                teamId = teamId,
                sort = enumValue<CampaignListSort>(sort, "campaign sort"),
            )
        val items =
            searchService
                .search(organizationId, criteria, currentUser, page * size, size)
                .map { campaign ->
                    campaign.toResponse(
                        contributionService.getConfirmedTotal(campaign.id),
                        campaignService.permissionsFor(campaign, currentUser),
                    )
                }
        return PageResponse(items, page, size, searchService.count(organizationId, criteria, currentUser))
    }

    private fun validatePage(
        page: Int,
        size: Int,
    ) {
        if (page < 0 || size !in 1..100) {
            throw ValidationException("Page must be zero or greater and size must be between 1 and 100.")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(
        value: String,
        label: String,
    ): T =
        runCatching { enumValueOf<T>(value.uppercase()) }
            .getOrElse { throw ValidationException("Unknown $label.") }

    private inline fun <reified T : Enum<T>> enumValueOrNull(
        value: String?,
        label: String,
    ): T? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { enumValue<T>(it, label) }
}
