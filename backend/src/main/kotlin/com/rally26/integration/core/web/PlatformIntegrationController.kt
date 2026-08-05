package com.rally26.integration.core.web

import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.PlatformIntegrationReadinessService
import com.rally26.integration.core.application.PlatformProviderContractService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/platform/integrations")
class PlatformIntegrationController(
    private val readinessService: PlatformIntegrationReadinessService,
    private val contractService: PlatformProviderContractService,
) {
    @GetMapping("/providers")
    fun providers(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<PlatformIntegrationReadinessResponse> = readinessService.list(currentUser).map { it.toResponse() }

    @GetMapping("/provider-contracts")
    fun providerContracts(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<PlatformProviderContractResponse> = contractService.list(currentUser).map { it.toResponse() }
}
