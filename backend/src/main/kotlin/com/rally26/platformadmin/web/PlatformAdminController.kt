package com.rally26.platformadmin.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.platformadmin.application.PlatformAdminConsoleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/platform/admin")
class PlatformAdminController(
    private val service: PlatformAdminConsoleService,
) {
    @GetMapping("/organizations")
    fun listOrganizations(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<PlatformOrganizationListItemResponse> {
        val result = service.listOrganizations(currentUser, query, status, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @GetMapping("/organizations/{organizationId}")
    fun getOrganization(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PlatformOrganizationDetailResponse = service.getOrganization(currentUser, organizationId).toResponse()

    @GetMapping("/users")
    fun listUsers(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<PlatformUserListItemResponse> {
        val result = service.listUsers(currentUser, query, status, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @GetMapping("/swag-shop/products")
    fun listSwagShopProducts(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) organizationId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<PlatformSwagShopProductListItemResponse> {
        val result = service.listSwagShopProducts(currentUser, query, status, organizationId, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @GetMapping("/support-access")
    fun listSupportAccess(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<PlatformSupportAccessListItemResponse> {
        val result = service.listSupportAccess(currentUser, status, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping("/organizations/{organizationId}/support-access")
    @ResponseStatus(HttpStatus.CREATED)
    fun startSupportAccess(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: StartPlatformSupportAccessRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PlatformSupportAccessResponse = service.startSupportAccess(currentUser, organizationId, request.reason).toResponse()

    @GetMapping("/support-access/current")
    fun currentSupportAccess(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PlatformSupportAccessResponse? = service.currentSupportAccess(currentUser)?.toResponse()

    @DeleteMapping("/support-access/{accessId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun endSupportAccess(
        @PathVariable accessId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.endSupportAccess(currentUser, accessId)
}
