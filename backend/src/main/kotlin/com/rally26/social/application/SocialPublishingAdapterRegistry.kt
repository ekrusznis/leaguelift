package com.rally26.social.application

import com.rally26.integration.core.domain.IntegrationProvider
import org.springframework.stereotype.Component

/** Mirrors com.rally26.integration.core.application.IntegrationAdapterRegistry's pattern exactly. */
@Component
class SocialPublishingAdapterRegistry(
    private val adapters: List<SocialPublishingAdapter>,
) {
    fun find(provider: IntegrationProvider): SocialPublishingAdapter? = adapters.firstOrNull { it.supports(provider) }
}
