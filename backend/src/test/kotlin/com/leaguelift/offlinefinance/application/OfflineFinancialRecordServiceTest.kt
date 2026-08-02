package com.leaguelift.offlinefinance.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.finance.domain.PaymentSource
import com.leaguelift.fundraising.domain.Campaign
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.CampaignType
import com.leaguelift.fundraising.domain.Contribution
import com.leaguelift.fundraising.domain.ContributionStatus
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.ledger.application.LedgerService
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.offlinefinance.domain.OfflineFinancialRecord
import com.leaguelift.offlinefinance.domain.OfflineFinancialRecordType
import com.leaguelift.offlinefinance.domain.OfflinePaymentMethod
import com.leaguelift.offlinefinance.domain.OfflineVerificationStatus
import com.leaguelift.offlinefinance.persistence.OfflineFinancialRecordRepository
import com.leaguelift.order.persistence.FulfillmentHistoryRepository
import com.leaguelift.order.persistence.FulfillmentRepository
import com.leaguelift.order.persistence.OrderItemRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.sponsorship.persistence.SponsorRepository
import com.leaguelift.sponsorship.persistence.SponsorshipPackageRepository
import com.leaguelift.sponsorship.persistence.SponsorshipRepository
import com.leaguelift.store.persistence.ProductRepository
import com.leaguelift.store.persistence.ProductVariantRepository
import com.leaguelift.store.persistence.StoreRepository
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
	private val service = OfflineFinancialRecordService(
		repository, campaignRepository, contributionRepository, sponsorshipPackageRepository,
		sponsorRepository, sponsorshipRepository, storeRepository, productRepository,
		productVariantRepository, orderRepository, orderItemRepository, fulfillmentRepository,
		fulfillmentHistoryRepository, membershipService, ledgerService, auditService,
		outboxWriter, objectMapper, clock,
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
		every { contributionRepository.insertOfflinePending(organizationId, campaignId, 15000, "USD", "Taylor", false, "taylor@example.com") } returns contribution
		every { repository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns record
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val created = service.createContribution(
			organizationId, campaignId, 15000, "Taylor", false, "taylor@example.com",
			OfflinePaymentMethod.CHECK, "CHK-104", receivedAt, "Deposit pending review",
			"offline-test-001", false, true, user,
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
				organizationId, campaignId, 15000, "Taylor", false, null,
				OfflinePaymentMethod.CHECK, "CHK-105", receivedAt, null,
				"offline-test-002", false, true, user,
			)
		}
	}

	@Test
	fun `verification confirms source record and appends balanced offline ledger entries`() {
		val pendingContribution = contribution()
		val pendingRecord = record(pendingContribution.id, OfflineVerificationStatus.PENDING_VERIFICATION)
		val verifiedRecord = pendingRecord.copy(
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
		verify(exactly = 1) { outboxWriter.write("offline_financial_record", pendingRecord.id, organizationId, "offline.financial.verified", any()) }
	}

	private fun campaign() = Campaign(
		campaignId, organizationId, null, "Uniform Fund", "uniform-fund", null,
		CampaignType.UNIFORMS, 100000, "USD", null, null, CampaignStatus.ACTIVE,
		Instant.parse("2026-07-01T00:00:00Z"), receivedAt, receivedAt,
	)

	private fun contribution() = Contribution(
		id = UUID.randomUUID(), organizationId = organizationId, campaignId = campaignId,
		amountMinor = 15000, currency = "USD", supporterName = "Taylor", isAnonymous = false,
		supporterEmail = "taylor@example.com", status = ContributionStatus.PENDING,
		stripeCheckoutSessionId = null, stripePaymentIntentId = null, confirmedAt = null,
		refundedAt = null, createdAt = receivedAt, paymentSource = PaymentSource.OFFLINE,
	)

	private fun record(recordId: UUID, status: OfflineVerificationStatus) = OfflineFinancialRecord(
		id = UUID.randomUUID(), organizationId = organizationId,
		recordType = OfflineFinancialRecordType.CONTRIBUTION, recordId = recordId,
		displayLabel = "Uniform Fund", paymentMethod = OfflinePaymentMethod.CHECK,
		verificationStatus = status, amountMinor = 15000, currency = "USD",
		payerName = "Taylor", payerEmail = "taylor@example.com", paymentReference = "CHK-104",
		receivedAt = receivedAt, internalNotes = "Deposit pending review", idempotencyKey = "offline-test-001",
		duplicateFingerprint = "a".repeat(64), sendAcknowledgement = true,
		recordedByUserId = user.userId, verifiedByUserId = null, verifiedAt = null,
		createdAt = receivedAt, updatedAt = receivedAt,
	)
}
