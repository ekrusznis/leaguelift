package com.rally26.integration.quickbooks.application

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
import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksActivationReadiness
import com.rally26.integration.quickbooks.domain.QuickBooksConnectionSetting
import com.rally26.integration.quickbooks.domain.QuickBooksEnvironment
import com.rally26.integration.quickbooks.domain.QuickBooksExportBatch
import com.rally26.integration.quickbooks.domain.QuickBooksExportPreview
import com.rally26.integration.quickbooks.domain.QuickBooksMappingCompatibility
import com.rally26.integration.quickbooks.domain.QuickBooksMappingDefinition
import com.rally26.integration.quickbooks.domain.QuickBooksMappingOptions
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidation
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidationStatus
import com.rally26.integration.quickbooks.domain.QuickBooksPostingIntentDefinition
import com.rally26.integration.quickbooks.persistence.QuickBooksRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class QuickBooksOverview(
    val catalog: IntegrationCatalogItem,
    val setting: QuickBooksConnectionSetting?,
    val mappings: List<QuickBooksAccountMapping>,
    val recentBatches: List<QuickBooksExportBatch>,
    val activationReadiness: QuickBooksActivationReadiness,
    val providerWritesEnabled: Boolean,
    val accountingReviewRequired: Boolean,
)

@Service
class QuickBooksService(
    private val catalogService: IntegrationCatalogService,
    private val oauthService: IntegrationOAuthService,
    private val repository: QuickBooksRepository,
    private val providerClient: QuickBooksProviderClient,
    private val mappingPolicy: QuickBooksAccountingMappingPolicy,
    private val postingIntentPolicy: QuickBooksPostingIntentPolicy,
    private val readinessPolicy: QuickBooksActivationReadinessPolicy,
    private val syncService: IntegrationSyncService,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    fun overview(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): QuickBooksOverview {
        membershipService.requireManagerRole(organizationId, currentUser)
        val catalog = quickBooksCatalog(organizationId, currentUser)
        val connectionId = catalog.connection?.id
        val setting = connectionId?.let(repository::findSetting)
        val mappings = connectionId?.let(repository::listMappings).orEmpty()
        val providerWritesEnabled = false
        return QuickBooksOverview(
            catalog = catalog,
            setting = setting,
            mappings = mappings,
            recentBatches = repository.listBatches(organizationId),
            activationReadiness =
                readinessPolicy.evaluate(
                    hasConnectionRecord = connectionId != null,
                    setting = setting,
                    mappings = mappings,
                    providerWritesEnabled = providerWritesEnabled,
                ),
            providerWritesEnabled = providerWritesEnabled,
            accountingReviewRequired = setting?.accountingApprovedAt == null,
        )
    }

    @Transactional
    fun readCompany(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): QuickBooksConnectionSetting {
        val access = requireAccess(organizationId, connectionId, currentUser)
        val realmId =
            access.connection.externalAccountId
                ?: throw ValidationException("The QuickBooks authorization did not provide a company realm identifier.")
        val company = providerClient.readCompany(access.accessToken, realmId)
        val setting =
            repository.upsertCompany(
                connectionId,
                company.realmId,
                company.companyName,
                company.defaultCurrency,
                QuickBooksEnvironment.SANDBOX,
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "integration.quickbooks_company_read",
            "integration_connection",
            connectionId,
        )
        return setting
    }

    @Transactional
    fun listAccounts(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): List<QuickBooksAccount> {
        val access = requireAccess(organizationId, connectionId, currentUser)
        val realmId =
            access.connection.externalAccountId
                ?: throw ValidationException("The QuickBooks authorization did not provide a company realm identifier.")
        val accounts = providerClient.listAccounts(access.accessToken, realmId)
        repository.markAccountsRead(connectionId)
        auditService.record(
            currentUser.userId,
            organizationId,
            "integration.quickbooks_accounts_read",
            "integration_connection",
            connectionId,
        )
        return accounts
    }

    fun mappingDefinitions(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<QuickBooksMappingDefinition> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return mappingPolicy.definitions()
    }

    fun postingIntentDefinitions(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<QuickBooksPostingIntentDefinition> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return postingIntentPolicy.definitions()
    }

    @Transactional
    fun mappingOptions(
        organizationId: UUID,
        connectionId: UUID,
        mappingType: QuickBooksMappingType,
        currentUser: CurrentUser,
    ): QuickBooksMappingOptions =
        mappingPolicy.options(
            mappingType,
            listAccounts(organizationId, connectionId, currentUser),
        )

    @Transactional
    fun validateMappings(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): List<QuickBooksMappingValidation> {
        val diagnostics =
            mappingPolicy.validateMappings(
                repository.listMappings(connectionId),
                listAccounts(organizationId, connectionId, currentUser),
            )
        repository.markMappingValidation(
            connectionId,
            diagnostics.all { it.status in NON_BLOCKING_MAPPING_STATUSES },
        )
        return diagnostics
    }

    @Transactional
    fun saveMapping(
        organizationId: UUID,
        connectionId: UUID,
        mappingType: QuickBooksMappingType,
        accountId: String,
        acknowledgeWarning: Boolean,
        currentUser: CurrentUser,
    ): QuickBooksAccountMapping {
        if (accountId.isBlank()) throw ValidationException("Choose a QuickBooks account.")
        val account =
            listAccounts(organizationId, connectionId, currentUser)
                .firstOrNull { it.id == accountId }
                ?: throw ValidationException("Choose an account returned by the connected QuickBooks company.")
        val evaluation = mappingPolicy.evaluate(mappingType, account)
        if (!evaluation.selectable || evaluation.compatibility == QuickBooksMappingCompatibility.BLOCKED) {
            throw ValidationException(evaluation.reason)
        }
        if (evaluation.compatibility == QuickBooksMappingCompatibility.ALLOWED_WITH_WARNING && !acknowledgeWarning) {
            throw ValidationException(
                "This QuickBooks account type is allowed only after the owner acknowledges the accounting warning.",
            )
        }
        val mapping =
            repository.replaceMapping(
                connectionId,
                mappingType,
                account.id,
                account.name,
                account.fullyQualifiedName,
                account.accountType,
                account.accountSubType,
                evaluation.compatibility,
                acknowledgeWarning,
                currentUser.userId,
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "integration.quickbooks_mapping_updated",
            "quickbooks_account_mapping",
            mapping.id,
        )
        return mapping
    }

    @Transactional
    fun previewExport(
        organizationId: UUID,
        connectionId: UUID,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        idempotencyKey: String,
        currentUser: CurrentUser,
    ): QuickBooksExportPreview {
        if (periodEnd.isBefore(periodStart)) throw ValidationException("The export end date must be on or after the start date.")
        if (ChronoUnit.DAYS.between(periodStart, periodEnd) > 366) throw ValidationException("Preview at most one year at a time.")
        if (idempotencyKey.isBlank()) throw ValidationException("An idempotency key is required.")
        val accounts = listAccounts(organizationId, connectionId, currentUser)
        val run =
            syncService.beginOrganizationRun(
                organizationId,
                connectionId,
                IntegrationProvider.QUICKBOOKS_ONLINE,
                IntegrationSyncDirection.WRITE,
                IntegrationSyncTrigger.MANUAL,
                "quickbooks-preview:$idempotencyKey",
                currentUser,
            )
        val counts = repository.countExportCandidates(organizationId, periodStart, periodEnd)
        val mappingDiagnostics = mappingPolicy.validateMappings(repository.listMappings(connectionId), accounts)
        repository.markMappingValidation(
            connectionId,
            mappingDiagnostics.all { it.status in NON_BLOCKING_MAPPING_STATUSES },
        )
        val missing =
            mappingDiagnostics
                .filter { it.status == QuickBooksMappingValidationStatus.MISSING }
                .map { it.mappingType }
        val blocked = mappingDiagnostics.any { it.status !in NON_BLOCKING_MAPPING_STATUSES }
        val alreadyCompleted =
            run.status in
                setOf(
                    IntegrationSyncStatus.SUCCEEDED,
                    IntegrationSyncStatus.PARTIAL,
                    IntegrationSyncStatus.FAILED,
                    IntegrationSyncStatus.CANCELLED,
                )
        if (!alreadyCompleted) {
            mappingDiagnostics
                .filter { it.status !in NON_BLOCKING_MAPPING_STATUSES }
                .forEach { diagnostic ->
                    syncService.issue(
                        run.id,
                        IntegrationSyncIssueSeverity.WARNING,
                        "QUICKBOOKS_MAPPING_${diagnostic.status.name}",
                        diagnostic.message,
                        externalEntityType = "ACCOUNT_MAPPING",
                        externalEntityId = diagnostic.mappingType.name,
                    )
                }
        }
        repository.insertPreviewBatch(
            connectionId,
            organizationId,
            run.id,
            periodStart,
            periodEnd,
            counts.total,
            digest("$organizationId:$connectionId:$periodStart:$periodEnd:$idempotencyKey"),
            currentUser.userId,
            blocked,
        )
        if (!alreadyCompleted) {
            syncService.finish(
                run.id,
                if (blocked) IntegrationSyncStatus.PARTIAL else IntegrationSyncStatus.SUCCEEDED,
                IntegrationSyncSummary(discovered = counts.total, skipped = if (blocked) counts.total else 0, failed = 0),
            )
        }
        auditService.record(
            currentUser.userId,
            organizationId,
            "integration.quickbooks_export_previewed",
            "integration_connection",
            connectionId,
        )
        return QuickBooksExportPreview(
            periodStart,
            periodEnd,
            counts,
            missing,
            mappingDiagnostics,
            exportAllowed = false,
            reason =
                if (blocked) {
                    "Complete or repair the required chart-of-accounts mappings before a later credentialed " +
                        "activation. Provider writes remain disabled in Phase 29."
                } else {
                    "The preview is ready, but provider writes remain disabled throughout Phase 29 and require " +
                        "a later explicitly approved credentialed activation."
                },
        )
    }

    private fun requireAccess(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ) = oauthService.accessTokenForOrganizationConnection(organizationId, connectionId, currentUser).also {
        if (it.connection.provider != IntegrationProvider.QUICKBOOKS_ONLINE) {
            throw NotFoundException("QUICKBOOKS_CONNECTION_NOT_FOUND", "The QuickBooks connection could not be found.")
        }
    }

    private fun quickBooksCatalog(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): IntegrationCatalogItem =
        catalogService
            .listForOrganization(organizationId, currentUser)
            .firstOrNull { it.definition.provider == IntegrationProvider.QUICKBOOKS_ONLINE }
            ?: throw NotFoundException("INTEGRATION_PROVIDER_NOT_FOUND", "QuickBooks Online could not be found in the provider catalog.")

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val NON_BLOCKING_MAPPING_STATUSES =
            setOf(
                QuickBooksMappingValidationStatus.VALID,
                QuickBooksMappingValidationStatus.VALID_WITH_WARNING,
            )
    }
}
