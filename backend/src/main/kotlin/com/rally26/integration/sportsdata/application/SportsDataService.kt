package com.rally26.integration.sportsdata.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationCatalogItem
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.application.IntegrationSyncService
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationSyncDirection
import com.rally26.integration.core.domain.IntegrationSyncIssueSeverity
import com.rally26.integration.core.domain.IntegrationSyncStatus
import com.rally26.integration.core.domain.IntegrationSyncSummary
import com.rally26.integration.core.domain.IntegrationSyncTrigger
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import com.rally26.integration.sportsdata.domain.SportsDataExternalRecord
import com.rally26.integration.sportsdata.domain.SportsDataImportIssue
import com.rally26.integration.sportsdata.domain.SportsDataImportStatus
import com.rally26.integration.sportsdata.domain.SportsDataIssueSeverity
import com.rally26.integration.sportsdata.domain.SportsDataPreview
import com.rally26.integration.sportsdata.domain.SportsDataSourceMode
import com.rally26.integration.sportsdata.persistence.SportsDataRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

data class SportsDataOverview(
    val providers: List<IntegrationCatalogItem>,
    val recentRuns: List<com.rally26.integration.sportsdata.domain.SportsDataImportRun>,
    val directProviderImportEnabled: Boolean,
    val reviewedFileImportAvailable: Boolean,
)

