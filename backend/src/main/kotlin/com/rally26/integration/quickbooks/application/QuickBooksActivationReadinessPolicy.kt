package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksActivationGate
import com.rally26.integration.quickbooks.domain.QuickBooksActivationGateStatus
import com.rally26.integration.quickbooks.domain.QuickBooksActivationReadiness
import com.rally26.integration.quickbooks.domain.QuickBooksActivationStage
import com.rally26.integration.quickbooks.domain.QuickBooksConnectionSetting
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidationSummary
import org.springframework.stereotype.Component

@Component
class QuickBooksActivationReadinessPolicy {
    fun evaluate(
        hasConnectionRecord: Boolean,
        setting: QuickBooksConnectionSetting?,
        mappings: List<QuickBooksAccountMapping>,
        providerWritesEnabled: Boolean,
    ): QuickBooksActivationReadiness {
        val companyContextReady =
            setting?.realmId?.isNotBlank() == true &&
                setting.companyName?.isNotBlank() == true
        val configuredTypes = mappings.filter { it.active }.map { it.mappingType }.toSet()
        val mappingsConfigured = REQUIRED_MAPPING_TYPES.all { it in configuredTypes }
        val mappingsRevalidated =
            setting?.lastMappingValidationStatus == QuickBooksMappingValidationSummary.PASSED &&
                setting.lastMappingValidationAt != null
        val credentialsVerified = setting?.credentialVerifiedAt != null
        val sandboxVerified = setting?.sandboxVerifiedAt != null
        val accountingApproved = setting?.accountingApprovedAt != null
        val writePolicyApproved =
            setting?.writePolicyApprovedAt != null &&
                !setting.writePolicyVersion.isNullOrBlank()

        val stage =
            when {
                !hasConnectionRecord -> QuickBooksActivationStage.NOT_CONFIGURED
                setting == null -> QuickBooksActivationStage.SCAFFOLDED
                !companyContextReady -> QuickBooksActivationStage.COMPANY_CONTEXT_REQUIRED
                !mappingsConfigured -> QuickBooksActivationStage.MAPPINGS_REQUIRED
                !mappingsRevalidated -> QuickBooksActivationStage.MAPPING_REVALIDATION_REQUIRED
                !credentialsVerified -> QuickBooksActivationStage.CREDENTIALS_REQUIRED
                !sandboxVerified -> QuickBooksActivationStage.SANDBOX_VERIFICATION_REQUIRED
                !accountingApproved -> QuickBooksActivationStage.ACCOUNTING_APPROVAL_REQUIRED
                !writePolicyApproved -> QuickBooksActivationStage.WRITE_POLICY_APPROVAL_REQUIRED
                !providerWritesEnabled -> QuickBooksActivationStage.ACTIVATION_READY
                else -> QuickBooksActivationStage.ACTIVE
            }

        val upstreamApproved =
            hasConnectionRecord &&
                companyContextReady &&
                mappingsConfigured &&
                mappingsRevalidated &&
                credentialsVerified &&
                sandboxVerified &&
                accountingApproved &&
                writePolicyApproved

        return QuickBooksActivationReadiness(
            stage = stage,
            activationAllowed = upstreamApproved && providerWritesEnabled,
            credentialedProviderVerified = credentialsVerified,
            providerWritesEnabled = providerWritesEnabled,
            gates =
                listOf(
                    gate(
                        "CONNECTION_RECORD",
                        "Integration scaffold",
                        hasConnectionRecord,
                        "A Rally26 integration connection record exists. This alone does not prove a live Intuit connection.",
                    ),
                    gate(
                        "COMPANY_CONTEXT",
                        "QuickBooks company context",
                        companyContextReady,
                        "Realm/company metadata has been read into Rally26. Saved metadata is not a health check.",
                        setting?.lastCompanyReadAt,
                    ),
                    gate(
                        "MAPPINGS_CONFIGURED",
                        "Accounting mappings configured",
                        mappingsConfigured,
                        "Every required Rally26 accounting role has an owner/admin-selected QuickBooks account.",
                    ),
                    gate(
                        "MAPPINGS_REVALIDATED",
                        "Accounting mappings revalidated",
                        mappingsRevalidated,
                        "The saved mappings passed the latest explicit chart-of-accounts revalidation.",
                        setting?.lastMappingValidationAt,
                    ),
                    gate(
                        "CREDENTIALS_VERIFIED",
                        "Intuit credentials verified",
                        credentialsVerified,
                        "Requires a later credentialed phase to verify real Intuit OAuth credentials and granted scopes.",
                        setting?.credentialVerifiedAt,
                    ),
                    gate(
                        "SANDBOX_VERIFIED",
                        "Sandbox verification complete",
                        sandboxVerified,
                        "Requires later end-to-end verification against an approved QuickBooks sandbox company.",
                        setting?.sandboxVerifiedAt,
                    ),
                    gate(
                        "ACCOUNTING_APPROVED",
                        "Accounting review approved",
                        accountingApproved,
                        "Requires explicit owner/accountant approval of mappings and posting behavior.",
                        setting?.accountingApprovedAt,
                    ),
                    gate(
                        "WRITE_POLICY_APPROVED",
                        "Write policy approved",
                        writePolicyApproved,
                        "Requires an explicit versioned provider-write policy before any accounting write is allowed.",
                        setting?.writePolicyApprovedAt,
                    ),
                    QuickBooksActivationGate(
                        code = "PROVIDER_WRITES",
                        label = "Provider writes enabled",
                        status =
                            if (providerWritesEnabled) {
                                QuickBooksActivationGateStatus.SATISFIED
                            } else {
                                QuickBooksActivationGateStatus.BLOCKED_BY_PHASE_POLICY
                            },
                        detail =
                            if (providerWritesEnabled) {
                                "Separately approved activation enabled provider writes."
                            } else {
                                "Provider writes are disabled until credentialed activation is approved."
                            },
                        satisfiedAt = null,
                    ),
                ),
        )
    }

    private fun gate(
        code: String,
        label: String,
        satisfied: Boolean,
        detail: String,
        satisfiedAt: java.time.Instant? = null,
    ) = QuickBooksActivationGate(
        code = code,
        label = label,
        status = if (satisfied) QuickBooksActivationGateStatus.SATISFIED else QuickBooksActivationGateStatus.PENDING,
        detail = detail,
        satisfiedAt = satisfiedAt,
    )

    private companion object {
        val REQUIRED_MAPPING_TYPES = QuickBooksMappingType.entries.toSet()
    }
}
