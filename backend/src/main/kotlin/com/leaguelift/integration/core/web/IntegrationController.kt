package com.leaguelift.integration.core.web

import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.core.application.IntegrationCatalogService
import com.leaguelift.integration.core.application.IntegrationOAuthService
import com.leaguelift.integration.core.domain.IntegrationProvider
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class IntegrationController(
    private val catalogService: IntegrationCatalogService,
    private val oauthService: IntegrationOAuthService,
) {
    @GetMapping("/organizations/{organizationId}/integrations/catalog")
    fun organizationCatalog(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationCatalogResponse> =
        catalogService.listForOrganization(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/organizations/{organizationId}/integration-connections")
    fun organizationConnections(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationConnectionResponse> =
        oauthService.listOrganizationConnections(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/me/integrations/catalog")
    fun personalCatalog(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationCatalogResponse> =
        catalogService.listForUser(currentUser).map { it.toResponse() }

    @GetMapping("/me/integration-connections")
    fun personalConnections(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationConnectionResponse> =
        oauthService.listUserConnections(currentUser).map { it.toResponse() }

    @PostMapping("/organizations/{organizationId}/integrations/{provider}/oauth/start")
    fun startOrganizationAuthorization(
        @PathVariable organizationId: UUID,
        @PathVariable provider: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AuthorizationStartResponse =
        oauthService.startOrganizationAuthorization(organizationId, provider(provider), currentUser).toResponse()

    @PostMapping("/me/integrations/{provider}/oauth/start")
    fun startPersonalAuthorization(
        @PathVariable provider: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): AuthorizationStartResponse =
        oauthService.startUserAuthorization(provider(provider), currentUser).toResponse()

    /**
     * Provider redirects arrive without a LeagueLift bearer token. The one-time state
     * record binds the callback to the initiating user/organization and is atomically
     * consumed before any credential is stored.
     */
    @GetMapping("/integrations/oauth/{provider}/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): AuthorizationCallbackResponse {
        if (!error.isNullOrBlank()) {
            throw ValidationException("The provider did not authorize the connection. Start the connection again if needed.")
        }
        val connection = oauthService.completeAuthorization(
            provider(provider),
            state ?: throw ValidationException("The provider callback is missing authorization state."),
            code ?: throw ValidationException("The provider callback is missing an authorization code."),
        )
        return AuthorizationCallbackResponse(connection.toResponse())
    }

    @PostMapping("/organizations/{organizationId}/integration-connections/{connectionId}/refresh")
    fun refresh(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse =
        oauthService.refreshOrganizationConnection(organizationId, connectionId, currentUser).toResponse()

    @PostMapping("/organizations/{organizationId}/integration-connections/{connectionId}/revoke")
    fun revoke(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse =
        oauthService.revokeOrganizationConnection(organizationId, connectionId, currentUser).toResponse()

    @PostMapping("/organizations/{organizationId}/integration-connections/{connectionId}/health-check")
    fun healthCheck(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationHealthResponse =
        oauthService.checkOrganizationHealth(organizationId, connectionId, currentUser).toResponse()

    @DeleteMapping("/organizations/{organizationId}/integration-connections/{connectionId}")
    @ResponseStatus(HttpStatus.OK)
    fun disconnect(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse =
        oauthService.disconnectOrganizationConnection(organizationId, connectionId, currentUser).toResponse()

    private fun provider(value: String): IntegrationProvider =
        IntegrationProvider.entries.firstOrNull {
            it.name.equals(value.replace('-', '_'), ignoreCase = true) || it.configKey.equals(value, ignoreCase = true)
        } ?: throw ValidationException("Unknown integration provider.")
}