@Service
class SportsDataService(
    private val catalogService: IntegrationCatalogService,
    private val oauthService: IntegrationOAuthService,
    private val providerClients: List<SportsDataProviderClient>,
    private val repository: SportsDataRepository,
    private val syncService: IntegrationSyncService,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun overview(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): SportsDataOverview {
        membershipService.requireManagerRole(organizationId, currentUser)
        val providers =
            catalogService
                .listForOrganization(organizationId, currentUser)
                .filter { it.definition.provider in SUPPORTED_PROVIDERS }
        return SportsDataOverview(
            providers,
            repository.listRuns(organizationId),
            directProviderImportEnabled = false,
            reviewedFileImportAvailable = true,
        )
    }

    @Transactional
    fun previewConnected(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): SportsDataPreview {
        val access = oauthService.accessTokenForOrganizationConnection(organizationId, connectionId, currentUser)
        if (access.connection.provider != IntegrationProvider.SPORTSENGINE) {
            throw NotFoundException("SPORTS_DATA_CONNECTION_NOT_FOUND", "The sports-data connection could not be found.")
        }
        val client =
            providerClients.firstOrNull { it.supports(access.connection.provider) }
                ?: throw ValidationException("This sports-data provider client is not configured.")
        val records = client.fetchSnapshot(access.connection.provider, access.accessToken)
        return preview(
            organizationId,
            connectionId,
            access.connection.provider,
            SportsDataSourceMode.OAUTH,
            records,
            currentUser,
        )
    }

    @Transactional
    fun previewFile(
        organizationId: UUID,
        provider: IntegrationProvider,
        records: List<SportsDataExternalRecord>,
        currentUser: CurrentUser,
    ): SportsDataPreview {
        membershipService.requireManagerRole(organizationId, currentUser)
        if (provider !in setOf(IntegrationProvider.GAMECHANGER, IntegrationProvider.MAXPREPS)) {
            throw ValidationException("Reviewed file previews are available only for partner-pending GameChanger and MaxPreps workflows.")
        }
        return preview(organizationId, null, provider, SportsDataSourceMode.FILE_IMPORT, records, currentUser)
    }

    fun issues(
        organizationId: UUID,
        runId: UUID,
        currentUser: CurrentUser,
    ): List<SportsDataImportIssue> {
        membershipService.requireManagerRole(organizationId, currentUser)
        val run =
            repository
                .findRun(runId)
                ?.takeIf { it.organizationId == organizationId }
                ?: throw NotFoundException("SPORTS_DATA_IMPORT_NOT_FOUND", "The sports-data preview could not be found.")
        return repository.listIssues(run.id)
    }

    private fun preview(
        organizationId: UUID,
        connectionId: UUID?,
        provider: IntegrationProvider,
        sourceMode: SportsDataSourceMode,
        records: List<SportsDataExternalRecord>,
        currentUser: CurrentUser,
    ): SportsDataPreview {
        if (records.size > 5000) throw ValidationException("Preview at most 5,000 provider records at a time.")
        val pendingIssues = mutableListOf<PendingIssue>()
        val seen = mutableSetOf<Pair<SportsDataEntityType, String>>()
        var duplicateCount = 0
        var validCount = 0
        records.forEachIndexed { index, record ->
            val row = index + 1
            if (record.externalId.isBlank()) {
                pendingIssues +=
                    PendingIssue(
                        row,
                        record.entityType,
                        null,
                        SportsDataIssueSeverity.ERROR,
                        "MISSING_EXTERNAL_ID",
                        "Every provider record needs a stable external identifier.",
                    )
                return@forEachIndexed
            }
            val key = record.entityType to record.externalId
            if (!seen.add(key)) {
                duplicateCount++
                pendingIssues +=
                    PendingIssue(
                        row,
                        record.entityType,
                        record.externalId,
                        SportsDataIssueSeverity.WARNING,
                        "DUPLICATE_EXTERNAL_ID",
                        "This external identifier appears more than once in the preview.",
                    )
                return@forEachIndexed
            }
            if (record.entityType != SportsDataEntityType.ORGANIZATION && record.externalParentId.isNullOrBlank()) {
                pendingIssues +=
                    PendingIssue(
                        row,
                        record.entityType,
                        record.externalId,
                        SportsDataIssueSeverity.WARNING,
                        "MISSING_PARENT_ID",
                        "This record has no external parent identity and will need review before import.",
                    )
            }
            if (connectionId != null && repository.findMapping(connectionId, record.entityType, record.externalId) != null) duplicateCount++
            validCount++
        }
        val errorCount = pendingIssues.count { it.severity == SportsDataIssueSeverity.ERROR }
        val conflictCount = pendingIssues.count { it.code == "MISSING_PARENT_ID" }
        val status = if (errorCount > 0) SportsDataImportStatus.BLOCKED else SportsDataImportStatus.PREVIEWED
        val previewHash =
            digest(
                records.joinToString(
                    "\n",
                ) { "${it.entityType}:${it.externalId}:${it.externalParentId}:${it.name}:${it.payload.toSortedMap()}" },
            )
        val syncRun =
            syncService.beginOrganizationRun(
                organizationId,
                connectionId,
                provider,
                IntegrationSyncDirection.READ,
                if (sourceMode == SportsDataSourceMode.OAUTH) IntegrationSyncTrigger.STUB else IntegrationSyncTrigger.MANUAL,
                "sports-preview:$provider:$previewHash",
                currentUser,
            )
        val run =
            repository.createPreviewRun(
                organizationId,
                connectionId,
                syncRun.id,
                provider,
                sourceMode,
                status,
                records.size,
                validCount,
                duplicateCount,
                conflictCount,
                errorCount,
                previewHash,
                currentUser.userId,
            )
        val issues =
            pendingIssues.map {
                syncService.issue(
                    syncRun.id,
                    if (it.severity ==
                        SportsDataIssueSeverity.ERROR
                    ) {
                        IntegrationSyncIssueSeverity.ERROR
                    } else {
                        IntegrationSyncIssueSeverity.WARNING
                    },
                    it.code,
                    it.message,
                    externalEntityType = it.entityType.name,
                    externalEntityId = it.externalId,
                )
                repository.addIssue(run.id, it.row, it.entityType, it.externalId, it.severity, it.code, it.message)
            }
        syncService.finish(
            syncRun.id,
            when {
                errorCount > 0 -> IntegrationSyncStatus.PARTIAL
                pendingIssues.isNotEmpty() -> IntegrationSyncStatus.PARTIAL
                else -> IntegrationSyncStatus.SUCCEEDED
            },
            IntegrationSyncSummary(discovered = records.size, skipped = duplicateCount + conflictCount, failed = errorCount),
        )
        auditService.record(currentUser.userId, organizationId, "integration.sports_data_previewed", "sports_data_import_run", run.id)
        return SportsDataPreview(
            run,
            issues,
            records.take(100),
            directImportEnabled = false,
            message =
                if (provider == IntegrationProvider.SPORTSENGINE) {
                    "Provider records were mapped into a review-only preview. Direct import remains disabled until the official SportsEngine contract is verified."
                } else {
                    "The partner-pending file was validated. Use the existing reviewed CSV/ICS workflows; this scaffold does not create a direct provider connection."
                },
        )
    }

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private data class PendingIssue(
        val row: Int,
        val entityType: SportsDataEntityType,
        val externalId: String?,
        val severity: SportsDataIssueSeverity,
        val code: String,
        val message: String,
    )

    private companion object {
        val SUPPORTED_PROVIDERS = setOf(IntegrationProvider.SPORTSENGINE, IntegrationProvider.GAMECHANGER, IntegrationProvider.MAXPREPS)
    }
}
