package com.rally26.publicpage.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.publicpage.application.PublicPageService
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
@RequestMapping("/api/v1/organizations/{organizationId}/pages")
class PublicPageController(
    private val publicPageService: PublicPageService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<PublicPageResponse> {
        val offset = page * size
        val items = publicPageService.list(organizationId, currentUser, offset, size).map { it.toResponse() }
        val total = publicPageService.count(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreatePublicPageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<PublicPageResponse> {
        val publicPage =
            publicPageService.create(
                organizationId,
                request.pageType,
                request.entityId,
                request.slug,
                request.title,
                request.summary,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(publicPage.toResponse())
    }

    @GetMapping("/{pageId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable pageId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PublicPageResponse = publicPageService.get(organizationId, pageId, currentUser).toResponse()

    @PatchMapping("/{pageId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable pageId: UUID,
        @Valid @RequestBody request: UpdatePublicPageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PublicPageResponse =
        publicPageService
            .update(
                organizationId,
                pageId,
                request.title,
                request.slug,
                request.summary,
                currentUser,
            ).toResponse()

    @PostMapping("/{pageId}/publish")
    fun publish(
        @PathVariable organizationId: UUID,
        @PathVariable pageId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PublicPageResponse = publicPageService.publish(organizationId, pageId, currentUser).toResponse()

    @PostMapping("/{pageId}/unpublish")
    fun unpublish(
        @PathVariable organizationId: UUID,
        @PathVariable pageId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PublicPageResponse = publicPageService.unpublish(organizationId, pageId, currentUser).toResponse()
}
