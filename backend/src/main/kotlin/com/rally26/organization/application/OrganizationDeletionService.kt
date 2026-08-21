package com.rally26.organization.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.organization.domain.OrganizationDeletionRequest
import com.rally26.organization.domain.OrganizationDeletionStatus
import com.rally26.organization.persistence.OrganizationDeletionRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val GRACE_PERIOD_DAYS = 7L

/**
 * Owner-only "close this organization" — the org-wide sibling of
 * [com.rally26.identity.application.AccountDeletionService]. Reachable as an
 * alternative to [MembershipService.transferOwnership]/
 * [com.rally26.invitation.application.OwnershipTransferInvitationService.invite] from
 * the same Settings entry point. Finalization is
 * [OrganizationDeletionLifecycleScanner]'s job.
 */
@Service
class OrganizationDeletionService(
    private val organizationDeletionRequestRepository: OrganizationDeletionRequestRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {
    @Transactional
    fun request(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationDeletionRequest {
        membershipService.requireOwnerRole(organizationId, currentUser)
        if (organizationDeletionRequestRepository.findPendingForOrganization(organizationId) != null) {
            throw ValidationException("A closure request is already pending for this organization.")
        }
        val scheduledFor = Instant.now().plus(Duration.ofDays(GRACE_PERIOD_DAYS))
        val request = organizationDeletionRequestRepository.insert(organizationId, currentUser.userId, scheduledFor)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "organization.deletion_requested",
            entityType = "organization_deletion_request",
            entityId = request.id,
        )
        return request
    }

    fun findPending(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationDeletionRequest? {
        membershipService.requireOwnerRole(organizationId, currentUser)
        return organizationDeletionRequestRepository.findPendingForOrganization(organizationId)
    }

    @Transactional
    fun cancel(
        organizationId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val request =
            organizationDeletionRequestRepository.findPendingForOrganization(organizationId)
                ?: throw ValidationException("No pending closure request exists for this organization.")
        organizationDeletionRequestRepository.markStatus(request.id, OrganizationDeletionStatus.CANCELED, canceledAt = Instant.now())
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "organization.deletion_canceled",
            entityType = "organization_deletion_request",
            entityId = request.id,
        )
    }
}
