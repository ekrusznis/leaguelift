package com.leaguelift.integration.core.web

import com.leaguelift.integration.core.domain.IntegrationSyncIssue
import com.leaguelift.integration.core.domain.IntegrationSyncRun
import java.time.Instant
import java.util.UUID

data class IntegrationSyncRunResponse(
    val id: UUID,
    val connectionId: UUID?,
    val provider: String,
    val ownerType: String,
    val organizationId: UUID?,
    val userId: UUID?,
    val direction: String,
    val trigger: String,
    val status: String,
    val discoveredCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val rateLimitRemaining: Int?,
    val rateLimitResetsAt: Instant?,
    val errorCode: String?,
    val errorMessage: String?,
    val requestedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
)

data class IntegrationSyncIssueResponse(
    val id: UUID,
    val syncRunId: UUID,
    val severity: String,
    val code: String,
    val message: String,
    val externalEntityType: String?,
    val externalEntityId: String?,
    val internalEntityType: String?,
    val internalEntityId: UUID?,
    val retryable: Boolean,
    val createdAt: Instant,
)

fun IntegrationSyncRun.toResponse() = IntegrationSyncRunResponse(
    id, connectionId, provider.name, ownerType.name, organizationId, userId,
    direction.name, trigger.name, status.name, discoveredCount, createdCount,
    updatedCount, skippedCount, failedCount, rateLimitRemaining, rateLimitResetsAt,
    errorCode, errorMessage, requestedAt, startedAt, completedAt,
)

fun IntegrationSyncIssue.toResponse() = IntegrationSyncIssueResponse(
    id, syncRunId, severity.name, code, message, externalEntityType, externalEntityId,
    internalEntityType, internalEntityId, retryable, createdAt,
)
