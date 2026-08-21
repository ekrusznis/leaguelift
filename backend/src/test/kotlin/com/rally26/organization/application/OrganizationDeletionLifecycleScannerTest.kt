package com.rally26.organization.application

import com.rally26.audit.application.AuditService
import com.rally26.organization.domain.ORGANIZATION_DELETION_SCOPE
import com.rally26.organization.domain.OrganizationDeletionRequest
import com.rally26.organization.domain.OrganizationDeletionStatus
import com.rally26.organization.persistence.OrganizationDeletionExecutorRepository
import com.rally26.organization.persistence.OrganizationDeletionRequestRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class OrganizationDeletionLifecycleScannerTest {
    private val organizationDeletionRequestRepository = mockk<OrganizationDeletionRequestRepository>()
    private val executor = mockk<OrganizationDeletionExecutorRepository>(relaxed = true)
    private val auditService = mockk<AuditService>(relaxed = true)
    private val properties = OrganizationDeletionLifecycleProperties()
    private val scanner =
        OrganizationDeletionLifecycleScanner(organizationDeletionRequestRepository, executor, auditService, properties)

    private val organizationId = UUID.randomUUID()

    private fun sampleRequest() =
        OrganizationDeletionRequest(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            requestedByUserId = UUID.randomUUID(),
            status = OrganizationDeletionStatus.PENDING,
            requestedAt = Instant.now().minusSeconds(700000),
            scheduledFor = Instant.now().minusSeconds(1),
            canceledAt = null,
            completedAt = null,
            createdAt = Instant.now().minusSeconds(700000),
            updatedAt = Instant.now().minusSeconds(700000),
        )

    @Test
    fun `finalize breaks the cycle, sweeps every scoped table, archives financial tables, and tombstones the org`() {
        val request = sampleRequest()
        every {
            organizationDeletionRequestRepository.markStatus(
                request.id,
                OrganizationDeletionStatus.COMPLETED,
                completedAt = any(),
            )
        } returns
            1

        scanner.finalize(request)

        verify(exactly = 1) { executor.breakFundraisingGameCycle(organizationId) }
        verify(exactly = ORGANIZATION_DELETION_SCOPE.size) { executor.deleteScopedTable(organizationId, any()) }
        val financialCount = ORGANIZATION_DELETION_SCOPE.count { it.financial }
        verify(exactly = financialCount) { executor.archiveFinancialTable(organizationId, any(), any()) }
        verify(exactly = 1) { executor.tombstoneOrganization(organizationId, any()) }
        verify(exactly = 1) {
            organizationDeletionRequestRepository.markStatus(request.id, OrganizationDeletionStatus.COMPLETED, completedAt = any())
        }
        verify(exactly = 1) {
            auditService.record(
                actorUserId = null,
                organizationId = organizationId,
                action = "organization.deletion_completed",
                entityType = "organization_deletion_request",
                entityId = request.id,
                metadataJson = any(),
                actorType = com.rally26.audit.domain.AuditActorType.SYSTEM,
            )
        }
    }

    @Test
    fun `scanAndFinalize does nothing when the property is disabled`() {
        val disabled = OrganizationDeletionLifecycleProperties(enabled = false)
        val disabledScanner =
            OrganizationDeletionLifecycleScanner(organizationDeletionRequestRepository, executor, auditService, disabled)

        disabledScanner.scanAndFinalize()

        verify(exactly = 0) { organizationDeletionRequestRepository.listPendingPastDue(any()) }
    }

    @Test
    fun `scanAndFinalize finalizes every past-due pending request`() {
        val request = sampleRequest()
        every { organizationDeletionRequestRepository.listPendingPastDue(any()) } returns listOf(request)
        every {
            organizationDeletionRequestRepository.markStatus(
                request.id,
                OrganizationDeletionStatus.COMPLETED,
                completedAt = any(),
            )
        } returns
            1

        scanner.scanAndFinalize()

        verify(exactly = 1) { executor.tombstoneOrganization(organizationId, any()) }
    }
}
