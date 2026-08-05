package com.rally26.integration.core.application

import com.rally26.integration.core.domain.IntegrationProvider
import org.springframework.stereotype.Component

@Component
class IntegrationAdapterRegistry(
    private val adapters: List<IntegrationAuthorizationAdapter>,
) {
    fun find(provider: IntegrationProvider): IntegrationAuthorizationAdapter? = adapters.firstOrNull { it.supports(provider) }
}
