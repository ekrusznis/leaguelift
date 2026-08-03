package com.leaguelift.integration.core.domain

import java.time.Instant
import java.util.UUID

enum class IntegrationSyncDirection { READ, WRITE, WEBHOOK, HEALTH }
enum class IntegrationSyncTrigger { MANUAL, SCHEDULED, OUTBOX, WEBHOOK, STUB }
enum class IntegrationSyncStatus { QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, CANCELLED }
enum class IntegrationSyncIssueSeverity { INFO, WARNING, ERROR }

data class IntegrationSyncRun(
    val id: UUID,
    val connectionId: UUID?,
    val provider: IntegrationProvider,
    val ownerType: IntegrationOwnerType,
    val organizationId: UUID?,
    val userId: UUID?,
    val direction: IntegrationSyncDirection,
    val trigger: IntegrationSyncTrigger,
    val status: IntegrationSyncStatus,
    val idempotencyKey: String?,
    val cursor: String?,
    val checkpointJson: String,
    val discoveredCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val rateLimitRemaining: Int?,
    val rateLimitResetsAt: Instant?,
    val errorCode: String?,
    val errorMessage: String?,
    val requestedByUserId: UUID?,
    val requestedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
)

data class IntegrationSyncIssue(
    val id: UUID,
    val syncRunId: UUID,
    val severity: IntegrationSyncIssueSeverity,
    val code: String,
    val message: String,
    val externalEntityType: String?,
    val externalEntityId: String?,
    val internalEntityType: String?,
    val internalEntityId: UUID?,
    val retryable: Boolean,
    val detailsJson: String,
    val createdAt: Instant,
)

data class IntegrationSyncSummary(
    val discovered: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
)
