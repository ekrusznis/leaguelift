package com.leaguelift.reconciliation.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.reconciliation.domain.NewReconciliationIssue
import com.leaguelift.reconciliation.domain.ReconciliationIssue
import com.leaguelift.reconciliation.domain.ReconciliationRun
import com.leaguelift.reconciliation.domain.ReconciliationRunStatus
import com.leaguelift.reconciliation.domain.ReconciliationSeverity
import com.leaguelift.reconciliation.persistence.ReconciliationRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ReconciliationServiceTest {
    private val repository = mockk<ReconciliationRepository>()
    private val membership = mockk<MembershipService>()
    private val audit = mockk<AuditService>()
    private val service = ReconciliationService(repository, membership, audit)
    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val now = Instant.parse("2026-08-01T16:00:00Z")

    @Test
    fun `run persists a durable issue snapshot and severity counts`() {
        val running = ReconciliationRun(UUID.randomUUID(), organizationId, ReconciliationRunStatus.RUNNING, 0, 0, 0, 0, user.userId, now, null)
        val detected = listOf(
            NewReconciliationIssue("MISSING_LEDGER_ENTRIES", ReconciliationSeverity.HIGH, "ORDER", UUID.randomUUID(), "Missing ledger", "detail", null),
            NewReconciliationIssue("OVERDUE_INSTALLMENT", ReconciliationSeverity.MEDIUM, "FEE_INSTALLMENT", UUID.randomUUID(), "Overdue", "detail", null),
        )
        every { membership.requireManagerRole(organizationId, user) } returns mockk()
        every { repository.insertRun(organizationId, user.userId) } returns running
        every { repository.detectIssues(organizationId) } returns detected
        every { repository.insertIssue(running.id, organizationId, any()) } returnsMany detected.map { issue ->
            ReconciliationIssue(UUID.randomUUID(), running.id, organizationId, issue.issueType, issue.severity, issue.resourceType, issue.resourceId, issue.title, issue.detail, issue.actionPath, now)
        }
        val completed = running.copy(status = ReconciliationRunStatus.COMPLETED, issueCount = 2, highCount = 1, mediumCount = 1, completedAt = now)
        every { repository.completeRun(running.id, organizationId, 1, 1, 0) } returns completed
        every { audit.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.run(organizationId, user)

        assertEquals(2, result.issues.size)
        assertEquals(1, result.run.highCount)
        verify(exactly = 1) { repository.completeRun(running.id, organizationId, 1, 1, 0) }
    }
}
