package com.rally26.fundraising.web

import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.application.FundraisingSettingsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "fundraising", description = "Organization fundraising policy.")
class FundraisingSettingsController(
    private val fundraisingSettingsService: FundraisingSettingsService,
) {
    @GetMapping
    @Operation(
        summary = "Get fundraising approval policy",
        description = "Readable by active organization members; default policy requires owner approval.",
    )
    fun get(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingSettingsResponse = fundraisingSettingsService.get(organizationId, currentUser).toResponse()

    @PatchMapping
    @Operation(
        summary = "Update fundraising approval policy",
        description = "Owner-only. Controls whether non-owner activation requests require approval.",
    )
    fun update(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: UpdateFundraisingSettingsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FundraisingSettingsResponse =
        fundraisingSettingsService
            .update(organizationId, request.requireOwnerApproval, currentUser)
            .toResponse()
}
