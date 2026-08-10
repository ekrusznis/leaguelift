package com.rally26.publicpage.web

import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaReadService
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.publicpage.application.PublicPageService
import com.rally26.publicpage.domain.PageType
import com.rally26.team.domain.Team
import com.rally26.team.persistence.TeamRepository
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
    private val teamRepository: TeamRepository,
) {
    @GetMapping("/pages/{slug}")
    fun getPage(
        @PathVariable slug: String,
    ): PublicPageResponse {
        val page = publicPageService.getPublic(slug)
        val entityType =
            when (page.pageType) {
                PageType.ORGANIZATION -> MediaEntityType.ORGANIZATION
                PageType.TEAM -> MediaEntityType.TEAM
                PageType.TOURNAMENT -> MediaEntityType.TOURNAMENT
            }
        val logo =
            mediaAssignmentService
                .getActiveAssignment(entityType, page.entityId, MediaUsageSlot.LOGO)
                ?.let(mediaReadService::describe)
        val cover =
            mediaAssignmentService
                .getActiveAssignment(entityType, page.entityId, MediaUsageSlot.COVER)
                ?.let(mediaReadService::describe)
        val team = if (page.pageType == PageType.TEAM) teamRepository.findById(page.entityId, page.organizationId) else null
        return page.toResponse(
            logo,
            cover,
            primaryColor = team?.resolvedPrimaryColor ?: Team.DEFAULT_PRIMARY_COLOR,
            secondaryColor = team?.resolvedSecondaryColor ?: Team.DEFAULT_SECONDARY_COLOR,
        )
    }
}
