package com.leaguelift.search.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.search.application.SearchService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class SearchController(private val searchService: SearchService) {

	@GetMapping("/organizations/{organizationId}/search")
	fun searchOrganization(
		@PathVariable organizationId: UUID,
		@RequestParam q: String,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SearchResponse = SearchResponse(searchService.searchOrganization(organizationId, q, currentUser).map { it.toResponse() })

	@GetMapping("/platform/search")
	fun searchPlatform(@RequestParam q: String, @AuthenticationPrincipal currentUser: CurrentUser): SearchResponse =
		SearchResponse(searchService.searchPlatform(q, currentUser).map { it.toResponse() })
}
