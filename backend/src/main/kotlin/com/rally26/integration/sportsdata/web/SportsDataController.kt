package com.rally26.integration.sportsdata.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.sportsdata.application.SportsDataService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/integrations/sports-data")
class SportsDataController(
    private val service: SportsDataService,
) {
    @GetMapping
    fun overview(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.overview(organizationId, currentUser).toResponse()

    @PostMapping("/connections/{connectionId}/preview")
    fun previewConnected(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.previewConnected(organizationId, connectionId, currentUser).toResponse()

    @PostMapping("/file-preview")
    fun previewFile(
        @PathVariable organizationId: UUID,
        @RequestBody request: SportsDataFilePreviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service
        .previewFile(
            organizationId,
            provider(request.provider),
            request.records.map { it.toDomain() },
            currentUser,
        ).toResponse()

    @GetMapping("/previews/{runId}/issues")
    fun issues(
        @PathVariable organizationId: UUID,
        @PathVariable runId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.issues(organizationId, runId, currentUser).map { it.toResponse() }

    private fun provider(value: String): IntegrationProvider =
        runCatching { IntegrationProvider.valueOf(value.uppercase()) }
            .getOrElse { throw ValidationException("Unknown sports-data provider.") }
}
