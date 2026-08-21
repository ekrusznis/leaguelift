package com.rally26.identity.application

import com.rally26.audit.application.AuditService
import com.rally26.identity.domain.AccountDeletionRequest
import com.rally26.identity.domain.AccountDeletionStatus
import com.rally26.identity.persistence.AccountDeletionRequestRepository
import com.rally26.identityintegrity.persistence.IdentityResolutionRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@ConfigurationProperties(prefix = "rally26.identity.account-deletion.lifecycle")
data class AccountDeletionLifecycleProperties(
    val enabled: Boolean = true,
    val cron: String = "0 30 8 * * *",
)

private val log = LoggerFactory.getLogger(AccountDeletionLifecycleScanner::class.java)

/**
 * Finalizes account-deletion requests once their 7-day grace period has passed.
 * Modeled on `foundingorg/application/FoundingPilotLifecycleScanner.kt`'s scan pattern.
 * Reuses [IdentityResolutionRepository] rather than duplicating its revoke/close
 * methods — the account-merge feature it was built for already has exactly the
 * granular per-table operations a deletion needs (membership/role-assignment/
 * guardian-relationship revoke, message-thread-membership close, auth-token
 * invalidation); this only ever calls the revoke-style methods, never the move-to-
 * target ones, since a deletion has no surviving target user.
 */
@Component
class AccountDeletionLifecycleScanner(
    private val accountDeletionRequestRepository: AccountDeletionRequestRepository,
    private val repository: IdentityResolutionRepository,
    private val auditService: AuditService,
    private val properties: AccountDeletionLifecycleProperties,
) {
    @Scheduled(cron = "\${rally26.identity.account-deletion.lifecycle.cron:0 30 8 * * *}")
    fun scanAndFinalize() {
        if (!properties.enabled) return
        val pastDue = accountDeletionRequestRepository.listPendingPastDue(Instant.now())
        pastDue.forEach(::finalize)
    }

    @Transactional
    fun finalize(request: AccountDeletionRequest) {
        val userId = request.userId
        val now = Instant.now()

        repository.memberships(userId).filter { it.status != "REVOKED" }.forEach { repository.revokeMembership(it.id, now) }
        repository.activeRoleAssignments(userId).forEach { repository.revokeRoleAssignment(it.id, now) }
        repository.activeGuardianRelationships(userId).forEach { repository.revokeGuardianRelationship(it.id, now) }
        repository.activeMessageThreadMemberships(userId).forEach { repository.closeMessageThreadMembership(it.id, now) }
        repository.invalidateAuthenticationTokens(userId, now)

        // Failsafe: message_recipient/announcement_recipient store a display-name
        // snapshot at delivery time, never re-joined to app_user — everywhere else that
        // shows a user's name (message sender, audit actor/target, membership lists) is
        // a live lookup and self-heals from the anonymization below alone.
        repository.anonymizeMessageRecipientDisplayName(userId, now)
        repository.anonymizeAnnouncementRecipientDisplayName(userId, now)

        val anonymizedEmail = "deleted-$userId@deleted.rally26.internal"
        repository.retireDeletedUser(userId, anonymizedEmail, now)

        accountDeletionRequestRepository.markStatus(request.id, AccountDeletionStatus.COMPLETED, completedAt = now)
        auditService.record(
            actorUserId = null,
            organizationId = null,
            action = "account.deletion_completed",
            entityType = "account_deletion_request",
            entityId = request.id,
            targetUserId = userId,
            actorType = com.rally26.audit.domain.AuditActorType.SYSTEM,
        )
        log.info("Account deletion finalized for user {}", userId)
    }
}
