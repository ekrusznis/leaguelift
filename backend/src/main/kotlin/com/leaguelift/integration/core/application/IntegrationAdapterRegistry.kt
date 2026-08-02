package com.leaguelift.integration.core.application

import com.leaguelift.integration.core.domain.IntegrationProvider
import org.springframework.stereotype.Component

@Component
class IntegrationAdapterRegistry(
    private val adapters: List<IntegrationAuthorizationAdapter>,
) {
    fun find(provider: IntegrationProvider): IntegrationAuthorizationAdapter? =
        adapters.firstOrNull { it.supports(provider) }
}
