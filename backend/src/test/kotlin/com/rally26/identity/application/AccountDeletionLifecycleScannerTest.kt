package com.rally26.identity.application

import com.rally26.audit.application.AuditService
import com.rally26.identity.domain.AccountDeletionRequest
import com.rally26.identity.domain.AccountDeletionStatus
import com.rally26.identity.persistence.AccountDeletionRequestRepository
import com.rally26.identityintegrity.persistence.GuardianRelationshipRow
import com.rally26.identityintegrity.persistence.IdentityResolutionRepository
import com.rally26.identityintegrity.persistence.MembershipRow
import com.rally26.identityintegrity.persistence.MessageThreadMembershipRow
import com.rally26.identityintegrity.persistence.RoleAssignmentRow
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

class AccountDeletionLifecycleScannerTest {
    private val accountDeletionRequestRepository = mockk<AccountDeletionRequestRepository>()
    private val repository = mockk<IdentityResolutionRepository>()
    private val auditService =
        mockk<AuditService> {
            every { record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
        }
    private val properties = AccountDeletionLifecycleProperties()
    private val scanner = AccountDeletionLifecycleScanner(accountDeletionRequestRepository, repository, auditService, properties)

    private val userId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()

    @Test
    fun `finalize revokes memberships and role grants, closes threads, anonymizes recipients and the user, then completes`() {
        val request =
            AccountDeletionRequest(
                id = UUID.randomUUID(),
                userId = userId,
                status = AccountDeletionStatus.PENDING,
                requestedAt = Instant.now().minusSeconds(700000),
                scheduledFor = Instant.now().minusSeconds(1),
                canceledAt = null,
                completedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val membership = MembershipRow(UUID.randomUUID(), orgId, "ADMINISTRATOR", "ACTIVE")
        val roleAssignment = RoleAssignmentRow(UUID.randomUUID(), orgId, "TEAM", UUID.randomUUID(), "TEAM_MANAGER")
        val guardianRelationship = GuardianRelationshipRow(UUID.randomUUID(), orgId, UUID.randomUUID(), UUID.randomUUID())
        val threadMembership =
            MessageThreadMembershipRow(UUID.randomUUID(), orgId, UUID.randomUUID(), "GUARDIAN", UUID.randomUUID(), null, "TARGETED", true)

        every { repository.memberships(userId) } returns listOf(membership)
        every { repository.revokeMembership(membership.id, any()) } returns 1
        every { repository.activeRoleAssignments(userId) } returns listOf(roleAssignment)
        every { repository.revokeRoleAssignment(roleAssignment.id, any()) } returns 1
        every { repository.activeGuardianRelationships(userId) } returns listOf(guardianRelationship)
        every { repository.revokeGuardianRelationship(guardianRelationship.id, any()) } returns 1
        every { repository.activeMessageThreadMemberships(userId) } returns listOf(threadMembership)
        every { repository.closeMessageThreadMembership(threadMembership.id, any()) } returns 1
        every { repository.invalidateAuthenticationTokens(userId, any()) } returns 2
        every { repository.anonymizeMessageRecipientDisplayName(userId, any()) } returns 1
        every { repository.anonymizeAnnouncementRecipientDisplayName(userId, any()) } returns 1
        every { repository.retireDeletedUser(userId, any(), any()) } returns 1
        every { accountDeletionRequestRepository.markStatus(request.id, AccountDeletionStatus.COMPLETED, completedAt = any()) } returns 1

        scanner.finalize(request)

        verify(exactly = 1) { repository.revokeMembership(membership.id, any()) }
        verify(exactly = 1) { repository.revokeRoleAssignment(roleAssignment.id, any()) }
        verify(exactly = 1) { repository.revokeGuardianRelationship(guardianRelationship.id, any()) }
        verify(exactly = 1) { repository.closeMessageThreadMembership(threadMembership.id, any()) }
        verify(exactly = 1) { repository.invalidateAuthenticationTokens(userId, any()) }
        verify(exactly = 1) { repository.anonymizeMessageRecipientDisplayName(userId, any()) }
        verify(exactly = 1) { repository.anonymizeAnnouncementRecipientDisplayName(userId, any()) }
        verify(exactly = 1) { repository.retireDeletedUser(userId, "deleted-$userId@deleted.rally26.internal", any()) }
        verify(
            exactly = 1,
        ) { accountDeletionRequestRepository.markStatus(request.id, AccountDeletionStatus.COMPLETED, completedAt = any()) }
    }

    @Test
    fun `scanAndFinalize does nothing when disabled`() {
        val disabledScanner =
            AccountDeletionLifecycleScanner(
                accountDeletionRequestRepository,
                repository,
                auditService,
                AccountDeletionLifecycleProperties(enabled = false),
            )

        disabledScanner.scanAndFinalize()

        verify(exactly = 0) { accountDeletionRequestRepository.listPendingPastDue(any()) }
    }
}
