package com.leaguelift.publicpage.web

import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaReadService
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.publicpage.application.PublicPageService
import com.leaguelift.publicpage.domain.PageType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public")
class PublicController(
    private val publicPageService: PublicPageService,
    private val mediaAssignmentService: MediaAssignmentService,
    private val mediaReadService: MediaReadService,
) {

    @GetMapping("/pages/{slug}")
    fun getPage(@PathVariable slug: String): PublicPageResponse {
        val page = publicPageService.getPublic(slug)
        val entityType = when (page.pageType) {
            PageType.ORGANIZATION -> MediaEntityType.ORGANIZATION
            PageType.TEAM -> MediaEntityType.TEAM
            PageType.TOURNAMENT -> MediaEntityType.TOURNAMENT
        }
        val logo = mediaAssignmentService.getActiveAssignment(entityType, page.entityId, MediaUsageSlot.LOGO)
            ?.let(mediaReadService::describe)
        val cover = mediaAssignmentService.getActiveAssignment(entityType, page.entityId, MediaUsageSlot.COVER)
            ?.let(mediaReadService::describe)
        return page.toResponse(logo, cover)
    }
}
