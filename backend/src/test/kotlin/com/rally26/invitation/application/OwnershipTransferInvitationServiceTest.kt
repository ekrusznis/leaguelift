package com.rally26.invitation.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.invitation.domain.OwnershipTransferInvitation
import com.rally26.invitation.domain.OwnershipTransferInvitationStatus
import com.rally26.invitation.persistence.OwnershipTransferInvitationRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
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

class OwnershipTransferInvitationServiceTest {
    private val ownershipTransferInvitationRepository = mockk<OwnershipTransferInvitationRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService =
        mockk<AuditService> {
            every { record(any(), any(), any(), any(), any()) } just runs
        }
    private val outboxWriter =
        mockk<OutboxWriter> {
            every { write(any(), any(), any(), any(), any()) } just runs
        }
    private val service =
        OwnershipTransferInvitationService(ownershipTransferInvitationRepository, membershipService, auditService, outboxWriter)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    // --- invite ---

    @Test
    fun `invite requires the caller to be the owner`() {
        every { membershipService.requireOwnerRole(orgId, currentUser) } throws
            ForbiddenException("OWNER_ACTION_DENIED", "Only the organization owner can perform this action.")

        assertFailsWith<ForbiddenException> {
            service.invite(orgId, "new-owner@example.com", currentUser)
        }
    }

    @Test
    fun `invite rejects inviting yourself`() {
        every { membershipService.requireOwnerRole(orgId, currentUser) } returns sampleMembership(MembershipRole.OWNER)

        assertFailsWith<ValidationException> {
            service.invite(orgId, currentUser.email, currentUser)
        }
    }

    @Test
    fun `invite rejects a second invitation while one is already pending`() {
        every { membershipService.requireOwnerRole(orgId, currentUser) } returns sampleMembership(MembershipRole.OWNER)
        every { ownershipTransferInvitationRepository.findPendingForOrganization(orgId) } returns sampleInvitation()

        assertFailsWith<ValidationException> {
            service.invite(orgId, "new-owner@example.com", currentUser)
        }
    }

    @Test
    fun `invite succeeds and audits the action`() {
        every { membershipService.requireOwnerRole(orgId, currentUser) } returns sampleMembership(MembershipRole.OWNER)
        every { ownershipTransferInvitationRepository.findPendingForOrganization(orgId) } returns null
        every {
            ownershipTransferInvitationRepository.insert(orgId, "new-owner@example.com", currentUser.userId, any(), any())
        } returns sampleInvitation().copy(email = "new-owner@example.com")

        val result = service.invite(orgId, "new-owner@example.com", currentUser)

        assertEquals("new-owner@example.com", result.invitation.email)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "membership.ownership_transfer_invited", "ownership_transfer_invitation", any())
        }
    }

    // --- accept ---

    @Test
    fun `accept transfers ownership to the accepting user`() {
        val token = "raw-token"
        val tokenHash = sha256HexForTest(token)
        val invitation = sampleInvitation().copy(email = currentUser.email)
        every { ownershipTransferInvitationRepository.findByTokenHash(tokenHash) } returns invitation
        every { membershipService.finalizeOwnershipTransfer(orgId, currentUser.userId) } returns sampleMembership(MembershipRole.OWNER)
        every {
            ownershipTransferInvitationRepository.markStatus(invitation.id, OwnershipTransferInvitationStatus.ACCEPTED, any())
        } returns 1
        every { ownershipTransferInvitationRepository.findById(invitation.id) } returns
            invitation.copy(status = OwnershipTransferInvitationStatus.ACCEPTED)

        service.accept(token, currentUser)

        verify(exactly = 1) { membershipService.finalizeOwnershipTransfer(orgId, currentUser.userId) }
    }

    @Test
    fun `accept throws ForbiddenException when the invitation email does not match the caller`() {
        val token = "raw-token-mismatch"
        val tokenHash = sha256HexForTest(token)
        val invitation = sampleInvitation().copy(email = "someone-else@example.com")
        every { ownershipTransferInvitationRepository.findByTokenHash(tokenHash) } returns invitation

        assertFailsWith<ForbiddenException> {
            service.accept(token, currentUser)
        }
    }

    @Test
    fun `accept throws NotFoundException for an unknown token`() {
        every { ownershipTransferInvitationRepository.findByTokenHash(any()) } returns null

        assertFailsWith<NotFoundException> {
            service.accept("bogus-token", currentUser)
        }
    }

    @Test
    fun `accept throws ValidationException for an already-accepted invitation`() {
        val token = "raw-token-used"
        val tokenHash = sha256HexForTest(token)
        val invitation = sampleInvitation().copy(email = currentUser.email, status = OwnershipTransferInvitationStatus.ACCEPTED)
        every { ownershipTransferInvitationRepository.findByTokenHash(tokenHash) } returns invitation

        assertFailsWith<ValidationException> {
            service.accept(token, currentUser)
        }
    }

    private fun sampleMembership(role: MembershipRole) =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = role,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleInvitation() =
        OwnershipTransferInvitation(
            id = UUID.randomUUID(),
            organizationId = orgId,
            email = "invitee@example.com",
            status = OwnershipTransferInvitationStatus.PENDING,
            invitedByUserId = UUID.randomUUID(),
            expiresAt = Instant.now().plusSeconds(3600),
            acceptedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sha256HexForTest(value: String): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
