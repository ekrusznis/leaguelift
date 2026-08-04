package com.rally26.integration.quickbooks.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class QuickBooksEnvironment { SANDBOX, PRODUCTION }
enum class QuickBooksExportPolicy { READ_ONLY, EXPORT_PREVIEW_ONLY }
enum class QuickBooksAccountingBasis { ACCRUAL, CASH }
enum class QuickBooksMappingType {
    SALES_INCOME,
    CONTRIBUTION_INCOME,
    SPONSORSHIP_INCOME,
    REFUNDS,
    FEES_RECEIVABLE,
    BANK_CLEARING,
    PAYOUT_CLEARING,
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
    val accountType: String,
    val active: Boolean,
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
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QuickBooksAccountMapping(
    val id: UUID,
    val connectionId: UUID,
    val mappingType: QuickBooksMappingType,
    val externalAccountId: String,
    val externalAccountName: String,
    val externalAccountType: String?,
    val active: Boolean,
    val configuredByUserId: UUID,
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
