package com.rally26.social.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.social.application.SocialDraftService
import com.rally26.social.application.SocialPublishService
import com.rally26.social.domain.SocialDraftSourceType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/social")
class SocialDraftController(
    private val draftService: SocialDraftService,
    private val publishService: SocialPublishService,
) {
    @PostMapping("/drafts")
    fun create(
        @RequestBody request: CreateSocialDraftRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SocialPostDraftResponse {
        val sourceType =
            SocialDraftSourceType.entries.firstOrNull { it.name.equals(request.sourceType, ignoreCase = true) }
                ?: throw ValidationException("Unknown social draft source type.")
        return draftService.createDraft(request.organizationId, sourceType, request.sourceId, currentUser).toResponse()
    }

    @GetMapping("/drafts/{id}")
    fun get(
        @PathVariable id: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SocialPostDraftResponse = draftService.findForUser(id, currentUser).toResponse()

    @PatchMapping("/drafts/{id}")
    fun updateCaption(
        @PathVariable id: UUID,
        @RequestBody request: UpdateSocialDraftCaptionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SocialPostDraftResponse = draftService.updateCaption(id, request.caption, currentUser).toResponse()

    @PostMapping("/drafts/{id}/publish")
    fun publish(
        @PathVariable id: UUID,
        @RequestBody request: PublishSocialDraftRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): SocialPublishingHistoryResponse {
        val provider =
            IntegrationProvider.entries.firstOrNull { it.name.equals(request.provider, ignoreCase = true) }
                ?: throw ValidationException("Unknown provider.")
        return publishService.publish(id, provider, currentUser).toResponse()
    }

    @GetMapping("/publishing-history")
    fun history(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<SocialPublishingHistoryResponse> = publishService.history(currentUser).map { it.toResponse() }
}
