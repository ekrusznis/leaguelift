package com.leaguelift.integration.core.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.core.application.PlatformIntegrationReadinessService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/platform/integrations")
class PlatformIntegrationController(
    private val readinessService: PlatformIntegrationReadinessService,
) {
    @GetMapping("/providers")
    fun providers(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<PlatformIntegrationReadinessResponse> =
        readinessService.list(currentUser).map { it.toResponse() }
}
