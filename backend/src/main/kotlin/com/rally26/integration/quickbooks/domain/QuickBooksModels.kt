package com.rally26.integration.quickbooks.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class QuickBooksEnvironment { SANDBOX, PRODUCTION }

enum class QuickBooksExportPolicy { READ_ONLY, EXPORT_PREVIEW_ONLY }

enum class QuickBooksAccountingBasis { ACCRUAL, CASH }

enum class QuickBooksMappingType {
    PROGRAM_FEE_INCOME,
    SALES_INCOME,
    CONTRIBUTION_INCOME,
    SPONSORSHIP_INCOME,
    REFUNDS,
    FEES_RECEIVABLE,
    BANK_CLEARING,
    PAYOUT_CLEARING,
}

enum class QuickBooksMappingCompatibility { RECOMMENDED, ALLOWED_WITH_WARNING, BLOCKED }

enum class QuickBooksMappingValidationStatus {
    MISSING,
    VALID,
    VALID_WITH_WARNING,
    NEEDS_REVIEW,
    INACTIVE,
    ACCOUNT_NOT_FOUND,
    INCOMPATIBLE,
}

enum class QuickBooksPostingSide { DEBIT, CREDIT }

enum class QuickBooksFinancialSourceType {
    PROGRAM_FEE_ASSESSMENT,
    PROGRAM_FEE_PAYMENT,
    MERCHANDISE_SALE,
    CONTRIBUTION,
    SPONSORSHIP,
    REFUND_OR_CORRECTION,
    PAYOUT_SETTLEMENT,
}

enum class QuickBooksExportStatus { PREVIEWED, READY, BLOCKED, EXPORTED, PARTIAL, FAILED }

data class QuickBooksCompany(
    val realmId: String,
    val companyName: String,
    val country: String?,
    val defaultCurrency: String?,
)

data class QuickBooksAccount(
    val id: String,
    val name: String,
    val fullyQualifiedName: String?,
    val accountType: String,
    val accountSubType: String?,
    val classification: String?,
    val active: Boolean,
)

enum class QuickBooksMappingValidationSummary { NOT_RUN, PASSED, NEEDS_ATTENTION }

enum class QuickBooksActivationStage {
    NOT_CONFIGURED,
    SCAFFOLDED,
    COMPANY_CONTEXT_REQUIRED,
    MAPPINGS_REQUIRED,
    MAPPING_REVALIDATION_REQUIRED,
    CREDENTIALS_REQUIRED,
    SANDBOX_VERIFICATION_REQUIRED,
    ACCOUNTING_APPROVAL_REQUIRED,
    WRITE_POLICY_APPROVAL_REQUIRED,
    ACTIVATION_READY,
    ACTIVE,
}

enum class QuickBooksActivationGateStatus { SATISFIED, PENDING, BLOCKED_BY_PHASE_POLICY }

data class QuickBooksActivationGate(
    val code: String,
    val label: String,
    val status: QuickBooksActivationGateStatus,
    val detail: String,
    val satisfiedAt: Instant?,
)

data class QuickBooksActivationReadiness(
    val stage: QuickBooksActivationStage,
    val activationAllowed: Boolean,
    val credentialedProviderVerified: Boolean,
    val providerWritesEnabled: Boolean,
    val gates: List<QuickBooksActivationGate>,
)

