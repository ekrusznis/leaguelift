package com.rally26.identity.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.domain.AccountDeletionRequest
import com.rally26.identity.domain.AccountDeletionStatus
import com.rally26.identity.persistence.AccountDeletionRequestRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.membership.persistence.MembershipRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountDeletionServiceTest {
    private val accountDeletionRequestRepository = mockk<AccountDeletionRequestRepository>()
    private val membershipRepository = mockk<MembershipRepository>()
    private val auditService =
        mockk<AuditService> {
            every { record(any(), any(), any(), any(), any()) } just runs
        }
    private val service = AccountDeletionService(accountDeletionRequestRepository, membershipRepository, auditService)

    private val currentUser = CurrentUser(UUID.randomUUID(), "self-delete@example.com", "Self Delete")

    @Test
    fun `request rejects a second request while one is already pending`() {
        every { accountDeletionRequestRepository.findPendingForUser(currentUser.userId) } returns sampleRequest()

        assertFailsWith<ValidationException> {
            service.request(currentUser)
        }
    }

    @Test
    fun `request blocks an owner of any active organization`() {
        every { accountDeletionRequestRepository.findPendingForUser(currentUser.userId) } returns null
        every { membershipRepository.listActiveForUser(currentUser.userId) } returns
            listOf(sampleMembership(MembershipRole.OWNER))

        assertFailsWith<ValidationException> {
            service.request(currentUser)
        }
    }

    @Test
    fun `request succeeds for a non-owner and audits it`() {
        every { accountDeletionRequestRepository.findPendingForUser(currentUser.userId) } returns null
        every { membershipRepository.listActiveForUser(currentUser.userId) } returns
            listOf(sampleMembership(MembershipRole.ADMINISTRATOR))
        every { accountDeletionRequestRepository.insert(currentUser.userId, any()) } returns sampleRequest()

        val result = service.request(currentUser)

        assertEquals(AccountDeletionStatus.PENDING, result.status)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, null, "account.deletion_requested", "account_deletion_request", any())
        }
    }

    @Test
    fun `cancel throws when no pending request exists`() {
        every { accountDeletionRequestRepository.findPendingForUser(currentUser.userId) } returns null

        assertFailsWith<ValidationException> {
            service.cancel(currentUser)
        }
    }

    @Test
    fun `cancel marks the pending request canceled and audits it`() {
        val request = sampleRequest()
        every { accountDeletionRequestRepository.findPendingForUser(currentUser.userId) } returns request
        every { accountDeletionRequestRepository.markStatus(request.id, AccountDeletionStatus.CANCELED, any()) } returns 1

        service.cancel(currentUser)

        verify(exactly = 1) { accountDeletionRequestRepository.markStatus(request.id, AccountDeletionStatus.CANCELED, any()) }
        verify(exactly = 1) {
            auditService.record(currentUser.userId, null, "account.deletion_canceled", "account_deletion_request", request.id)
        }
    }

    private fun sampleRequest() =
        AccountDeletionRequest(
            id = UUID.randomUUID(),
            userId = currentUser.userId,
            status = AccountDeletionStatus.PENDING,
            requestedAt = Instant.now(),
            scheduledFor = Instant.now().plusSeconds(604800),
            canceledAt = null,
            completedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleMembership(role: MembershipRole) =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = UUID.randomUUID(),
            userId = currentUser.userId,
            role = role,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
