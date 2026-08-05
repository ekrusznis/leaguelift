package com.rally26.integration.core.application

import com.rally26.config.IntegrationProperties
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI

/** Fails closed when an integration is enabled with unsafe or incomplete runtime configuration. */
@Component
class IntegrationRuntimeGuard(
    private val properties: IntegrationProperties,
    private val credentialCipher: CredentialCipher,
    private val environment: Environment,
) : InitializingBean {
    override fun afterPropertiesSet() {
        val productionLike = environment.activeProfiles.any { it.equals("prod", true) || it.equals("staging", true) }
        if (productionLike && properties.stubMode) {
            throw IllegalStateException("Integration stub mode cannot be enabled in staging or production.")
        }
        require(properties.oauthStateTtlMinutes in 5..30) {
            "rally26.integrations.oauth-state-ttl-minutes must be between 5 and 30."
        }

        val enabledProviders = properties.providers.filterValues { it.enabled }
        if (enabledProviders.isNotEmpty() && !credentialCipher.validateConfiguredKey()) {
            throw IllegalStateException("INTEGRATION_ENCRYPTION_KEY must be a base64-encoded 32-byte key before a provider is enabled.")
        }

        if (productionLike && enabledProviders.isNotEmpty()) {
            val callback = runCatching { URI(properties.oauthCallbackBaseUrl) }.getOrNull()
            require(callback != null && callback.scheme.equals("https", true) && callback.host != null) {
                "Integration OAuth callback base URL must be an absolute HTTPS URL in staging/production."
            }
        }

        if (!properties.stubMode) {
            enabledProviders.forEach { (key, provider) ->
                require(
                    provider.clientId.isNotBlank() &&
                        provider.clientSecret.isNotBlank() &&
                        provider.authorizationUri.isNotBlank() &&
                        provider.tokenUri.isNotBlank(),
                ) {
                    "Enabled integration provider '$key' is missing its OAuth client or endpoint configuration."
                }
            }
        }
    }
}
