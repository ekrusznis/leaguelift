package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksAccountingBasis
import com.rally26.integration.quickbooks.domain.QuickBooksActivationGateStatus
import com.rally26.integration.quickbooks.domain.QuickBooksActivationStage
import com.rally26.integration.quickbooks.domain.QuickBooksConnectionSetting
import com.rally26.integration.quickbooks.domain.QuickBooksEnvironment
import com.rally26.integration.quickbooks.domain.QuickBooksExportPolicy
import com.rally26.integration.quickbooks.domain.QuickBooksMappingCompatibility
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidationSummary
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickBooksActivationReadinessPolicyTest {
    private val policy = QuickBooksActivationReadinessPolicy()

    @Test
    fun `connection record never implies credentialed provider verification`() {
        val result = policy.evaluate(true, null, emptyList(), false)

        assertEquals(QuickBooksActivationStage.SCAFFOLDED, result.stage)
        assertFalse(result.credentialedProviderVerified)
        assertFalse(result.activationAllowed)
        assertEquals(
            QuickBooksActivationGateStatus.BLOCKED_BY_PHASE_POLICY,
            result.gates.single { it.code == "PROVIDER_WRITES" }.status,
        )
    }

    @Test
    fun `complete local setup stops at credentials required`() {
        val result =
            policy.evaluate(
                hasConnectionRecord = true,
                setting = setting(mappingStatus = QuickBooksMappingValidationSummary.PASSED),
                mappings = allMappings(),
                providerWritesEnabled = false,
            )

        assertEquals(QuickBooksActivationStage.CREDENTIALS_REQUIRED, result.stage)
        assertFalse(result.credentialedProviderVerified)
        assertFalse(result.activationAllowed)
    }

    @Test
    fun `all approval gates with writes disabled is activation ready but not active`() {
        val now = Instant.parse("2026-08-09T20:00:00Z")
        val result =
            policy.evaluate(
                hasConnectionRecord = true,
                setting =
                    setting(
                        mappingStatus = QuickBooksMappingValidationSummary.PASSED,
                        credentialVerifiedAt = now,
                        sandboxVerifiedAt = now,
                        accountingApprovedAt = now,
                        writePolicyApprovedAt = now,
                        writePolicyVersion = "future-v1",
                    ),
                mappings = allMappings(),
                providerWritesEnabled = false,
            )

        assertEquals(QuickBooksActivationStage.ACTIVATION_READY, result.stage)
        assertTrue(result.credentialedProviderVerified)
        assertFalse(result.activationAllowed)
    }

    @Test
    fun `active requires every approval gate plus explicit provider write enablement`() {
        val now = Instant.parse("2026-08-09T20:00:00Z")
        val result =
            policy.evaluate(
                hasConnectionRecord = true,
                setting =
                    setting(
                        mappingStatus = QuickBooksMappingValidationSummary.PASSED,
                        credentialVerifiedAt = now,
                        sandboxVerifiedAt = now,
                        accountingApprovedAt = now,
                        writePolicyApprovedAt = now,
                        writePolicyVersion = "future-v1",
                    ),
                mappings = allMappings(),
                providerWritesEnabled = true,
            )

        assertEquals(QuickBooksActivationStage.ACTIVE, result.stage)
        assertTrue(result.activationAllowed)
    }

    private fun setting(
        mappingStatus: QuickBooksMappingValidationSummary,
        credentialVerifiedAt: Instant? = null,
        sandboxVerifiedAt: Instant? = null,
        accountingApprovedAt: Instant? = null,
        writePolicyApprovedAt: Instant? = null,
        writePolicyVersion: String? = null,
    ) = QuickBooksConnectionSetting(
        connectionId = UUID.randomUUID(),
        realmId = "1234567890",
        companyName = "Rally26 Test Club",
        environment = QuickBooksEnvironment.SANDBOX,
        exportPolicy = QuickBooksExportPolicy.READ_ONLY,
        accountingBasis = QuickBooksAccountingBasis.ACCRUAL,
        defaultCurrency = "USD",
        lastCompanyReadAt = Instant.parse("2026-08-09T19:00:00Z"),
        lastAccountsReadAt = Instant.parse("2026-08-09T19:05:00Z"),
        lastMappingValidationAt = Instant.parse("2026-08-09T19:10:00Z"),
        lastMappingValidationStatus = mappingStatus,
        credentialVerifiedAt = credentialVerifiedAt,
        sandboxVerifiedAt = sandboxVerifiedAt,
        accountingApprovedAt = accountingApprovedAt,
        writePolicyApprovedAt = writePolicyApprovedAt,
        writePolicyVersion = writePolicyVersion,
        createdAt = Instant.parse("2026-08-09T18:00:00Z"),
        updatedAt = Instant.parse("2026-08-09T19:10:00Z"),
    )

    private fun allMappings(): List<QuickBooksAccountMapping> =
        QuickBooksMappingType.entries.mapIndexed { index, type ->
            QuickBooksAccountMapping(
                id = UUID.randomUUID(),
                connectionId = UUID.randomUUID(),
                mappingType = type,
                externalAccountId = "acct-$index",
                externalAccountName = "Account $index",
                externalAccountFullyQualifiedName = "Account $index",
                externalAccountType = "Income",
                externalAccountSubType = null,
                compatibilityAtSelection = QuickBooksMappingCompatibility.RECOMMENDED,
                warningAcknowledged = false,
                active = true,
                configuredByUserId = UUID.randomUUID(),
                createdAt = Instant.parse("2026-08-09T18:00:00Z"),
                updatedAt = Instant.parse("2026-08-09T18:00:00Z"),
            )
        }
}
