package com.leaguelift.reconciliation.domain

import java.time.Instant
import java.util.UUID

enum class ReconciliationRunStatus { RUNNING, COMPLETED, FAILED }
enum class ReconciliationSeverity { HIGH, MEDIUM, LOW }

data class ReconciliationRun(
    val id: UUID,
    val organizationId: UUID,
    val status: ReconciliationRunStatus,
    val issueCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val startedByUserId: UUID,
    val startedAt: Instant,
    val completedAt: Instant?,
)

data class ReconciliationIssue(
    val id: UUID,
    val reconciliationRunId: UUID,
    val organizationId: UUID,
    val issueType: String,
    val severity: ReconciliationSeverity,
    val resourceType: String,
    val resourceId: UUID?,
    val title: String,
    val detail: String,
    val actionPath: String?,
    val createdAt: Instant,
)

data class NewReconciliationIssue(
    val issueType: String,
    val severity: ReconciliationSeverity,
    val resourceType: String,
    val resourceId: UUID?,
    val title: String,
    val detail: String,
    val actionPath: String?,
)

data class ReconciliationResult(
    val run: ReconciliationRun,
    val issues: List<ReconciliationIssue>,
)
