package com.rally26.fundraising.web

import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.team.domain.Team
import com.rally26.team.persistence.TeamRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Joins team/org branding into the public campaign page the same way
 * [com.rally26.publicpage.web.PublicController]/`StorePublicController` already do — a
 * real fix for plain campaigns (which had zero branding on their public page before
 * Phase 42), not just something the box-pool template needed.
 */
@RestController
@RequestMapping("/api/v1/public/campaigns")
class CampaignPublicController(
    private val campaignService: CampaignService,
    private val contributionService: ContributionService,
    private val mediaAssignmentService: MediaAssignmentService,
    private val mediaReadService: MediaReadService,
    private val teamRepository: TeamRepository,
) {
    @GetMapping("/{slug}")
    fun getCampaign(
        @PathVariable slug: String,
    ): PublicCampaignResponse {
        val campaign = campaignService.getPublic(slug)
        val entityType = if (campaign.teamId != null) MediaEntityType.TEAM else MediaEntityType.ORGANIZATION
        val entityId = campaign.teamId ?: campaign.organizationId
        val logo = mediaAssignmentService.getActiveAssignment(entityType, entityId, MediaUsageSlot.LOGO)?.let(mediaReadService::describe)
        val cover = mediaAssignmentService.getActiveAssignment(entityType, entityId, MediaUsageSlot.COVER)?.let(mediaReadService::describe)
        val team = campaign.teamId?.let { teamRepository.findById(it, campaign.organizationId) }
        return campaign.toPublicResponse(
            raisedMinor = contributionService.getConfirmedTotal(campaign.id),
            logoUrl = logo?.url,
            coverUrl = cover?.url,
            primaryColor = team?.resolvedPrimaryColor ?: Team.DEFAULT_PRIMARY_COLOR,
            secondaryColor = team?.resolvedSecondaryColor ?: Team.DEFAULT_SECONDARY_COLOR,
        )
    }
}
