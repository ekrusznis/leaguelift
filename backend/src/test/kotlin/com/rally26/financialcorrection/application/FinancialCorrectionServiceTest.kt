package com.rally26.financialcorrection.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.finance.domain.PaymentSource
import com.rally26.financialcorrection.domain.FinancialCorrection
import com.rally26.financialcorrection.domain.FinancialCorrectionTargetType
import com.rally26.financialcorrection.domain.FinancialCorrectionType
import com.rally26.financialcorrection.persistence.FinancialCorrectionRepository
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.fundraising.infra.StripeCheckoutClient
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.membership.application.MembershipService
import com.rally26.offlinefinance.domain.OfflineFinancialRecord
import com.rally26.offlinefinance.domain.OfflineFinancialRecordType
import com.rally26.offlinefinance.domain.OfflinePaymentMethod
import com.rally26.offlinefinance.domain.OfflineVerificationStatus
import com.rally26.offlinefinance.persistence.OfflineFinancialRecordRepository
import com.rally26.order.infra.StripeOrderCheckoutClient
import com.rally26.order.persistence.OrderItemRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.sponsorship.infra.StripeSponsorshipCheckoutClient
import com.rally26.sponsorship.persistence.SponsorshipRepository
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
import kotlin.test.assertFalse

class FinancialCorrectionServiceTest {
    private val repository = mockk<FinancialCorrectionRepository>()
    private val contributions = mockk<ContributionRepository>()
    private val sponsorships = mockk<SponsorshipRepository>()
    private val orders = mockk<OrderRepository>()
    private val orderItems = mockk<OrderItemRepository>()
    private val offlineRecords = mockk<OfflineFinancialRecordRepository>()
    private val contributionStripe = mockk<StripeCheckoutClient>()
    private val sponsorshipStripe = mockk<StripeSponsorshipCheckoutClient>()
    private val orderStripe = mockk<StripeOrderCheckoutClient>()
    private val ledger = mockk<LedgerService>()
    private val membership = mockk<MembershipService>()
    private val audit = mockk<AuditService>()
    private val service =
        FinancialCorrectionService(
            repository,
            contributions,
            sponsorships,
            orders,
            orderItems,
            offlineRecords,
            contributionStripe,
            sponsorshipStripe,
            orderStripe,
            ledger,
            membership,
            audit,
        )

    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val contributionId = UUID.randomUUID()
    private val confirmedAt = Instant.now().minusSeconds(3600)

    @Test
    fun `preview subtracts prior partial corrections from the refundable amount`() {
        every { membership.requireManagerRole(organizationId, user) } returns mockk()
        every { contributions.findById(contributionId) } returns contribution()
        every { repository.sumByTarget(organizationId, FinancialCorrectionTargetType.CONTRIBUTION, contributionId) } returns 2000

        val result =
            service.preview(
                organizationId,
                FinancialCorrectionTargetType.CONTRIBUTION,
                contributionId,
                3000,
                "Partial supporter refund",
                user,
            )

        assertEquals(2000, result.previouslyCorrectedMinor)
        assertEquals(3000, result.requestedAmountMinor)
        assertEquals(5000, result.remainingAfterMinor)
        assertFalse(result.willFullyCorrect)
    }

