package com.rally26.fundraising.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/campaigns")
class CampaignController(
    private val campaignService: CampaignService,
    private val contributionService: ContributionService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<CampaignResponse> {
        val offset = page * size
        val items =
            campaignService
                .list(organizationId, currentUser, offset, size)
                .map { campaign ->
                    campaign.toResponse(
                        contributionService.getConfirmedTotal(campaign.id),
                        campaignService.permissionsFor(campaign, currentUser),
                    )
                }
        val total = campaignService.count(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateCampaignRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<CampaignResponse> {
        val campaign =
            campaignService.create(
                organizationId = organizationId,
                teamId = request.teamId,
                name = request.name,
                slug = request.slug,
                description = request.description,
                campaignType = request.campaignType,
                goalAmountMinor = request.goalAmountMinor,
                currency = request.currency,
                startDate = request.startDate,
                endDate = request.endDate,
                currentUser = currentUser,
                templateKey = request.templateKey,
                eventLocationName = request.eventLocationName,
                eventAddress = request.eventAddress,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            campaign.toResponse(
                raisedMinor = 0,
                permissions = campaignService.permissionsFor(campaign, currentUser),
            ),
        )
    }

    @GetMapping("/{campaignId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign = campaignService.get(organizationId, campaignId, currentUser)
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    @PatchMapping("/{campaignId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: UpdateCampaignRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign =
            campaignService.update(
                organizationId = organizationId,
                campaignId = campaignId,
                name = request.name,
                description = request.description,
                goalAmountMinor = request.goalAmountMinor,
                startDate = request.startDate,
                endDate = request.endDate,
                currentUser = currentUser,
                eventLocationName = request.eventLocationName,
                eventAddress = request.eventAddress,
            )
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    /** Legacy route retained for existing web clients; now follows the approval policy. */
    @PostMapping("/{campaignId}/publish")
    fun publish(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign = campaignService.publish(organizationId, campaignId, currentUser)
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    /** Preferred web/mobile route: activates immediately or moves to PENDING_APPROVAL. */
    @PostMapping("/{campaignId}/request-activation")
    fun requestActivation(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign = campaignService.requestActivation(organizationId, campaignId, currentUser)
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    @PostMapping("/{campaignId}/approve")
    fun approve(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign = campaignService.approve(organizationId, campaignId, currentUser)
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    @PostMapping("/{campaignId}/reject-approval")
    fun rejectApproval(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign = campaignService.rejectApproval(organizationId, campaignId, currentUser)
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    @PatchMapping("/{campaignId}/status")
    fun updateStatus(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @Valid @RequestBody request: UpdateCampaignStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignResponse {
        val campaign = campaignService.updateStatus(organizationId, campaignId, request.status, currentUser)
        return campaign.toResponse(
            contributionService.getConfirmedTotal(campaign.id),
            campaignService.permissionsFor(campaign, currentUser),
        )
    }

    @GetMapping("/{campaignId}/contributions")
    fun listContributions(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<ContributionResponse> {
        val offset = page * size
        val items = contributionService.listConfirmed(organizationId, campaignId, currentUser, offset, size).map { it.toResponse() }
        val total = contributionService.getConfirmedCount(campaignId)
        return PageResponse(items, page, size, total)
    }

    @PostMapping("/{campaignId}/contributions/{contributionId}/refund")
    fun refundContribution(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @PathVariable contributionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ContributionResponse = contributionService.refund(organizationId, contributionId, currentUser).toResponse()

    @GetMapping("/qr-code")
    fun getShareLinkQrCode(
        @PathVariable organizationId: UUID,
        @RequestParam url: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignShareLinkResponse = CampaignShareLinkResponse(url, campaignService.buildShareLink(organizationId, url, currentUser))
}
