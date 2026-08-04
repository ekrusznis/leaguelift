package com.rally26.integration.core.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.web.CurrentUser
import com.rally26.config.EmailProviderProperties
import com.rally26.config.PrintifyProperties
import com.rally26.config.ResendProperties
import com.rally26.config.SmsProviderProperties
import com.rally26.config.SpacesProperties
import com.rally26.config.StripeProperties
import com.rally26.config.TwilioProperties
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.persistence.IntegrationProviderRepository
import org.springframework.stereotype.Service

enum class PlatformIntegrationConfigurationStatus { CONFIGURED, PARTIAL, NOT_CONFIGURED, BUILT_IN }

data class PlatformIntegrationConfigurationCheck(
    val label: String,
    val configured: Boolean,
)

data class PlatformIntegrationReadiness(
    val provider: IntegrationProvider,
    val displayName: String,
    val category: String,
    val status: PlatformIntegrationConfigurationStatus,
    val mode: String,
    val summary: String,
    val checks: List<PlatformIntegrationConfigurationCheck>,
)

/**
 * Sanitized platform-provider configuration readiness. This intentionally does not
 * call a vendor or label configuration as a successful health check; Phase 20 owns
 * credentialed probes and contract verification.
 */
@Service
class PlatformIntegrationReadinessService(
    private val providerRepository: IntegrationProviderRepository,
    private val authorizationService: AuthorizationService,
    private val stripe: StripeProperties,
    private val printify: PrintifyProperties,
    private val emailProvider: EmailProviderProperties,
    private val resend: ResendProperties,
    private val smsProvider: SmsProviderProperties,
    private val twilio: TwilioProperties,
    private val spaces: SpacesProperties,
) {
    fun list(currentUser: CurrentUser): List<PlatformIntegrationReadiness> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW)
        val definitions = providerRepository.list(IntegrationOwnerType.PLATFORM, customerVisibleOnly = false)
            .associateBy { it.provider }
        return listOf(
            readiness(
                IntegrationProvider.STRIPE,
                "Platform secret + signed webhook",
                listOf(check("Secret key", stripe.secretKey), check("Webhook secret", stripe.webhookSecret)),
                "Payment and Stripe Connect configuration is managed only by Rally26.",
            ),
            readiness(
                IntegrationProvider.PRINTIFY,
                "Platform API token",
                listOf(check("API token", printify.apiToken), check("Shop ID", printify.shopId)),
                "Catalog and fulfillment credentials are managed only by Rally26.",
            ),
            readiness(
                IntegrationProvider.RESEND,
                "${emailProvider.provider.lowercase()} email adapter",
                listOf(
                    PlatformIntegrationConfigurationCheck("Resend adapter selected", emailProvider.provider.equals("resend", true)),
                    check("API key", resend.apiKey),
                    check("From address", resend.fromAddress),
                ),
                if (emailProvider.provider.equals("resend", true)) "Transactional email is configured for Resend when all checks pass." else "The logging email adapter is selected; no live Resend delivery is claimed.",
            ),
            readiness(
                IntegrationProvider.TWILIO,
                "${smsProvider.provider.lowercase()} SMS adapter",
                listOf(
                    PlatformIntegrationConfigurationCheck("Twilio adapter selected", smsProvider.provider.equals("twilio", true)),
                    check("Account SID", twilio.accountSid),
                    check("Auth token", twilio.authToken),
                    check("From number", twilio.fromNumber),
                ),
                if (smsProvider.provider.equals("twilio", true)) "Transactional SMS is configured for Twilio when all checks pass." else "The logging SMS adapter is selected; no live Twilio delivery is claimed.",
            ),
            readiness(
                IntegrationProvider.DIGITALOCEAN_SPACES,
                "S3-compatible storage adapter",
                listOf(
                    check("Endpoint", spaces.endpoint),
                    check("Access key", spaces.accessKey),
                    check("Secret key", spaces.secretKey),
                    check("Bucket", spaces.bucket),
                    check("Region", spaces.region),
                ),
                "Storage configuration is sanitized; permanent credentials are never returned.",
            ),
            PlatformIntegrationReadiness(
                provider = IntegrationProvider.GOOGLE_MAPS,
                displayName = definitions[IntegrationProvider.GOOGLE_MAPS]?.displayName ?: "Google Maps",
                category = definitions[IntegrationProvider.GOOGLE_MAPS]?.category?.name ?: "MAPS",
                status = PlatformIntegrationConfigurationStatus.BUILT_IN,
                mode = "Keyless directions links",
                summary = "Safe external directions links remain available without a Google Maps API credential. Richer APIs are not enabled.",
                checks = listOf(PlatformIntegrationConfigurationCheck("Keyless directions available", true)),
            ),
        ).map { item ->
            val definition = definitions[item.provider]
            if (definition == null) item else item.copy(displayName = definition.displayName, category = definition.category.name)
        }
    }

    private fun readiness(
        provider: IntegrationProvider,
        mode: String,
        checks: List<PlatformIntegrationConfigurationCheck>,
        summary: String,
    ): PlatformIntegrationReadiness {
        val configured = checks.count { it.configured }
        val status = when {
            configured == checks.size -> PlatformIntegrationConfigurationStatus.CONFIGURED
            configured == 0 -> PlatformIntegrationConfigurationStatus.NOT_CONFIGURED
            else -> PlatformIntegrationConfigurationStatus.PARTIAL
        }
        return PlatformIntegrationReadiness(provider, provider.name, "", status, mode, summary, checks)
    }

    private fun check(label: String, value: String) = PlatformIntegrationConfigurationCheck(label, value.isNotBlank())
}
