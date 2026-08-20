package com.rally26.settings.web

import com.rally26.common.web.CurrentUser
import com.rally26.settings.application.UserPreferenceService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/preferences")
class UserPreferenceController(
    private val service: UserPreferenceService,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): UserPreferenceResponse = service.get(currentUser.userId).toResponse()

    @PatchMapping
    fun update(
        @RequestBody request: UpdateUserPreferenceRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): UserPreferenceResponse =
        service
            .updateAppearance(
                userId = currentUser.userId,
                appearance = request.appearance,
            ).toResponse()

    @PatchMapping("/media-visibility")
    fun updateDefaultMediaVisibility(
        @RequestBody request: UpdateDefaultMediaVisibilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): UserPreferenceResponse =
        service
            .updateDefaultMediaVisibility(
                userId = currentUser.userId,
                defaultMediaVisibility = request.defaultMediaVisibility,
            ).toResponse()
}
