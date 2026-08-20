package com.rally26.credit.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.domain.CreditExpirationPolicy
import com.rally26.credit.domain.CreditSourceType
import com.rally26.credit.domain.CreditTransferStatus
import com.rally26.credit.domain.FamilyCreditGrant
import com.rally26.credit.domain.FamilyCreditGrantStatus
import com.rally26.credit.domain.FamilyCreditTransfer
import com.rally26.credit.domain.OrganizationCreditSettings
import com.rally26.credit.persistence.FamilyCreditApplicationRepository
import com.rally26.credit.persistence.FamilyCreditGrantRepository
import com.rally26.credit.persistence.FamilyCreditTransferRepository
import com.rally26.credit.persistence.OrganizationCreditSettingsRepository
import com.rally26.fee.application.FeeService
import com.rally26.fee.domain.AdjustmentType
import com.rally26.fee.domain.FeeAdjustment
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.subscription.application.PlanEntitlementService
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

class FamilyCreditServiceTest {
    private val grantRepository = mockk<FamilyCreditGrantRepository>()
    private val applicationRepository = mockk<FamilyCreditApplicationRepository>()
    private val transferRepository = mockk<FamilyCreditTransferRepository>()
    private val settingsRepository = mockk<OrganizationCreditSettingsRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val feeService = mockk<FeeService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val membershipService = mockk<MembershipService>()
    private val planEntitlementService = mockk<PlanEntitlementService>()
    private val auditService = mockk<AuditService>()
    private val service =
        FamilyCreditService(
            grantRepository,
            applicationRepository,
            transferRepository,
            settingsRepository,
            householdRepository,
            feeService,
            authorizationService,
            membershipService,
            planEntitlementService,
            auditService,
        )

    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val otherHouseholdId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "sarah.johnson@example.com", "Sarah Johnson")

    private fun settings(
        percent: Int = 1000,
        policy: CreditExpirationPolicy = CreditExpirationPolicy.ROLLOVER,
        months: Int? = null,
        p2p: Boolean = false,
    ) = OrganizationCreditSettings(UUID.randomUUID(), orgId, percent, policy, months, p2p, Instant.now())

    private fun grant(
        remainingMinor: Long,
        amountMinor: Long = remainingMinor,
    ) = FamilyCreditGrant(
        UUID.randomUUID(),
        orgId,
        householdId,
        amountMinor,
        remainingMinor,
        "USD",
        FamilyCreditGrantStatus.AVAILABLE,
        CreditSourceType.CAMPAIGN_ATTRIBUTION,
        UUID.randomUUID(),
        Instant.now(),
        Instant.now(),
        null,
        Instant.now(),
    )

    @Test
    fun `grantForContribution computes the grant from the org's configured credit percent`() {
        every { settingsRepository.getOrCreateDefault(orgId) } returns settings(percent = 1500)
        every { auditService.record(null, orgId, "family_credit.granted", "family_credit_grant", any()) } just runs
        every {
            grantRepository.insert(
                orgId,
                householdId,
                1500L,
                "USD",
                FamilyCreditGrantStatus.AVAILABLE,
                CreditSourceType.CAMPAIGN_ATTRIBUTION,
                any(),
                null,
            )
        } returns grant(1500L)

        val result = service.grantForContribution(orgId, householdId, UUID.randomUUID(), 10_000L, "USD")

        assertEquals(1500L, result.remainingMinor)
    }

    @Test
    fun `applyToFee rejects amount exceeding available balance`() {
        every { grantRepository.listAvailableForHousehold(orgId, householdId) } returns listOf(grant(500L))

        assertFailsWith<ValidationException> {
            service.applyToFee(orgId, householdId, UUID.randomUUID(), 600L, currentUser)
        }
    }

    @Test
    fun `applyToFee consumes multiple grants FIFO and records one application row per grant`() {
        val grant1 = grant(300L)
        val grant2 = grant(400L)
        every { grantRepository.listAvailableForHousehold(orgId, householdId) } returns listOf(grant1, grant2)
        val assignmentId = UUID.randomUUID()
        val adjustment =
            FeeAdjustment(
                UUID.randomUUID(),
                orgId,
                assignmentId,
                householdId,
                AdjustmentType.CREDIT,
                500L,
                "USD",
                "x",
                currentUser.userId,
                null,
                null,
                null,
                Instant.now(),
            )
        every { feeService.applyCreditFromHousehold(orgId, assignmentId, householdId, 500L, currentUser) } returns (adjustment to mockk())
        every { grantRepository.decrementRemaining(grant1.id, orgId, 300L) } returns 1
        every { grantRepository.decrementRemaining(grant2.id, orgId, 200L) } returns 1
        every { applicationRepository.insert(orgId, grant1.id, adjustment.id, 300L, currentUser.userId) } returns mockk()
        every { applicationRepository.insert(orgId, grant2.id, adjustment.id, 200L, currentUser.userId) } returns mockk()
        every { auditService.record(currentUser.userId, orgId, "family_credit.applied", "fee_adjustment", adjustment.id) } just runs

        service.applyToFee(orgId, householdId, assignmentId, 500L, currentUser)

        verify(exactly = 1) { grantRepository.decrementRemaining(grant1.id, orgId, 300L) }
        verify(exactly = 1) { grantRepository.decrementRemaining(grant2.id, orgId, 200L) }
    }

    @Test
    fun `transfer is rejected when the organization has not enabled P2P transfer`() {
        every { settingsRepository.getOrCreateDefault(orgId) } returns settings(p2p = false)

        assertFailsWith<ValidationException> {
            service.transfer(orgId, householdId, otherHouseholdId, 500L, currentUser)
        }
    }

    @Test
    fun `transfer requires an exact guardian relationship, not just household capability`() {
        every { settingsRepository.getOrCreateDefault(orgId) } returns settings(p2p = true)
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns false

        assertFailsWith<ForbiddenException> {
            service.transfer(orgId, householdId, otherHouseholdId, 500L, currentUser)
        }
    }

    private fun transfer(
        status: CreditTransferStatus = CreditTransferStatus.PENDING,
        from: UUID = householdId,
        to: UUID = otherHouseholdId,
    ) = FamilyCreditTransfer(UUID.randomUUID(), orgId, from, to, 500L, currentUser.userId, status, null, null, Instant.now(), null)

    private fun otherHousehold() =
        Household(
            id = otherHouseholdId,
            organizationId = orgId,
            displayName = "Other Family",
            contactEmail = null,
            contactPhone = null,
            notes = null,
            emailRemindersOptOut = false,
            smsRemindersOptIn = false,
            status = HouseholdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `transfer holds the sender's credit as PENDING without granting the receiver yet`() {
        every { settingsRepository.getOrCreateDefault(orgId) } returns settings(p2p = true)
        every { authorizationService.hasGuardianRelationship(orgId, householdId, currentUser) } returns true
        every { householdRepository.findById(otherHouseholdId, orgId) } returns otherHousehold()
        val existingGrant = grant(500L)
        every { grantRepository.listAvailableForHousehold(orgId, householdId) } returns listOf(existingGrant)
        every { grantRepository.decrementRemaining(existingGrant.id, orgId, 500L) } returns 1
        every { transferRepository.insert(orgId, householdId, otherHouseholdId, 500L, currentUser.userId) } returns transfer()
        every { auditService.record(currentUser.userId, orgId, "family_credit.transfer_requested", "family_credit_transfer", any()) } just
            runs

        val result = service.transfer(orgId, householdId, otherHouseholdId, 500L, currentUser)

        assertEquals(CreditTransferStatus.PENDING, result.status)
        verify(exactly = 1) { grantRepository.decrementRemaining(existingGrant.id, orgId, 500L) }
        verify(exactly = 0) { grantRepository.insert(orgId, otherHouseholdId, any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `approveTransfer grants the receiver and marks the transfer approved`() {
        val pending = transfer()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns mockk()
        every { transferRepository.findById(pending.id, orgId) } returns pending andThen
            pending.copy(status = CreditTransferStatus.APPROVED)
        every { grantRepository.listForHousehold(orgId, householdId) } returns listOf(grant(500L))
        every {
            grantRepository.insert(
                orgId,
                otherHouseholdId,
                500L,
                "USD",
                FamilyCreditGrantStatus.AVAILABLE,
                CreditSourceType.P2P_TRANSFER,
                pending.id,
                null,
            )
        } returns grant(500L).copy(householdId = otherHouseholdId, sourceType = CreditSourceType.P2P_TRANSFER)
        every { transferRepository.review(pending.id, orgId, CreditTransferStatus.APPROVED, currentUser.userId, null) } returns 1
        every {
            auditService.record(
                currentUser.userId,
                orgId,
                "family_credit.transfer_approved",
                "family_credit_transfer",
                pending.id,
            )
        } just
            runs

        val result = service.approveTransfer(orgId, pending.id, currentUser)

        assertEquals(CreditTransferStatus.APPROVED, result.status)
        verify(exactly = 1) {
            grantRepository.insert(
                orgId,
                otherHouseholdId,
                500L,
                "USD",
                FamilyCreditGrantStatus.AVAILABLE,
                CreditSourceType.P2P_TRANSFER,
                pending.id,
                null,
            )
        }
    }

    @Test
    fun `rejectTransfer restores the sender's credit and marks the transfer rejected`() {
        val pending = transfer()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns mockk()
        every { transferRepository.findById(pending.id, orgId) } returns pending andThen
            pending.copy(status = CreditTransferStatus.REJECTED)
        every { grantRepository.listForHousehold(orgId, otherHouseholdId) } returns listOf(grant(0L).copy(householdId = otherHouseholdId))
        every {
            grantRepository.insert(
                orgId,
                householdId,
                500L,
                "USD",
                FamilyCreditGrantStatus.AVAILABLE,
                CreditSourceType.P2P_TRANSFER,
                pending.id,
                null,
            )
        } returns grant(500L)
        every {
            transferRepository.review(
                pending.id,
                orgId,
                CreditTransferStatus.REJECTED,
                currentUser.userId,
                "Not this household",
            )
        } returns
            1
        every {
            auditService.record(
                currentUser.userId,
                orgId,
                "family_credit.transfer_rejected",
                "family_credit_transfer",
                pending.id,
            )
        } just
            runs

        val result = service.rejectTransfer(orgId, pending.id, "Not this household", currentUser)

        assertEquals(CreditTransferStatus.REJECTED, result.status)
        verify(exactly = 1) {
            grantRepository.insert(
                orgId,
                householdId,
                500L,
                "USD",
                FamilyCreditGrantStatus.AVAILABLE,
                CreditSourceType.P2P_TRANSFER,
                pending.id,
                null,
            )
        }
    }

    @Test
    fun `approveTransfer rejects a transfer that is no longer pending`() {
        val approved = transfer(status = CreditTransferStatus.APPROVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns mockk()
        every { transferRepository.findById(approved.id, orgId) } returns approved

        assertFailsWith<ConflictException> {
            service.approveTransfer(orgId, approved.id, currentUser)
        }
    }

    @Test
    fun `approveTransfer requires manager role`() {
        val pending = transfer()
        every { membershipService.requireManagerRole(orgId, currentUser) } throws ForbiddenException("FORBIDDEN", "nope")

        assertFailsWith<ForbiddenException> {
            service.approveTransfer(orgId, pending.id, currentUser)
        }
    }

    @Test
    fun `reverseForRefundedContribution revokes only remaining unapplied grant balance`() {
        val contributionId = UUID.randomUUID()
        every { grantRepository.revokeRemainingBySource(orgId, CreditSourceType.CAMPAIGN_ATTRIBUTION, contributionId) } returns 1
        every { auditService.record(null, orgId, "family_credit.revoked", "contribution", contributionId) } just runs

        service.reverseForRefundedContribution(orgId, contributionId)

        verify(exactly = 1) { grantRepository.revokeRemainingBySource(orgId, CreditSourceType.CAMPAIGN_ATTRIBUTION, contributionId) }
    }

    @Test
    fun `grantForStorefrontOrder computes the grant from the org's configured credit percent and uses STOREFRONT_ATTRIBUTION`() {
        val orderId = UUID.randomUUID()
        every { settingsRepository.getOrCreateDefault(orgId) } returns settings(percent = 1000)
        every { auditService.record(null, orgId, "family_credit.granted", "family_credit_grant", any()) } just runs
        every {
            grantRepository.insert(
                orgId,
                householdId,
                250L,
                "USD",
                FamilyCreditGrantStatus.AVAILABLE,
                CreditSourceType.STOREFRONT_ATTRIBUTION,
                orderId,
                null,
            )
        } returns grant(250L).copy(sourceType = CreditSourceType.STOREFRONT_ATTRIBUTION, sourceId = orderId)

        val result = service.grantForStorefrontOrder(orgId, householdId, orderId, 2500L, "USD")

        assertEquals(250L, result.remainingMinor)
        assertEquals(CreditSourceType.STOREFRONT_ATTRIBUTION, result.sourceType)
    }

    @Test
    fun `reverseForRefundedOrder revokes only remaining unapplied grant balance sourced from STOREFRONT_ATTRIBUTION`() {
        val orderId = UUID.randomUUID()
        every { grantRepository.revokeRemainingBySource(orgId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId) } returns 1
        every { auditService.record(null, orgId, "family_credit.revoked", "order", orderId) } just runs

        service.reverseForRefundedOrder(orgId, orderId)

        verify(exactly = 1) { grantRepository.revokeRemainingBySource(orgId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId) }
    }

    @Test
    fun `reverseForRefundedOrder does not record an audit event when nothing was revoked`() {
        val orderId = UUID.randomUUID()
        every { grantRepository.revokeRemainingBySource(orgId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId) } returns 0

        service.reverseForRefundedOrder(orgId, orderId)

        verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `findGrantForOrder looks up by STOREFRONT_ATTRIBUTION source`() {
        val orderId = UUID.randomUUID()
        val existing = grant(250L).copy(sourceType = CreditSourceType.STOREFRONT_ATTRIBUTION, sourceId = orderId)
        every { grantRepository.findBySource(orgId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId) } returns existing

        val result = service.findGrantForOrder(orgId, orderId)

        assertEquals(existing, result)
    }
}
