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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformIntegrationReadinessServiceTest {
    private val providerRepository = mockk<IntegrationProviderRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val currentUser = CurrentUser(UUID.randomUUID(), "platform@example.com", "Platform", platformAdministrator = true)

    @Test
    fun `readiness returns only sanitized configuration checks`() {
        every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW) } just runs
        every { providerRepository.list(IntegrationOwnerType.PLATFORM, false) } returns emptyList()
        val secretKey = "stripe-secret-value"
        val service =
            PlatformIntegrationReadinessService(
                providerRepository = providerRepository,
                authorizationService = authorizationService,
                stripe = StripeProperties(secretKey, "stripe-webhook-value"),
                printify = PrintifyProperties("printify-token-value", "shop-123"),
                emailProvider = EmailProviderProperties("logging"),
                resend = ResendProperties("resend-key-value", "notifications@example.com"),
                smsProvider = SmsProviderProperties("logging"),
                twilio = TwilioProperties("sid-value", "twilio-token-value", "+15555550100"),
                spaces = SpacesProperties("https://spaces.invalid", "access-value", "secret-value", "bucket", "nyc3"),
            )

        val result = service.list(currentUser)

        assertEquals(6, result.size)
        assertEquals(PlatformIntegrationConfigurationStatus.CONFIGURED, result.first { it.provider == IntegrationProvider.STRIPE }.status)
        assertEquals(PlatformIntegrationConfigurationStatus.PARTIAL, result.first { it.provider == IntegrationProvider.RESEND }.status)
        assertEquals(
            PlatformIntegrationConfigurationStatus.BUILT_IN,
            result.first { it.provider == IntegrationProvider.GOOGLE_MAPS }.status,
        )
        val serializedView = result.toString()
        assertFalse(serializedView.contains(secretKey))
        assertFalse(serializedView.contains("printify-token-value"))
        assertFalse(serializedView.contains("twilio-token-value"))
        assertTrue(result.flatMap { it.checks }.all { it.label.isNotBlank() })
        verify(exactly = 1) { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW) }
    }
}
