package com.rally26.integration.core.web

import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationSyncService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class IntegrationSyncController(private val service: IntegrationSyncService) {
    @GetMapping("/organizations/{organizationId}/integration-sync-runs")
    fun organizationRuns(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationSyncRunResponse> = service.listOrganization(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/me/integration-sync-runs")
    fun personalRuns(@AuthenticationPrincipal currentUser: CurrentUser): List<IntegrationSyncRunResponse> =
        service.listPersonal(currentUser).map { it.toResponse() }

    @GetMapping("/platform/integrations/sync-runs")
    fun platformRuns(@AuthenticationPrincipal currentUser: CurrentUser): List<IntegrationSyncRunResponse> =
        service.listPlatform(currentUser).map { it.toResponse() }

    @GetMapping("/integration-sync-runs/{runId}/issues")
    fun issues(
        @PathVariable runId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationSyncIssueResponse> = service.issues(runId, currentUser).map { it.toResponse() }
}
