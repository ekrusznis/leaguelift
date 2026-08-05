package com.rally26.reconciliation.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.reconciliation.domain.ReconciliationResult
import com.rally26.reconciliation.domain.ReconciliationRun
import com.rally26.reconciliation.domain.ReconciliationSeverity
import com.rally26.reconciliation.persistence.ReconciliationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReconciliationService(
    private val repository: ReconciliationRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    @Transactional
    fun run(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): ReconciliationResult {
        membershipService.requireManagerRole(organizationId, currentUser)
        val run = repository.insertRun(organizationId, currentUser.userId)
        val detected = repository.detectIssues(organizationId)
        val issues = detected.map { repository.insertIssue(run.id, organizationId, it) }
        val completed =
            repository.completeRun(
                run.id,
                organizationId,
                issues.count { it.severity == ReconciliationSeverity.HIGH },
                issues.count { it.severity == ReconciliationSeverity.MEDIUM },
                issues.count { it.severity == ReconciliationSeverity.LOW },
            )
        auditService.record(
            currentUser.userId,
            organizationId,
            "reconciliation.completed",
            "reconciliation_run",
            completed.id,
            "{\"issueCount\":${completed.issueCount},\"highCount\":${completed.highCount}}",
        )
        return ReconciliationResult(completed, issues)
    }

    fun latest(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): ReconciliationResult? {
        membershipService.requireManagerRole(organizationId, currentUser)
        val run = repository.latestRun(organizationId) ?: return null
        return ReconciliationResult(run, repository.listIssues(organizationId, run.id))
    }

    fun get(
        organizationId: UUID,
        runId: UUID,
        currentUser: CurrentUser,
    ): ReconciliationResult {
        membershipService.requireManagerRole(organizationId, currentUser)
        val run =
            repository.findRun(organizationId, runId)
                ?: throw NotFoundException("RECONCILIATION_RUN_NOT_FOUND", "The reconciliation run could not be found.")
        return ReconciliationResult(run, repository.listIssues(organizationId, run.id))
    }

    fun list(
        organizationId: UUID,
        offset: Int,
        limit: Int,
        currentUser: CurrentUser,
    ): List<ReconciliationRun> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.listRuns(organizationId, offset.coerceAtLeast(0), limit.coerceIn(1, 100))
    }

    fun count(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireManagerRole(organizationId, currentUser)
        return repository.countRuns(organizationId)
    }
}
