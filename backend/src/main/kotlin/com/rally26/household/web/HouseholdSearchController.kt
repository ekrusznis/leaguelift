package com.rally26.household.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.household.application.HouseholdSearchService
import com.rally26.household.domain.HouseholdSearchCriteria
import com.rally26.household.domain.HouseholdSearchSort
import com.rally26.household.domain.HouseholdStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/households")
class HouseholdSearchController(
    private val service: HouseholdSearchService,
) {
    @GetMapping("/search")
    fun search(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: HouseholdStatus?,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(defaultValue = "NAME_ASC") sort: HouseholdSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<HouseholdResponse> {
        if (page < 0 || size !in 1..100) {
            throw ValidationException("Page must be zero or greater and size must be between 1 and 100.")
        }
        val criteria = HouseholdSearchCriteria(q, status, teamId, sort)
        val items = service.search(organizationId, criteria, currentUser, page * size, size).map { it.toResponse() }
        val total = service.count(organizationId, criteria, currentUser)
        return PageResponse(items, page, size, total)
    }
}
