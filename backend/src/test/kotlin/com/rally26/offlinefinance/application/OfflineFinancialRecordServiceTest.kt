package com.rally26.offlinefinance.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.finance.domain.PaymentSource
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraising.persistence.ContributionRepository
import com.rally26.ledger.application.LedgerService
import com.rally26.membership.application.MembershipService
import com.rally26.offlinefinance.domain.OfflineFinancialRecord
import com.rally26.offlinefinance.domain.OfflineFinancialRecordType
import com.rally26.offlinefinance.domain.OfflinePaymentMethod
import com.rally26.offlinefinance.domain.OfflineVerificationStatus
import com.rally26.offlinefinance.persistence.OfflineFinancialRecordRepository
import com.rally26.order.persistence.FulfillmentHistoryRepository
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.order.persistence.OrderItemRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.sponsorship.persistence.SponsorRepository
import com.rally26.sponsorship.persistence.SponsorshipPackageRepository
import com.rally26.sponsorship.persistence.SponsorshipRepository
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.store.persistence.StoreRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineFinancialRecordServiceTest {
    private val repository = mockk<OfflineFinancialRecordRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val contributionRepository = mockk<ContributionRepository>()
    private val sponsorshipPackageRepository = mockk<SponsorshipPackageRepository>()
    private val sponsorRepository = mockk<SponsorRepository>()
    private val sponsorshipRepository = mockk<SponsorshipRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val productVariantRepository = mockk<ProductVariantRepository>()
    private val orderRepository = mockk<OrderRepository>()
    private val orderItemRepository = mockk<OrderItemRepository>()
    private val fulfillmentRepository = mockk<FulfillmentRepository>()
    private val fulfillmentHistoryRepository = mockk<FulfillmentHistoryRepository>()
    private val membershipService = mockk<MembershipService>()
    private val ledgerService = mockk<LedgerService>()
    private val auditService = mockk<AuditService>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val objectMapper = ObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2026-08-01T16:00:00Z"), ZoneOffset.UTC)
    private val service =
        OfflineFinancialRecordService(
            repository,
            campaignRepository,
            contributionRepository,
            sponsorshipPackageRepository,
            sponsorRepository,
            sponsorshipRepository,
            storeRepository,
            productRepository,
            productVariantRepository,
            orderRepository,
            orderItemRepository,
            fulfillmentRepository,
            fulfillmentHistoryRepository,
            membershipService,
            ledgerService,
            auditService,
            outboxWriter,
            objectMapper,
            clock,
        )
    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val campaignId = UUID.randomUUID()
    private val receivedAt = Instant.parse("2026-07-31T18:00:00Z")

    @Test
    fun `pending offline contribution does not create ledger activity until verification`() {
        val campaign = campaign()
        val contribution = contribution()
        val record = record(contribution.id, OfflineVerificationStatus.PENDING_VERIFICATION)
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk()
        every { campaignRepository.findById(campaignId, organizationId) } returns campaign
        every { repository.findByIdempotencyKey(organizationId, any()) } returns null
        every { repository.findByFingerprint(organizationId, any()) } returns null
        every {
            contributionRepository.insertOfflinePending(organizationId, campaignId, 15000, "USD", "Taylor", false, "taylor@example.com")
        } returns
            contribution
        every {
            repository.insert(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns record
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val created =
            service.createContribution(
                organizationId,
                campaignId,
                15000,
                "Taylor",
                false,
                "taylor@example.com",
                OfflinePaymentMethod.CHECK,
                "CHK-104",
                receivedAt,
                "Deposit pending review",
                "offline-test-001",
                false,
                true,
                user,
            )

        assertEquals(OfflineVerificationStatus.PENDING_VERIFICATION, created.verificationStatus)
        verify(exactly = 0) { ledgerService.recordOfflineContribution(any(), any()) }
    }

    @Test
    fun `acknowledgement requires a payer email`() {
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk()
        every { campaignRepository.findById(campaignId, organizationId) } returns campaign()

        assertFailsWith<ValidationException> {
            service.createContribution(
                organizationId,
                campaignId,
                15000,
                "Taylor",
                false,
                null,
                OfflinePaymentMethod.CHECK,
                "CHK-105",
                receivedAt,
                null,
                "offline-test-002",
                false,
                true,
                user,
            )
        }
    }

    @Test
    fun `verification confirms source record and appends balanced offline ledger entries`() {
        val pendingContribution = contribution()
        val pendingRecord = record(pendingContribution.id, OfflineVerificationStatus.PENDING_VERIFICATION)
        val verifiedRecord =
            pendingRecord.copy(
                verificationStatus = OfflineVerificationStatus.VERIFIED,
                verifiedByUserId = user.userId,
                verifiedAt = Instant.parse("2026-08-01T16:00:00Z"),
            )
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk()
        every { repository.findByIdForUpdate(pendingRecord.id, organizationId) } returns pendingRecord
        every { contributionRepository.findById(pendingContribution.id) } returns pendingContribution
        every { contributionRepository.markOfflineConfirmed(pendingContribution.id, receivedAt) } returns 1
        every { ledgerService.recordOfflineContribution(any(), "CHK-104") } just runs
        every { repository.markVerified(pendingRecord.id, organizationId, user.userId, any()) } returns 1
        every { repository.findById(pendingRecord.id, organizationId) } returns verifiedRecord
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
        every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

        val verified = service.verify(organizationId, pendingRecord.id, user)

        assertEquals(OfflineVerificationStatus.VERIFIED, verified.verificationStatus)
        verify(exactly = 1) { ledgerService.recordOfflineContribution(match { it.status == ContributionStatus.CONFIRMED }, "CHK-104") }
        verify(exactly = 1) {
            outboxWriter.write("offline_financial_record", pendingRecord.id, organizationId, "offline.financial.verified", any())
        }
    }

    private fun campaign() =
        Campaign(
            campaignId,
            organizationId,
            null,
            "Uniform Fund",
            "uniform-fund",
            null,
            CampaignType.UNIFORMS,
            100000,
            "USD",
            null,
            null,
            CampaignStatus.ACTIVE,
            Instant.parse("2026-07-01T00:00:00Z"),
            receivedAt,
            receivedAt,
        )

    private fun contribution() =
        Contribution(
            id = UUID.randomUUID(),
            organizationId = organizationId,
            campaignId = campaignId,
            amountMinor = 15000,
            currency = "USD",
            supporterName = "Taylor",
            isAnonymous = false,
            supporterEmail = "taylor@example.com",
            status = ContributionStatus.PENDING,
            stripeCheckoutSessionId = null,
            stripePaymentIntentId = null,
            confirmedAt = null,
            refundedAt = null,
            createdAt = receivedAt,
            paymentSource = PaymentSource.OFFLINE,
        )

    private fun record(
        recordId: UUID,
        status: OfflineVerificationStatus,
    ) = OfflineFinancialRecord(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        recordType = OfflineFinancialRecordType.CONTRIBUTION,
        recordId = recordId,
        displayLabel = "Uniform Fund",
        paymentMethod = OfflinePaymentMethod.CHECK,
        verificationStatus = status,
        amountMinor = 15000,
        currency = "USD",
        payerName = "Taylor",
        payerEmail = "taylor@example.com",
        paymentReference = "CHK-104",
        receivedAt = receivedAt,
        internalNotes = "Deposit pending review",
        idempotencyKey = "offline-test-001",
        duplicateFingerprint = "a".repeat(64),
        sendAcknowledgement = true,
        recordedByUserId = user.userId,
        verifiedByUserId = null,
        verifiedAt = null,
        createdAt = receivedAt,
        updatedAt = receivedAt,
    )
}
