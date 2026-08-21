package com.rally26.organization.application

import com.rally26.audit.application.AuditService
import com.rally26.organization.domain.ORGANIZATION_DELETION_SCOPE
import com.rally26.organization.domain.OrganizationDeletionRequest
import com.rally26.organization.domain.OrganizationDeletionStatus
import com.rally26.organization.persistence.OrganizationDeletionExecutorRepository
import com.rally26.organization.persistence.OrganizationDeletionRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@ConfigurationProperties(prefix = "rally26.organization.deletion.lifecycle")
data class OrganizationDeletionLifecycleProperties(
    val enabled: Boolean = true,
    val cron: String = "0 0 9 * * *",
)

private val log = LoggerFactory.getLogger(OrganizationDeletionLifecycleScanner::class.java)

/**
 * Finalizes organization-closure requests once their 7-day grace period has passed.
 * Same scheduler pattern as `foundingorg/application/FoundingPilotLifecycleScanner.kt`
 * and `identity/application/AccountDeletionLifecycleScanner.kt`.
 *
 * The DB itself can't do this cascade — 667 of this schema's 675 FK constraints are
 * `NO ACTION`, deliberately, so a direct `delete from organization` would just fail.
 * Instead: archive the 14 financial tables first (full row snapshot, see
 * [ORGANIZATION_DELETION_SCOPE]'s `financial` flag), break the one real cycle
 * (`fundraising_game` <-> `fundraising_game_entry`), then delete every table in
 * [ORGANIZATION_DELETION_SCOPE] in its precomputed dependency order (children before
 * parents — the list itself documents how it was derived). `audit_event` (all
 * partitions) and `platform_support_access` are deliberately excluded from that list —
 * founder decision this session — and the `organization` row itself is never deleted,
 * only tombstoned (`status -> ARCHIVED`, contact/address fields cleared). Messaging
 * history is handled separately again — see [OrganizationDeletionExecutorRepository.redactMessagingHistory].
 *
 * [self] is a Spring self-injection ([Lazy] breaks the circular-construction reference):
 * calling `pastDue.forEach(::finalize)` directly from [scanAndFinalize] would invoke
 * `finalize` on `this` rather than through the proxy, silently skipping `@Transactional`
 * (the classic Spring AOP self-invocation pitfall) — discovered via
 * `OrganizationDeletionCascadeIntegrationTest` when a `set local` from one statement
 * wasn't visible to the next, proving each call was running in its own auto-committed
 * mini-transaction rather than one atomic sweep. Nullable with a default so the existing
 * plain-constructor unit tests keep working unchanged (falling back to the un-proxied
 * `this`, which is fine for MockK's already-mocked collaborators).
 */
@Component
class OrganizationDeletionLifecycleScanner(
    private val organizationDeletionRequestRepository: OrganizationDeletionRequestRepository,
    private val executor: OrganizationDeletionExecutorRepository,
    private val auditService: AuditService,
    private val properties: OrganizationDeletionLifecycleProperties,
    @Lazy private val self: OrganizationDeletionLifecycleScanner? = null,
) {
    @Scheduled(cron = "\${rally26.organization.deletion.lifecycle.cron:0 0 9 * * *}")
    fun scanAndFinalize() {
        if (!properties.enabled) return
        val pastDue = organizationDeletionRequestRepository.listPendingPastDue(Instant.now())
        pastDue.forEach { (self ?: this).finalize(it) }
    }

    @Transactional
    fun finalize(request: OrganizationDeletionRequest) {
        val organizationId = request.organizationId
        val now = Instant.now()

        executor.breakFundraisingGameCycle(organizationId)

        var archivedRows = 0
        var deletedRows = 0
        for (table in ORGANIZATION_DELETION_SCOPE) {
            if (table.financial) {
                archivedRows += executor.archiveFinancialTable(organizationId, table, now)
            }
            deletedRows += executor.deleteScopedTable(organizationId, table)
        }

        executor.enableMessagingHistoryRedaction()
        val redactedRows = executor.redactMessagingHistory(organizationId)

        executor.tombstoneOrganization(organizationId, now)

        organizationDeletionRequestRepository.markStatus(request.id, OrganizationDeletionStatus.COMPLETED, completedAt = now)
        auditService.record(
            actorUserId = null,
            organizationId = organizationId,
            action = "organization.deletion_completed",
            entityType = "organization_deletion_request",
            entityId = request.id,
            metadataJson = """{"archivedRows":$archivedRows,"deletedRows":$deletedRows,"redactedRows":$redactedRows}""",
            actorType = com.rally26.audit.domain.AuditActorType.SYSTEM,
        )
        log.info(
            "Organization closure finalized for organization {}: {} rows archived, {} rows deleted, {} rows redacted",
            organizationId,
            archivedRows,
            deletedRows,
            redactedRows,
        )
    }
}
