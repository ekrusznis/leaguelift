package com.rally26.integration.core.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationSyncDirection
import com.rally26.integration.core.domain.IntegrationSyncIssue
import com.rally26.integration.core.domain.IntegrationSyncIssueSeverity
import com.rally26.integration.core.domain.IntegrationSyncRun
import com.rally26.integration.core.domain.IntegrationSyncStatus
import com.rally26.integration.core.domain.IntegrationSyncSummary
import com.rally26.integration.core.domain.IntegrationSyncTrigger
import com.rally26.integration.core.persistence.IntegrationSyncRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IntegrationSyncService(
    private val repository: IntegrationSyncRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
) {
    @Transactional
    fun beginOrganizationRun(
        organizationId: UUID,
        connectionId: UUID?,
        provider: IntegrationProvider,
        direction: IntegrationSyncDirection,
        trigger: IntegrationSyncTrigger,
        idempotencyKey: String?,
        currentUser: CurrentUser,
    ): IntegrationSyncRun {
        membershipService.requireManagerRole(organizationId, currentUser)
        if (!idempotencyKey.isNullOrBlank()) {
            repository
                .findByIdempotency(
                    provider,
                    IntegrationOwnerType.ORGANIZATION,
                    organizationId,
                    null,
                    idempotencyKey,
                )?.let { return it }
        }
        return repository.markRunning(
            repository
                .create(
                    connectionId,
                    provider,
                    IntegrationOwnerType.ORGANIZATION,
                    organizationId,
                    null,
                    direction,
                    trigger,
                    idempotencyKey,
                    currentUser.userId,
                ).id,
        )
    }

    @Transactional
    fun finish(
        runId: UUID,
        status: IntegrationSyncStatus,
        summary: IntegrationSyncSummary,
        errorCode: String? = null,
        errorMessage: String? = null,
    ): IntegrationSyncRun = repository.complete(runId, status, summary, errorCode = errorCode, errorMessage = errorMessage)

    @Transactional
    fun issue(
        runId: UUID,
        severity: IntegrationSyncIssueSeverity,
        code: String,
        message: String,
        externalEntityType: String? = null,
        externalEntityId: String? = null,
        internalEntityType: String? = null,
        internalEntityId: UUID? = null,
        retryable: Boolean = false,
    ): IntegrationSyncIssue =
        repository.addIssue(
            runId,
            severity,
            code,
            message,
            externalEntityType,
            externalEntityId,
            internalEntityType,
            internalEntityId,
            retryable,
        )

    fun listOrganization(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<IntegrationSyncRun> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.listForOrganization(organizationId, 50)
    }

    fun listPersonal(currentUser: CurrentUser): List<IntegrationSyncRun> = repository.listForUser(currentUser.userId, 50)

    fun listPlatform(currentUser: CurrentUser): List<IntegrationSyncRun> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW)
        return repository.listPlatform(100)
    }

    fun issues(
        runId: UUID,
        currentUser: CurrentUser,
    ): List<IntegrationSyncIssue> {
        val run =
            repository.find(runId) ?: throw NotFoundException("INTEGRATION_SYNC_NOT_FOUND", "The integration sync run could not be found.")
        when (run.ownerType) {
            IntegrationOwnerType.PLATFORM ->
                authorizationService.requirePlatformCapability(
                    currentUser,
                    Capabilities.PLATFORM_INTEGRATION_VIEW,
                )
            IntegrationOwnerType.ORGANIZATION -> membershipService.requireManagerRole(requireNotNull(run.organizationId), currentUser)
            IntegrationOwnerType.USER ->
                if (run.userId != currentUser.userId && !currentUser.platformAdministrator) {
                    throw NotFoundException("INTEGRATION_SYNC_NOT_FOUND", "The integration sync run could not be found.")
                }
        }
        return repository.listIssues(runId)
    }
}
