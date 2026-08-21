package com.rally26.organization.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.organization.domain.OrganizationDeletionRequest
import com.rally26.organization.domain.OrganizationDeletionStatus
import com.rally26.organization.persistence.OrganizationDeletionRequestRepository
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

class OrganizationDeletionServiceTest {
    private val organizationDeletionRequestRepository = mockk<OrganizationDeletionRequestRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService =
        mockk<AuditService> {
            every { record(any(), any(), any(), any(), any()) } just runs
        }
    private val service = OrganizationDeletionService(organizationDeletionRequestRepository, membershipService, auditService)

    private val organizationId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Org Owner")

    private fun ownerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            userId = currentUser.userId,
            role = MembershipRole.OWNER,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleRequest() =
        OrganizationDeletionRequest(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            requestedByUserId = currentUser.userId,
            status = OrganizationDeletionStatus.PENDING,
            requestedAt = Instant.now(),
            scheduledFor = Instant.now().plusSeconds(604800),
            canceledAt = null,
            completedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `request requires the caller to hold the owner role`() {
        every { membershipService.requireOwnerRole(organizationId, currentUser) } throws ValidationException("not owner")

        assertFailsWith<ValidationException> {
            service.request(organizationId, currentUser)
        }
    }

    @Test
    fun `request rejects a second request while one is already pending`() {
        every { membershipService.requireOwnerRole(organizationId, currentUser) } returns ownerMembership()
        every { organizationDeletionRequestRepository.findPendingForOrganization(organizationId) } returns sampleRequest()

        assertFailsWith<ValidationException> {
            service.request(organizationId, currentUser)
        }
    }

    @Test
    fun `request succeeds for an owner and audits it`() {
        every { membershipService.requireOwnerRole(organizationId, currentUser) } returns ownerMembership()
        every { organizationDeletionRequestRepository.findPendingForOrganization(organizationId) } returns null
        every { organizationDeletionRequestRepository.insert(organizationId, currentUser.userId, any()) } returns sampleRequest()

        val result = service.request(organizationId, currentUser)

        assertEquals(OrganizationDeletionStatus.PENDING, result.status)
        verify(exactly = 1) {
            auditService.record(
                currentUser.userId,
                organizationId,
                "organization.deletion_requested",
                "organization_deletion_request",
                any(),
            )
        }
    }

    @Test
    fun `cancel throws when no pending request exists`() {
        every { membershipService.requireOwnerRole(organizationId, currentUser) } returns ownerMembership()
        every { organizationDeletionRequestRepository.findPendingForOrganization(organizationId) } returns null

        assertFailsWith<ValidationException> {
            service.cancel(organizationId, currentUser)
        }
    }

    @Test
    fun `cancel marks the pending request canceled and audits it`() {
        val request = sampleRequest()
        every { membershipService.requireOwnerRole(organizationId, currentUser) } returns ownerMembership()
        every { organizationDeletionRequestRepository.findPendingForOrganization(organizationId) } returns request
        every {
            organizationDeletionRequestRepository.markStatus(request.id, OrganizationDeletionStatus.CANCELED, canceledAt = any())
        } returns 1

        service.cancel(organizationId, currentUser)

        verify(exactly = 1) {
            organizationDeletionRequestRepository.markStatus(request.id, OrganizationDeletionStatus.CANCELED, canceledAt = any())
        }
        verify(exactly = 1) {
            auditService.record(
                currentUser.userId,
                organizationId,
                "organization.deletion_canceled",
                "organization_deletion_request",
                request.id,
            )
        }
    }
}
