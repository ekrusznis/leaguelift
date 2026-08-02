package com.leaguelift.reconciliation.web

import com.leaguelift.reconciliation.domain.ReconciliationIssue
import com.leaguelift.reconciliation.domain.ReconciliationResult
import com.leaguelift.reconciliation.domain.ReconciliationRun
import java.time.Instant
import java.util.UUID

data class ReconciliationRunResponse(
    val id: UUID,
    val organizationId: UUID,
    val status: String,
    val issueCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val startedByUserId: UUID,
    val startedAt: Instant,
    val completedAt: Instant?,
)

data class ReconciliationIssueResponse(
    val id: UUID,
    val issueType: String,
    val severity: String,
    val resourceType: String,
    val resourceId: UUID?,
    val title: String,
    val detail: String,
    val actionPath: String?,
    val createdAt: Instant,
)

data class ReconciliationResultResponse(
    val run: ReconciliationRunResponse,
    val issues: List<ReconciliationIssueResponse>,
)

fun ReconciliationRun.toResponse() = ReconciliationRunResponse(
    id, organizationId, status.name, issueCount, highCount, mediumCount, lowCount,
    startedByUserId, startedAt, completedAt,
)

fun ReconciliationIssue.toResponse() = ReconciliationIssueResponse(
    id, issueType, severity.name, resourceType, resourceId, title, detail, actionPath, createdAt,
)

fun ReconciliationResult.toResponse() = ReconciliationResultResponse(run.toResponse(), issues.map { it.toResponse() })
