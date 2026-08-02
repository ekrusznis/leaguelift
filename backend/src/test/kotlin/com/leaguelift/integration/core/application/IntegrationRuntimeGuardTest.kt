package com.leaguelift.integration.core.application

import com.leaguelift.config.IntegrationProperties
import com.leaguelift.config.IntegrationProviderRuntimeProperties
import org.springframework.mock.env.MockEnvironment
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IntegrationRuntimeGuardTest {
    @Test
    fun `disabled providers do not require an encryption key`() {
        val properties = IntegrationProperties()
        IntegrationRuntimeGuard(properties, CredentialCipher(properties), MockEnvironment()).afterPropertiesSet()
    }

    @Test
    fun `stub mode is rejected in production`() {
        val properties = IntegrationProperties(stubMode = true)
        val environment = MockEnvironment().withProperty("spring.profiles.active", "prod")
        environment.setActiveProfiles("prod")

        assertFailsWith<IllegalStateException> {
            IntegrationRuntimeGuard(properties, CredentialCipher(properties), environment).afterPropertiesSet()
        }
    }

    @Test
    fun `enabled provider requires a configured encryption key`() {
        val properties = IntegrationProperties(
            providers = mapOf("google-calendar" to IntegrationProviderRuntimeProperties(enabled = true)),
        )

        assertFailsWith<IllegalStateException> {
            IntegrationRuntimeGuard(properties, CredentialCipher(properties), MockEnvironment()).afterPropertiesSet()
        }
    }

    @Test
    fun `local stub accepts a valid encryption key without real provider endpoints`() {
        val properties = IntegrationProperties(
            encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
            stubMode = true,
            providers = mapOf("google-calendar" to IntegrationProviderRuntimeProperties(enabled = true)),
        )
        IntegrationRuntimeGuard(properties, CredentialCipher(properties), MockEnvironment()).afterPropertiesSet()
    }
}
