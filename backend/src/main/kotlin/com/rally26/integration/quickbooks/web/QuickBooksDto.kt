package com.rally26.integration.quickbooks.web

import com.rally26.integration.core.web.IntegrationCatalogResponse
import com.rally26.integration.core.web.toResponse
import com.rally26.integration.quickbooks.application.QuickBooksOverview
import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksConnectionSetting
import com.rally26.integration.quickbooks.domain.QuickBooksExportBatch
import com.rally26.integration.quickbooks.domain.QuickBooksExportPreview
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
    val accountType: String,
    val active: Boolean,
)

data class QuickBooksAccountMappingResponse(
    val id: UUID,
    val mappingType: String,
    val externalAccountId: String,
    val externalAccountName: String,
    val externalAccountType: String?,
    val updatedAt: Instant,
)

data class UpdateQuickBooksMappingRequest(
    val mappingType: String,
    val accountId: String,
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

fun QuickBooksAccount.toResponse() = QuickBooksAccountResponse(id, name, accountType, active)

fun QuickBooksAccountMapping.toResponse() =
    QuickBooksAccountMappingResponse(id, mappingType.name, externalAccountId, externalAccountName, externalAccountType, updatedAt)

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
        missingMappings.map {
            it.name
        },
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
