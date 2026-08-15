package com.rally26.fundraising.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.fundraising.application.CampaignService
import com.rally26.fundraising.application.ContributionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "fundraising", description = "Fundraiser creation, approval, lifecycle, sharing, contributions, and closeout.")
class CampaignController(
    private val campaignService: CampaignService,
    private val contributionService: ContributionService,
) {
    @GetMapping
    @Operation(
        summary = "List fundraising campaigns",
        description = "Returns campaigns visible to the current member plus backend-computed mutation permissions.",
    )
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
    @Operation(
        summary = "Create fundraiser draft",
        description = "Creates a DRAFT fundraiser within the caller's authorized organization/team scope.",
    )
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
    @Operation(
        summary = "Get fundraiser",
        description = "Returns campaign state, totals, creator/approval metadata, payment availability, and caller permissions.",
    )
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
    @Operation(
        summary = "Edit fundraiser",
        description = "Creator/owner authorization is backend-enforced; editing pending work returns it to draft for re-review.",
    )
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
    @Operation(
        summary = "Legacy activation route",
        description = "Compatibility alias for the approval-aware activation workflow. Prefer request-activation in new clients.",
    )
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
    @Operation(
        summary = "Request fundraiser activation",
        description = "Transitions to PENDING_APPROVAL, SCHEDULED, or ACTIVE according to owner policy and start date.",
    )
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
    @Operation(
        summary = "Approve fundraiser",
        description = "Owner-only approval. Future-start fundraisers become SCHEDULED; otherwise ACTIVE.",
    )
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
    @Operation(
        summary = "Return fundraiser to draft",
        description = "Owner-only. Returns PENDING_APPROVAL work to DRAFT for creator changes and resubmission.",
    )
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
    @Operation(
        summary = "Close or archive fundraiser",
        description = "Owner closeout route. Approval/activation lifecycle states use their dedicated endpoints.",
    )
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
    @Operation(summary = "List confirmed fundraiser contributions")
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
    @Operation(summary = "Refund a confirmed online contribution")
    fun refundContribution(
        @PathVariable organizationId: UUID,
        @PathVariable campaignId: UUID,
        @PathVariable contributionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ContributionResponse = contributionService.refund(organizationId, contributionId, currentUser).toResponse()

    @GetMapping("/qr-code")
    @Operation(
        summary = "Generate campaign share QR code",
        description = "Returns the public URL plus a PNG data-URI QR code; no temporary payment-session URL is encoded.",
    )
    fun getShareLinkQrCode(
        @PathVariable organizationId: UUID,
        @RequestParam url: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): CampaignShareLinkResponse = CampaignShareLinkResponse(url, campaignService.buildShareLink(organizationId, url, currentUser))
}
