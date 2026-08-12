package com.rally26.integration.core.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.config.FrontendProperties
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.domain.IntegrationProvider
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
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class IntegrationController(
    private val catalogService: IntegrationCatalogService,
    private val oauthService: IntegrationOAuthService,
    private val frontendProperties: FrontendProperties,
) {
    @GetMapping("/organizations/{organizationId}/integrations/catalog")
    fun organizationCatalog(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationCatalogResponse> = catalogService.listForOrganization(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/organizations/{organizationId}/integration-connections")
    fun organizationConnections(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationConnectionResponse> = oauthService.listOrganizationConnections(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/me/integrations/catalog")
    fun personalCatalog(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationCatalogResponse> = catalogService.listForUser(currentUser).map { it.toResponse() }

    @GetMapping("/me/integration-connections")
    fun personalConnections(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<IntegrationConnectionResponse> = oauthService.listUserConnections(currentUser).map { it.toResponse() }

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
    ): AuthorizationStartResponse = oauthService.startUserAuthorization(provider(provider), currentUser).toResponse()

    /**
     * Provider redirects arrive without a Rally26 bearer token. The one-time state
     * record binds the callback to the initiating user/organization and is atomically
     * consumed before any credential is stored.
     *
     * [organizationGrant] (2026-08-12): SportsEngine's documented "Organization
     * Authorization Flow" redirects back with an `organization_grant` query param
     * (the approved organization's id) instead of the standard OAuth2 `code` param
     * every other provider here uses — see `SportsEngineAuthorizationAdapter`'s
     * class doc. Rather than adding a provider-specific callback path, this generic
     * endpoint accepts either and forwards whichever is present through the same
     * `completeAuthorization` call unchanged.
     */
    @GetMapping("/integrations/oauth/{provider}/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(name = "organization_grant", required = false) organizationGrant: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): RedirectView {
        val resolvedProvider = provider(provider)
        val effectiveCode = code ?: organizationGrant
        var organizationId: UUID? = null
        val status =
            try {
                if (!error.isNullOrBlank()) {
                    organizationId = oauthService.failAuthorization(resolvedProvider, state, error)?.organizationId
                    "denied"
                } else {
                    organizationId =
                        oauthService
                            .completeAuthorization(
                                resolvedProvider,
                                state ?: throw ValidationException("The provider callback is missing authorization state."),
                                effectiveCode ?: throw ValidationException("The provider callback is missing an authorization code."),
                            ).organizationId
                    "connected"
                }
            } catch (_: Exception) {
                "error"
            }
        val returnPath = organizationId?.let { "/app/organizations/$it/integrations" } ?: "/app/integrations"
        val target =
            UriComponentsBuilder
                .fromUriString(frontendProperties.baseUrl)
                .path(returnPath)
                .queryParam("integration", status)
                .queryParam("provider", resolvedProvider.configKey)
                .build(true)
                .toUriString()
        return RedirectView(target)
    }

    @PostMapping("/organizations/{organizationId}/integration-connections/{connectionId}/refresh")
    fun refresh(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse = oauthService.refreshOrganizationConnection(organizationId, connectionId, currentUser).toResponse()

    @PostMapping("/organizations/{organizationId}/integration-connections/{connectionId}/revoke")
    fun revoke(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse = oauthService.revokeOrganizationConnection(organizationId, connectionId, currentUser).toResponse()

    @PostMapping("/organizations/{organizationId}/integration-connections/{connectionId}/health-check")
    fun healthCheck(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationHealthResponse = oauthService.checkOrganizationHealth(organizationId, connectionId, currentUser).toResponse()

    @DeleteMapping("/organizations/{organizationId}/integration-connections/{connectionId}")
    @ResponseStatus(HttpStatus.OK)
    fun disconnect(
        @PathVariable organizationId: UUID,
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse = oauthService.disconnectOrganizationConnection(organizationId, connectionId, currentUser).toResponse()

    @PostMapping("/me/integration-connections/{connectionId}/refresh")
    fun refreshPersonal(
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse = oauthService.refreshUserConnection(connectionId, currentUser).toResponse()

    @PostMapping("/me/integration-connections/{connectionId}/revoke")
    fun revokePersonal(
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse = oauthService.revokeUserConnection(connectionId, currentUser).toResponse()

    @PostMapping("/me/integration-connections/{connectionId}/health-check")
    fun healthCheckPersonal(
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationHealthResponse = oauthService.checkUserHealth(connectionId, currentUser).toResponse()

    @DeleteMapping("/me/integration-connections/{connectionId}")
    @ResponseStatus(HttpStatus.OK)
    fun disconnectPersonal(
        @PathVariable connectionId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): IntegrationConnectionResponse = oauthService.disconnectUserConnection(connectionId, currentUser).toResponse()

    private fun provider(value: String): IntegrationProvider =
        IntegrationProvider.entries.firstOrNull {
            it.name.equals(value.replace('-', '_'), ignoreCase = true) || it.configKey.equals(value, ignoreCase = true)
        } ?: throw ValidationException("Unknown integration provider.")
}
