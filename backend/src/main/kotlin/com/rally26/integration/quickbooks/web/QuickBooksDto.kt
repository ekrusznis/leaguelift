package com.rally26.integration.quickbooks.web

import com.rally26.integration.core.web.IntegrationCatalogResponse
import com.rally26.integration.core.web.toResponse
import com.rally26.integration.quickbooks.application.QuickBooksOverview
import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksConnectionSetting
import com.rally26.integration.quickbooks.domain.QuickBooksExportBatch
import com.rally26.integration.quickbooks.domain.QuickBooksExportPreview
import com.rally26.integration.quickbooks.domain.QuickBooksMappingDefinition
import com.rally26.integration.quickbooks.domain.QuickBooksMappingOption
import com.rally26.integration.quickbooks.domain.QuickBooksMappingOptions
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidation
import com.rally26.integration.quickbooks.domain.QuickBooksPostingIntentDefinition
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class QuickBooksOverviewResponse(
    val catalog: IntegrationCatalogResponse,
    val setting: QuickBooksConnectionSettingResponse?,
    val mappings: List<QuickBooksAccountMappingResponse>,
    val recentBatches: List<QuickBooksExportBatchResponse>,
    val providerWritesEnabled: Boolean,
    val accountingReviewRequired: Boolean,
)

data class QuickBooksConnectionSettingResponse(
    val connectionId: UUID,
    val realmId: String?,
    val companyName: String?,
    val environment: String,
    val exportPolicy: String,
    val accountingBasis: String,
    val defaultCurrency: String?,
    val lastCompanyReadAt: Instant?,
    val lastAccountsReadAt: Instant?,
    val updatedAt: Instant,
)

data class QuickBooksAccountResponse(
    val id: String,
    val name: String,
    val fullyQualifiedName: String?,
    val accountType: String,
    val accountSubType: String?,
    val classification: String?,
    val active: Boolean,
)

data class QuickBooksAccountMappingResponse(
    val id: UUID,
    val mappingType: String,
    val externalAccountId: String,
    val externalAccountName: String,
    val externalAccountFullyQualifiedName: String?,
    val externalAccountType: String?,
    val externalAccountSubType: String?,
    val compatibilityAtSelection: String,
    val warningAcknowledged: Boolean,
    val updatedAt: Instant,
)

data class QuickBooksMappingDefinitionResponse(
    val mappingType: String,
    val label: String,
    val description: String,
    val recommendedAccountTypes: List<String>,
    val warningAccountTypes: List<String>,
)

data class QuickBooksMappingOptionResponse(
    val account: QuickBooksAccountResponse,
    val compatibility: String,
    val selectable: Boolean,
    val suggested: Boolean,
    val reason: String,
)

data class QuickBooksMappingOptionsResponse(
    val definition: QuickBooksMappingDefinitionResponse,
    val suggestedAccountId: String?,
    val accounts: List<QuickBooksMappingOptionResponse>,
)

data class QuickBooksMappingValidationResponse(
    val mappingType: String,
    val mapping: QuickBooksAccountMappingResponse?,
    val currentAccount: QuickBooksAccountResponse?,
    val status: String,
    val message: String,
)

data class QuickBooksPostingLegDefinitionResponse(
    val side: String,
    val mappingType: String,
)

data class QuickBooksPostingIntentDefinitionResponse(
    val sourceType: String,
    val description: String,
    val legs: List<QuickBooksPostingLegDefinitionResponse>,
)

data class UpdateQuickBooksMappingRequest(
    val mappingType: String,
    val accountId: String,
    val acknowledgeWarning: Boolean = false,
)

data class QuickBooksExportPreviewRequest(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val idempotencyKey: String,
)

data class QuickBooksExportCandidateCountsResponse(
    val contributions: Int,
    val sponsorships: Int,
    val orders: Int,
    val feePayments: Int,
    val corrections: Int,
    val total: Int,
)

data class QuickBooksExportPreviewResponse(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val counts: QuickBooksExportCandidateCountsResponse,
    val missingMappings: List<String>,
    val mappingDiagnostics: List<QuickBooksMappingValidationResponse>,
    val exportAllowed: Boolean,
    val reason: String,
)

data class QuickBooksExportBatchResponse(
    val id: UUID,
    val status: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val candidateCount: Int,
    val exportedCount: Int,
    val failedCount: Int,
    val createdAt: Instant,
    val completedAt: Instant?,
)

fun QuickBooksOverview.toResponse() =
    QuickBooksOverviewResponse(
        catalog.toResponse(),
        setting?.toResponse(),
        mappings.map { it.toResponse() },
        recentBatches.map { it.toResponse() },
        providerWritesEnabled,
        accountingReviewRequired,
    )

fun QuickBooksConnectionSetting.toResponse() =
    QuickBooksConnectionSettingResponse(
        connectionId,
        realmId,
        companyName,
        environment.name,
        exportPolicy.name,
        accountingBasis.name,
        defaultCurrency,
        lastCompanyReadAt,
        lastAccountsReadAt,
        updatedAt,
    )

fun QuickBooksAccount.toResponse() =
    QuickBooksAccountResponse(
        id,
        name,
        fullyQualifiedName,
        accountType,
        accountSubType,
        classification,
        active,
    )

fun QuickBooksAccountMapping.toResponse() =
    QuickBooksAccountMappingResponse(
        id,
        mappingType.name,
        externalAccountId,
        externalAccountName,
        externalAccountFullyQualifiedName,
        externalAccountType,
        externalAccountSubType,
        compatibilityAtSelection.name,
        warningAcknowledged,
        updatedAt,
    )

fun QuickBooksMappingDefinition.toResponse() =
    QuickBooksMappingDefinitionResponse(
        mappingType.name,
        label,
        description,
        recommendedAccountTypes.sorted(),
        warningAccountTypes.sorted(),
    )

fun QuickBooksMappingOption.toResponse() =
    QuickBooksMappingOptionResponse(
        account.toResponse(),
        compatibility.name,
        selectable,
        suggested,
        reason,
    )

fun QuickBooksMappingOptions.toResponse() =
    QuickBooksMappingOptionsResponse(
        definition.toResponse(),
        suggestedAccountId,
        accounts.map { it.toResponse() },
    )

fun QuickBooksMappingValidation.toResponse() =
    QuickBooksMappingValidationResponse(
        mappingType.name,
        mapping?.toResponse(),
        currentAccount?.toResponse(),
        status.name,
        message,
    )

fun QuickBooksPostingIntentDefinition.toResponse() =
    QuickBooksPostingIntentDefinitionResponse(
        sourceType.name,
        description,
        legs.map { QuickBooksPostingLegDefinitionResponse(it.side.name, it.mappingType.name) },
    )

fun QuickBooksExportPreview.toResponse() =
    QuickBooksExportPreviewResponse(
        periodStart,
        periodEnd,
        QuickBooksExportCandidateCountsResponse(
            counts.contributions,
            counts.sponsorships,
            counts.orders,
            counts.feePayments,
            counts.corrections,
            counts.total,
        ),
        missingMappings.map { it.name },
        mappingDiagnostics.map { it.toResponse() },
        exportAllowed,
        reason,
    )

fun QuickBooksExportBatch.toResponse() =
    QuickBooksExportBatchResponse(
        id,
        status.name,
        periodStart,
        periodEnd,
        candidateCount,
        exportedCount,
        failedCount,
        createdAt,
        completedAt,
    )