data class QuickBooksConnectionSetting(
    val connectionId: UUID,
    val realmId: String?,
    val companyName: String?,
    val environment: QuickBooksEnvironment,
    val exportPolicy: QuickBooksExportPolicy,
    val accountingBasis: QuickBooksAccountingBasis,
    val defaultCurrency: String?,
    val lastCompanyReadAt: Instant?,
    val lastAccountsReadAt: Instant?,
    val lastMappingValidationAt: Instant?,
    val lastMappingValidationStatus: QuickBooksMappingValidationSummary,
    val credentialVerifiedAt: Instant?,
    val sandboxVerifiedAt: Instant?,
    val accountingApprovedAt: Instant?,
    val writePolicyApprovedAt: Instant?,
    val writePolicyVersion: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QuickBooksAccountMapping(
    val id: UUID,
    val connectionId: UUID,
    val mappingType: QuickBooksMappingType,
    val externalAccountId: String,
    val externalAccountName: String,
    val externalAccountFullyQualifiedName: String?,
    val externalAccountType: String?,
    val externalAccountSubType: String?,
    val compatibilityAtSelection: QuickBooksMappingCompatibility,
    val warningAcknowledged: Boolean,
    val active: Boolean,
    val configuredByUserId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QuickBooksMappingDefinition(
    val mappingType: QuickBooksMappingType,
    val label: String,
    val description: String,
    val recommendedAccountTypes: Set<String>,
    val warningAccountTypes: Set<String>,
)

data class QuickBooksMappingEvaluation(
    val compatibility: QuickBooksMappingCompatibility,
    val selectable: Boolean,
    val reason: String,
)

data class QuickBooksMappingOption(
    val account: QuickBooksAccount,
    val compatibility: QuickBooksMappingCompatibility,
    val selectable: Boolean,
    val suggested: Boolean,
    val reason: String,
)

data class QuickBooksMappingOptions(
    val definition: QuickBooksMappingDefinition,
    val suggestedAccountId: String?,
    val accounts: List<QuickBooksMappingOption>,
)

data class QuickBooksMappingValidation(
    val mappingType: QuickBooksMappingType,
    val mapping: QuickBooksAccountMapping?,
    val currentAccount: QuickBooksAccount?,
    val status: QuickBooksMappingValidationStatus,
    val message: String,
)

data class QuickBooksPostingLegDefinition(
    val side: QuickBooksPostingSide,
    val mappingType: QuickBooksMappingType,
)

data class QuickBooksPostingIntentDefinition(
    val sourceType: QuickBooksFinancialSourceType,
    val description: String,
    val legs: List<QuickBooksPostingLegDefinition>,
)

data class QuickBooksPostingIntentInput(
    val sourceType: QuickBooksFinancialSourceType,
    val sourceReference: String,
    val occurredOn: LocalDate,
    val amountMinor: Long,
    val currency: String,
)

data class QuickBooksPostingIntentValidation(
    val valid: Boolean,
    val diagnostics: List<String>,
    val requiredMappings: Set<QuickBooksMappingType>,
)

enum class QuickBooksProviderOperationKind { CREATE, UPDATE, DELETE }

enum class QuickBooksProviderOperationStatus {
    PENDING,
    PLANNED,
    WRITE_DISABLED,
    SENT,
    READBACK_REQUIRED,
    RETRY_SCHEDULED,
    EXPORTED,
    SKIPPED,
    FAILED,
}

enum class QuickBooksFailureCategory {
    VALIDATION,
    AUTHENTICATION,
    AUTHORIZATION,
    THROTTLED,
    STALE_OBJECT,
    MISSING_REFERENCE,
    DUPLICATE_REQUEST_ID,
    DUPLICATE_BUSINESS_KEY,
    CLOSED_PERIOD,
    COMPANY_STATUS,
    TRANSIENT_SYSTEM,
    AMBIGUOUS_TRANSPORT,
    UNKNOWN,
}

enum class QuickBooksRetryDisposition {
    DO_NOT_RETRY,
    REFRESH_AUTH,
    RETRY_SAME_REQUEST_AFTER_DELAY,
    READBACK_REQUIRED,
    READBACK_THEN_RETRY_SAME_REQUEST,
    REFRESH_ENTITY_THEN_REBUILD,
    REFRESH_REFERENCE_DATA,
    MANUAL_REVIEW,
}

enum class QuickBooksReadbackStrategy {
    NONE,
    QUERY_BY_STABLE_REFERENCE,
    READ_BY_ENTITY_ID,
}

enum class QuickBooksTransportFailureKind { TIMEOUT, CONNECTION_CLOSED, CONNECT_FAILURE, UNKNOWN }

data class QuickBooksRequestIdentity(
    val operationKey: String,
    val payloadHash: String,
    val intuitRequestId: String,
)

data class QuickBooksProviderRequestPlan(
    val identity: QuickBooksRequestIdentity,
    val operationKind: QuickBooksProviderOperationKind,
    val providerEntityType: String,
    val sourceType: String,
    val sourceReference: String,
    val readbackStrategy: QuickBooksReadbackStrategy,
    val readbackReference: String?,
    val providerWritesEnabled: Boolean,
)

data class QuickBooksProviderFailure(
    val category: QuickBooksFailureCategory,
    val httpStatus: Int?,
    val faultType: String?,
    val faultCode: String?,
    val message: String,
    val intuitTid: String?,
)

data class QuickBooksRetryDecision(
    val disposition: QuickBooksRetryDisposition,
    val retryable: Boolean,
    val minimumDelaySeconds: Long?,
    val readbackStrategy: QuickBooksReadbackStrategy,
    val reason: String,
)

data class QuickBooksProviderWriteResult(
    val externalEntityId: String?,
    val intuitTid: String?,
    val responsePayloadHash: String?,
)

data class QuickBooksProviderReadbackResult(
    val found: Boolean,
    val externalEntityId: String?,
    val syncToken: String?,
    val intuitTid: String?,
)

data class QuickBooksExportItem(
    val id: UUID,
    val batchId: UUID,
    val sourceType: String,
    val sourceId: UUID,
    val externalTransactionId: String?,
    val status: QuickBooksProviderOperationStatus,
    val payloadHash: String,
    val providerEntityType: String?,
    val operationKind: QuickBooksProviderOperationKind?,
    val operationKey: String?,
    val intuitRequestId: String?,
    val attemptCount: Int,
    val lastHttpStatus: Int?,
    val lastFaultType: String?,
    val lastFaultCode: String?,
    val lastIntuitTid: String?,
    val retryDisposition: QuickBooksRetryDisposition?,
    val retryNotBefore: Instant?,
    val lastAttemptAt: Instant?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QuickBooksExportCandidateCounts(
    val contributions: Int,
    val sponsorships: Int,
    val orders: Int,
    val feePayments: Int,
    val corrections: Int,
) {
    val total: Int get() = contributions + sponsorships + orders + feePayments + corrections
}

data class QuickBooksExportPreview(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val counts: QuickBooksExportCandidateCounts,
    val missingMappings: List<QuickBooksMappingType>,
    val mappingDiagnostics: List<QuickBooksMappingValidation>,
    val exportAllowed: Boolean,
    val reason: String,
)

data class QuickBooksExportBatch(
    val id: UUID,
    val connectionId: UUID,
    val organizationId: UUID,
    val syncRunId: UUID?,
    val status: QuickBooksExportStatus,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val candidateCount: Int,
    val exportedCount: Int,
    val failedCount: Int,
    val requestedByUserId: UUID,
    val createdAt: Instant,
    val completedAt: Instant?,
)
