package com.leaguelift.config

import com.leaguelift.integration.core.domain.IntegrationProvider
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Phase 19 provider-neutral integration configuration. Platform provider secrets
 * remain in their existing properties; this object is only for organization/user
 * connections and the shared OAuth/credential framework.
 */
@ConfigurationProperties(prefix = "leaguelift.integrations")
data class IntegrationProperties(
    val encryptionKey: String = "",
    val encryptionKeyVersion: Int = 1,
    val oauthStateTtlMinutes: Long = 10,
    val oauthCallbackBaseUrl: String = "http://localhost:8080/api/v1/integrations/oauth",
    val stubMode: Boolean = false,
    val providers: Map<String, IntegrationProviderRuntimeProperties> = emptyMap(),
) {
    fun provider(provider: IntegrationProvider): IntegrationProviderRuntimeProperties =
        providers[provider.configKey] ?: providers[provider.name] ?: IntegrationProviderRuntimeProperties()
}

data class IntegrationProviderRuntimeProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val authorizationUri: String = "",
    val tokenUri: String = "",
    val revocationUri: String = "",
    val scopes: List<String> = emptyList(),
)
