package com.rally26.fundraising.web

import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.FundraisingSettingsService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/fundraising/settings")
class FundraisingSettingsController(
    private val fundraisingSettingsService: FundraisingSettingsService,
) {
    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingSettingsResponse = fundraisingSettingsService.get(organizationId, currentUser).toResponse()

    @PatchMapping
    fun update(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: UpdateFundraisingSettingsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingSettingsResponse =
        fundraisingSettingsService
            .update(organizationId, request.requireOwnerApproval, currentUser)
            .toResponse()
}
