package com.rally26.identity.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.domain.AccountDeletionRequest
import com.rally26.identity.domain.AccountDeletionStatus
import com.rally26.identity.persistence.AccountDeletionRequestRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.persistence.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private const val GRACE_PERIOD_DAYS = 7L

/**
 * Self-service "delete my account" — App Store Guideline 5.1.1(v). Deliberately scoped
 * to a single user's own login; an Owner must transfer ownership
 * ([com.rally26.membership.application.MembershipService.transferOwnership]/
 * [com.rally26.invitation.application.OwnershipTransferInvitationService.invite]) or
 * close their organization first — this never orphans a live org. Finalization is
 * [AccountDeletionLifecycleScanner]'s job, not this service's; this only manages the
 * request/cancel lifecycle so the 7-day window stays a real, reviewable decision.
 */
@Service
class AccountDeletionService(
    private val accountDeletionRequestRepository: AccountDeletionRequestRepository,
    private val membershipRepository: MembershipRepository,
    private val auditService: AuditService,
) {
    @Transactional
    fun request(currentUser: CurrentUser): AccountDeletionRequest {
        if (accountDeletionRequestRepository.findPendingForUser(currentUser.userId) != null) {
            throw ValidationException("A deletion request is already pending for your account.")
        }
        val ownedOrganizations = membershipRepository.listActiveForUser(currentUser.userId).filter { it.role == MembershipRole.OWNER }
        if (ownedOrganizations.isNotEmpty()) {
            throw ValidationException(
                "You are the owner of an organization. Transfer ownership or close the organization before deleting your account.",
            )
        }
        val scheduledFor = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS))
        val request = accountDeletionRequestRepository.insert(currentUser.userId, scheduledFor)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = null,
            action = "account.deletion_requested",
            entityType = "account_deletion_request",
            entityId = request.id,
        )
        return request
    }

    fun findPending(currentUser: CurrentUser): AccountDeletionRequest? =
        accountDeletionRequestRepository.findPendingForUser(currentUser.userId)

    @Transactional
    fun cancel(currentUser: CurrentUser) {
        val request =
            accountDeletionRequestRepository.findPendingForUser(currentUser.userId)
                ?: throw ValidationException("No pending deletion request exists for your account.")
        accountDeletionRequestRepository.markStatus(request.id, AccountDeletionStatus.CANCELED, canceledAt = Instant.now())
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = null,
            action = "account.deletion_canceled",
            entityType = "account_deletion_request",
            entityId = request.id,
        )
    }
}
