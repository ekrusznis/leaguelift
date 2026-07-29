package com.leaguelift.store.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.store.application.StoreService
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
@RequestMapping("/api/v1/organizations/{organizationId}/stores")
class StoreController(private val storeService: StoreService) {

	@GetMapping
	fun list(
		@PathVariable organizationId: UUID,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): PageResponse<StoreResponse> {
		val offset = page * size
		val items = storeService.list(organizationId, currentUser, offset, size).map { it.toResponse() }
		val total = storeService.count(organizationId, currentUser)
		return PageResponse(items, page, size, total)
	}

	@PostMapping
	fun create(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: CreateStoreRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): ResponseEntity<StoreResponse> {
		val store = storeService.create(organizationId, request.teamId, request.name, request.slug, currentUser)
		return ResponseEntity.status(HttpStatus.CREATED).body(store.toResponse())
	}

	@GetMapping("/{storeId}")
	fun get(
		@PathVariable organizationId: UUID,
		@PathVariable storeId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): StoreResponse = storeService.get(organizationId, storeId, currentUser).toResponse()

	@PatchMapping("/{storeId}/status")
	fun updateStatus(
		@PathVariable organizationId: UUID,
		@PathVariable storeId: UUID,
		@Valid @RequestBody request: UpdateStoreStatusRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): StoreResponse = storeService.updateStatus(organizationId, storeId, request.status, currentUser).toResponse()
}