    @Test
    fun `execute performs an idempotent provider refund and appends correction ledger entries`() {
        val previewReason = "Duplicate contribution"
        every { membership.requireManagerRole(organizationId, user) } returns mockk()
        every { contributions.findById(contributionId) } returns contribution()
        every { repository.findByIdempotencyKey(organizationId, "correction-key-123") } returns null
        every { repository.lockTarget(organizationId, FinancialCorrectionTargetType.CONTRIBUTION, contributionId) } just runs
        every { repository.sumByTarget(organizationId, FinancialCorrectionTargetType.CONTRIBUTION, contributionId) } returns 0
        val preview =
            service.preview(
                organizationId,
                FinancialCorrectionTargetType.CONTRIBUTION,
                contributionId,
                null,
                previewReason,
                user,
            )
        every {
            contributionStripe.createRefund(
                "pi_test_123",
                10_000,
                "financial-correction:$organizationId:correction-key-123",
            )
        } returns "re_test_123"
        val correction =
            FinancialCorrection(
                UUID.randomUUID(),
                organizationId,
                FinancialCorrectionType.REFUND,
                FinancialCorrectionTargetType.CONTRIBUTION,
                contributionId,
                10_000,
                "USD",
                previewReason,
                "re_test_123",
                preview.confirmationHash,
                "correction-key-123",
                user.userId,
                Instant.now(),
            )
        every {
            repository.insert(
                organizationId,
                FinancialCorrectionType.REFUND,
                FinancialCorrectionTargetType.CONTRIBUTION,
                contributionId,
                10_000,
                "USD",
                previewReason,
                "re_test_123",
                preview.confirmationHash,
                "correction-key-123",
                user.userId,
            )
        } returns correction
        every { ledger.recordCorrectionRefund(organizationId, correction.id, 10_000, "USD", "re_test_123") } just runs
        every { contributions.markRefunded(contributionId) } returns 1
        every { audit.record(any(), any(), any(), any(), any(), any()) } just runs

        val result =
            service.execute(
                organizationId,
                FinancialCorrectionTargetType.CONTRIBUTION,
                contributionId,
                null,
                previewReason,
                preview.confirmationHash,
                "correction-key-123",
                user,
            )

        assertEquals(correction.id, result.id)
        verify(exactly = 1) { repository.lockTarget(organizationId, FinancialCorrectionTargetType.CONTRIBUTION, contributionId) }
        verify(exactly = 1) {
            contributionStripe.createRefund(
                "pi_test_123",
                10_000,
                "financial-correction:$organizationId:correction-key-123",
            )
        }
        verify(exactly = 1) { ledger.recordCorrectionRefund(organizationId, correction.id, 10_000, "USD", "re_test_123") }
        verify(exactly = 1) { contributions.markRefunded(contributionId) }
    }

    @Test
    fun `verified offline records reject partial reversals`() {
        val offlineId = UUID.randomUUID()
        every { membership.requireManagerRole(organizationId, user) } returns mockk()
        every { offlineRecords.findById(offlineId, organizationId) } returns offlineRecord(offlineId)
        every { repository.sumByTarget(organizationId, FinancialCorrectionTargetType.OFFLINE_FINANCIAL_RECORD, offlineId) } returns 0

        assertFailsWith<ValidationException> {
            service.preview(
                organizationId,
                FinancialCorrectionTargetType.OFFLINE_FINANCIAL_RECORD,
                offlineId,
                5000,
                "Incorrect offline receipt",
                user,
            )
        }
    }

    private fun contribution() =
        Contribution(
            id = contributionId,
            organizationId = organizationId,
            campaignId = UUID.randomUUID(),
            amountMinor = 10_000,
            currency = "USD",
            supporterName = "Supporter",
            isAnonymous = false,
            supporterEmail = "supporter@example.com",
            status = ContributionStatus.CONFIRMED,
            stripeCheckoutSessionId = "cs_test_123",
            stripePaymentIntentId = "pi_test_123",
            confirmedAt = confirmedAt,
            refundedAt = null,
            createdAt = confirmedAt.minusSeconds(60),
            paymentSource = PaymentSource.STRIPE,
        )

    private fun offlineRecord(id: UUID) =
        OfflineFinancialRecord(
            id = id,
            organizationId = organizationId,
            recordType = OfflineFinancialRecordType.CONTRIBUTION,
            recordId = UUID.randomUUID(),
            displayLabel = "Offline contribution",
            paymentMethod = OfflinePaymentMethod.CHECK,
            verificationStatus = OfflineVerificationStatus.VERIFIED,
            amountMinor = 10_000,
            currency = "USD",
            payerName = "Supporter",
            payerEmail = null,
            paymentReference = "check-100",
            receivedAt = confirmedAt,
            internalNotes = null,
            idempotencyKey = "offline-key",
            duplicateFingerprint = "a".repeat(64),
            sendAcknowledgement = false,
            recordedByUserId = user.userId,
            verifiedByUserId = user.userId,
            verifiedAt = confirmedAt,
            createdAt = confirmedAt,
            updatedAt = confirmedAt,
        )
}
