package com.rally26.team.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.team.application.TeamSearchService
import com.rally26.team.domain.TeamGenderCategory
import com.rally26.team.domain.TeamSearchCriteria
import com.rally26.team.domain.TeamSearchSort
import com.rally26.team.domain.TeamStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/teams")
class TeamSearchController(
    private val service: TeamSearchService,
) {
    @GetMapping("/search")
    fun search(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sport: String?,
        @RequestParam(required = false) season: String?,
        @RequestParam(required = false) genderCategory: TeamGenderCategory?,
        @RequestParam(required = false) status: TeamStatus?,
        @RequestParam(defaultValue = "NAME_ASC") sort: TeamSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<TeamResponse> {
        if (page < 0 || size !in 1..100) {
            throw ValidationException("Page must be zero or greater and size must be between 1 and 100.")
        }
        val criteria = TeamSearchCriteria(q, sport, season, genderCategory, status, sort)
        val items = service.search(organizationId, criteria, currentUser, page * size, size).map { it.toResponse() }
        val total = service.count(organizationId, criteria, currentUser)
        return PageResponse(items, page, size, total)
    }
}
