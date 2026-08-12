package com.rally26.ledger.application

import com.rally26.config.PayoutProperties
import com.rally26.config.PlatformFeeProperties
import com.rally26.fundraising.domain.Contribution
import com.rally26.fundraising.domain.ContributionStatus
import com.rally26.ledger.domain.LedgerDirection
import com.rally26.ledger.domain.LedgerEntry
import com.rally26.ledger.domain.LedgerEntryType
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.ledger.persistence.LedgerEntryRepository
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderItem
import com.rally26.order.domain.OrderStatus
import com.rally26.sponsorship.domain.Sponsorship
import com.rally26.sponsorship.domain.SponsorshipStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerServiceTest {
    private val ledgerEntryRepository = mockk<LedgerEntryRepository>()
    private val platformFeeProperties = PlatformFeeProperties(feeBasisPoints = 500) // 5%
    private val payoutProperties = PayoutProperties(holdingPeriodDays = 7)
    private val service = LedgerService(ledgerEntryRepository, platformFeeProperties, payoutProperties)

    private val orgId = UUID.randomUUID()

    @Test
    fun `recordConfirmedContribution writes a gross credit, a 5 percent fee debit, and a net earning credit`() {
        val contribution = confirmedContribution(amountMinor = 10_000L)
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordConfirmedContribution(contribution)

        assertEquals(3, captured.size)
        val gross = captured[0]
        assertEquals(LedgerEntryType.CONTRIBUTION, gross.entryType)
        assertEquals(LedgerDirection.CREDIT, gross.direction)
        assertEquals(10_000L, gross.amountMinor)

        val fee = captured[1]
        assertEquals(LedgerEntryType.RALLY26_PLATFORM_FEE, fee.entryType)
        assertEquals(LedgerDirection.DEBIT, fee.direction)
        assertEquals(500L, fee.amountMinor) // 5% of 10,000

        val earning = captured[2]
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING, earning.entryType)
        assertEquals(LedgerDirection.CREDIT, earning.direction)
        assertEquals(9_500L, earning.amountMinor)
    }

    @Test
    fun `recordConfirmedSponsorship writes a gross credit, a 5 percent fee debit, and a net earning credit, sourced to SPONSORSHIP`() {
        val sponsorship = confirmedSponsorship(amountMinor = 50_000L)
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordConfirmedSponsorship(sponsorship)

        assertEquals(3, captured.size)
        val gross = captured[0]
        assertEquals(LedgerEntryType.CONTRIBUTION, gross.entryType)
        assertEquals(LedgerDirection.CREDIT, gross.direction)
        assertEquals(50_000L, gross.amountMinor)
        assertEquals(LedgerSourceType.SPONSORSHIP, gross.sourceType)
        assertEquals(sponsorship.id, gross.sourceId)

        val fee = captured[1]
        assertEquals(LedgerEntryType.RALLY26_PLATFORM_FEE, fee.entryType)
        assertEquals(LedgerDirection.DEBIT, fee.direction)
        assertEquals(2_500L, fee.amountMinor) // 5% of 50,000
        assertEquals(LedgerSourceType.SPONSORSHIP, fee.sourceType)

        val earning = captured[2]
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING, earning.entryType)
        assertEquals(LedgerDirection.CREDIT, earning.direction)
        assertEquals(47_500L, earning.amountMinor)
        assertEquals(LedgerSourceType.SPONSORSHIP, earning.sourceType)
    }

    @Test
    fun `recordConfirmedOrder writes gross sale, production cost, platform fee, and a net earning credit when profitable`() {
        val order = confirmedOrder()
        val items = listOf(orderItem(order.id, quantity = 2, unitPriceMinor = 2_500L, unitCostMinor = 1_000L))
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordConfirmedOrder(order, items)

        assertEquals(4, captured.size)
        assertEquals(LedgerEntryType.GROSS_SALE to LedgerDirection.CREDIT, captured[0].entryType to captured[0].direction)
        assertEquals(5_000L, captured[0].amountMinor) // 2 * 2500

        assertEquals(LedgerEntryType.PRODUCTION_COST to LedgerDirection.DEBIT, captured[1].entryType to captured[1].direction)
        assertEquals(2_000L, captured[1].amountMinor) // 2 * 1000

        assertEquals(LedgerEntryType.RALLY26_PLATFORM_FEE to LedgerDirection.DEBIT, captured[2].entryType to captured[2].direction)
        assertEquals(250L, captured[2].amountMinor) // 5% of 5000

        assertEquals(LedgerEntryType.ORGANIZATION_EARNING to LedgerDirection.CREDIT, captured[3].entryType to captured[3].direction)
        assertEquals(2_750L, captured[3].amountMinor) // 5000 - 2000 - 250
    }

    @Test
    fun `recordConfirmedOrder writes a DEBIT organization earning when production cost and fee exceed gross`() {
        val order = confirmedOrder()
        // gross = 1000, cost = 900, fee = 50 (5% of 1000) -> net = 1000 - 900 - 50 = 50... make it clearly negative:
        val items = listOf(orderItem(order.id, quantity = 1, unitPriceMinor = 1_000L, unitCostMinor = 1_200L))
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordConfirmedOrder(order, items)

        val earning = captured[3]
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING, earning.entryType)
        assertEquals(LedgerDirection.DEBIT, earning.direction)
        // gross 1000, cost 1200, fee 50 -> net = 1000 - 1200 - 50 = -250 -> abs = 250
        assertEquals(250L, earning.amountMinor)
    }

    @Test
    fun `recordOfflineContribution clears the external receipt without creating payout earnings`() {
        val contribution = confirmedContribution(amountMinor = 10_000L)
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordOfflineContribution(contribution, "CHK-104")

        assertEquals(2, captured.size)
        assertEquals(LedgerEntryType.CONTRIBUTION to LedgerDirection.CREDIT, captured[0].entryType to captured[0].direction)
        assertEquals(LedgerEntryType.OFFLINE_SETTLEMENT to LedgerDirection.DEBIT, captured[1].entryType to captured[1].direction)
        assertEquals(10_000L, captured[0].amountMinor)
        assertEquals(10_000L, captured[1].amountMinor)
        assertTrue(captured.none { it.entryType == LedgerEntryType.ORGANIZATION_EARNING })
    }

    @Test
    fun `recordOfflineOrder records gross settlement and vendor cost without payout earnings`() {
        val order = confirmedOrder()
        val items = listOf(orderItem(order.id, quantity = 2, unitPriceMinor = 2_500L, unitCostMinor = 1_000L))
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordOfflineOrder(order, items, "EXT-ORDER-9")

        assertEquals(3, captured.size)
        assertEquals(LedgerEntryType.GROSS_SALE to LedgerDirection.CREDIT, captured[0].entryType to captured[0].direction)
        assertEquals(LedgerEntryType.OFFLINE_SETTLEMENT to LedgerDirection.DEBIT, captured[1].entryType to captured[1].direction)
        assertEquals(LedgerEntryType.PRODUCTION_COST to LedgerDirection.DEBIT, captured[2].entryType to captured[2].direction)
        assertEquals(5_000L, captured[0].amountMinor)
        assertEquals(5_000L, captured[1].amountMinor)
        assertEquals(2_000L, captured[2].amountMinor)
        assertTrue(captured.none { it.entryType == LedgerEntryType.ORGANIZATION_EARNING })
    }

    @Test
    fun `getPayoutSummary nets eligible credits against pending debits`() {
        every { ledgerEntryRepository.findEligibleOrganizationEarningCredits(orgId, any()) } returns
            listOf(
                earningEntry(LedgerDirection.CREDIT, 5_000L),
                earningEntry(LedgerDirection.CREDIT, 2_000L),
            )
        every { ledgerEntryRepository.findHeldOrganizationEarningCredits(orgId, any()) } returns
            listOf(
                earningEntry(LedgerDirection.CREDIT, 1_000L),
            )
        every { ledgerEntryRepository.findPendingOrganizationEarningDebits(orgId) } returns
            listOf(
                earningEntry(LedgerDirection.DEBIT, 3_000L),
            )

        val summary = service.getPayoutSummary(orgId)

        assertEquals(7_000L, summary.eligibleMinor)
        assertEquals(1_000L, summary.heldMinor)
        assertEquals(3_000L, summary.pendingDebitsMinor)
        assertEquals(4_000L, summary.netAvailableMinor)
    }

    @Test
    fun `getPayoutSummary reports a negative netAvailableMinor honestly when pending debits exceed eligible credits`() {
        every { ledgerEntryRepository.findEligibleOrganizationEarningCredits(orgId, any()) } returns
            listOf(earningEntry(LedgerDirection.CREDIT, 1_000L))
        every { ledgerEntryRepository.findHeldOrganizationEarningCredits(orgId, any()) } returns emptyList()
        every { ledgerEntryRepository.findPendingOrganizationEarningDebits(orgId) } returns
            listOf(earningEntry(LedgerDirection.DEBIT, 4_000L))

        val summary = service.getPayoutSummary(orgId)

        assertEquals(-3_000L, summary.netAvailableMinor)
    }

    @Test
    fun `recordTransfer writes a TRANSFER debit and marks the included entries`() {
        val transferEntry = earningEntry(LedgerDirection.DEBIT, 4_000L).copy(entryType = LedgerEntryType.TRANSFER, id = UUID.randomUUID())
        val includedIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        every {
            ledgerEntryRepository.insert(
                organizationId = orgId,
                accountCode = LedgerEntryType.TRANSFER.name,
                entryType = LedgerEntryType.TRANSFER,
                direction = LedgerDirection.DEBIT,
                amountMinor = 4_000L,
                currency = "usd",
                sourceType = LedgerSourceType.TRANSFER,
                sourceId = any(),
                externalReference = "tr_test_123",
                description = any(),
            )
        } returns transferEntry
        every { ledgerEntryRepository.markIncludedInTransfer(includedIds, transferEntry.id) } returns includedIds.size

        val result = service.recordTransfer(orgId, 4_000L, "usd", "tr_test_123", includedIds)

        assertEquals(transferEntry.id, result.id)
        verify(exactly = 1) { ledgerEntryRepository.markIncludedInTransfer(includedIds, transferEntry.id) }
    }

    @Test
    fun `recordRefund writes a REFUND debit for the gross amount and an ORGANIZATION_EARNING debit net of the platform fee`() {
        val sourceId = UUID.randomUUID()
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordRefund(orgId, LedgerSourceType.CONTRIBUTION, sourceId, 10_000L, "usd", "re_test_123")

        assertEquals(2, captured.size)
        val refund = captured[0]
        assertEquals(LedgerEntryType.REFUND, refund.entryType)
        assertEquals(LedgerDirection.DEBIT, refund.direction)
        assertEquals(10_000L, refund.amountMinor)
        assertEquals("re_test_123", refund.externalReference)

        val earningReversal = captured[1]
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING, earningReversal.entryType)
        assertEquals(LedgerDirection.DEBIT, earningReversal.direction)
        assertEquals(9_500L, earningReversal.amountMinor) // 10,000 - 5% fee
    }

    @Test
    fun `recordDisputeOpened writes a CHARGEBACK debit, an ORGANIZATION_EARNING debit net of the platform fee, and a CHARGEBACK_FEE debit with no matching earning debit`() {
        val sourceId = UUID.randomUUID()
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordDisputeOpened(orgId, LedgerSourceType.ORDER, sourceId, 10_000L, "usd", 1_500L, "dp_test_123")

        assertEquals(3, captured.size)
        val chargeback = captured[0]
        assertEquals(LedgerEntryType.CHARGEBACK, chargeback.entryType)
        assertEquals(LedgerDirection.DEBIT, chargeback.direction)
        assertEquals(10_000L, chargeback.amountMinor)
        assertEquals("dp_test_123", chargeback.externalReference)

        val earningReversal = captured[1]
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING, earningReversal.entryType)
        assertEquals(LedgerDirection.DEBIT, earningReversal.direction)
        assertEquals(9_500L, earningReversal.amountMinor) // 10,000 - 5% fee

        val fee = captured[2]
        assertEquals(LedgerEntryType.CHARGEBACK_FEE, fee.entryType)
        assertEquals(LedgerDirection.DEBIT, fee.direction)
        assertEquals(1_500L, fee.amountMinor)
        // Rally26 absorbs the dispute fee (founder decision, 2026-08-12) — no third
        // ORGANIZATION_EARNING debit is ever produced for it.
        assertTrue(captured.count { it.entryType == LedgerEntryType.ORGANIZATION_EARNING } == 1)
    }

    @Test
    fun `recordDisputeOpened omits the CHARGEBACK_FEE entry when the fee is zero`() {
        val sourceId = UUID.randomUUID()
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordDisputeOpened(orgId, LedgerSourceType.ORDER, sourceId, 10_000L, "usd", 0L, "dp_test_zero_fee")

        assertEquals(2, captured.size)
        assertTrue(captured.none { it.entryType == LedgerEntryType.CHARGEBACK_FEE })
    }

    @Test
    fun `recordDisputeWon reinstates the CHARGEBACK and ORGANIZATION_EARNING amounts as credits`() {
        val sourceId = UUID.randomUUID()
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordDisputeWon(orgId, LedgerSourceType.ORDER, sourceId, 10_000L, "usd", "dp_test_123")

        assertEquals(2, captured.size)
        val chargeback = captured[0]
        assertEquals(LedgerEntryType.CHARGEBACK, chargeback.entryType)
        assertEquals(LedgerDirection.CREDIT, chargeback.direction)
        assertEquals(10_000L, chargeback.amountMinor)

        val earningReinstatement = captured[1]
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING, earningReinstatement.entryType)
        assertEquals(LedgerDirection.CREDIT, earningReinstatement.direction)
        assertEquals(9_500L, earningReinstatement.amountMinor)
    }

    @Test
    fun `recordCorrectionRefund appends refund and organization earning debits under the correction source`() {
        val correctionId = UUID.randomUUID()
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.recordCorrectionRefund(orgId, correctionId, 4_000L, "usd", "re_partial_123")

        assertEquals(2, captured.size)
        assertTrue(captured.all { it.sourceType == LedgerSourceType.CORRECTION && it.sourceId == correctionId })
        assertEquals(LedgerEntryType.REFUND to LedgerDirection.DEBIT, captured[0].entryType to captured[0].direction)
        assertEquals(4_000L, captured[0].amountMinor)
        assertEquals(LedgerEntryType.ORGANIZATION_EARNING to LedgerDirection.DEBIT, captured[1].entryType to captured[1].direction)
        assertEquals(3_800L, captured[1].amountMinor)
    }

    @Test
    fun `reverseOfflineSource appends opposite manual adjustments without changing original rows`() {
        val sourceId = UUID.randomUUID()
        val correctionId = UUID.randomUUID()
        val originals =
            listOf(
                earningEntry(LedgerDirection.CREDIT, 5_000L).copy(
                    entryType = LedgerEntryType.CONTRIBUTION,
                    accountCode = LedgerEntryType.CONTRIBUTION.name,
                    sourceType = LedgerSourceType.CONTRIBUTION,
                    sourceId = sourceId,
                ),
                earningEntry(LedgerDirection.DEBIT, 5_000L).copy(
                    entryType = LedgerEntryType.OFFLINE_SETTLEMENT,
                    accountCode = LedgerEntryType.OFFLINE_SETTLEMENT.name,
                    sourceType = LedgerSourceType.CONTRIBUTION,
                    sourceId = sourceId,
                ),
            )
        every { ledgerEntryRepository.findBySource(orgId, LedgerSourceType.CONTRIBUTION, sourceId) } returns originals
        val captured = mutableListOf<InsertCall>()
        stubInsert(captured)

        service.reverseOfflineSource(orgId, LedgerSourceType.CONTRIBUTION, sourceId, correctionId, "Duplicate check")

        assertEquals(2, captured.size)
        assertTrue(captured.all { it.entryType == LedgerEntryType.MANUAL_ADJUSTMENT })
        assertTrue(captured.all { it.sourceType == LedgerSourceType.CORRECTION && it.sourceId == correctionId })
        assertEquals(LedgerDirection.DEBIT, captured[0].direction)
        assertEquals(LedgerDirection.CREDIT, captured[1].direction)
        verify(exactly = 1) { ledgerEntryRepository.findBySource(orgId, LedgerSourceType.CONTRIBUTION, sourceId) }
    }

    private data class InsertCall(
        val organizationId: UUID,
        val accountCode: String,
        val entryType: LedgerEntryType,
        val direction: LedgerDirection,
        val amountMinor: Long,
        val currency: String,
        val sourceType: LedgerSourceType,
        val sourceId: UUID,
        val externalReference: String?,
        val description: String?,
    )

    private fun stubInsert(captured: MutableList<InsertCall>) {
        every {
            ledgerEntryRepository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val args = it.invocation.args
            val call =
                InsertCall(
                    organizationId = args[0] as UUID,
                    accountCode = args[1] as String,
                    entryType = args[2] as LedgerEntryType,
                    direction = args[3] as LedgerDirection,
                    amountMinor = args[4] as Long,
                    currency = args[5] as String,
                    sourceType = args[6] as LedgerSourceType,
                    sourceId = args[7] as UUID,
                    externalReference = args[8] as String?,
                    description = args[9] as String?,
                )
            captured += call
            LedgerEntry(
                id = UUID.randomUUID(),
                organizationId = call.organizationId,
                accountCode = call.accountCode,
                entryType = call.entryType,
                direction = call.direction,
                amountMinor = call.amountMinor,
                currency = call.currency,
                sourceType = call.sourceType,
                sourceId = call.sourceId,
                externalReference = call.externalReference,
                description = call.description,
                includedInTransferEntryId = null,
                effectiveAt = Instant.now(),
                createdAt = Instant.now(),
            )
        }
    }

    private fun earningEntry(
        direction: LedgerDirection,
        amountMinor: Long,
    ) = LedgerEntry(
        id = UUID.randomUUID(),
        organizationId = orgId,
        accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
        entryType = LedgerEntryType.ORGANIZATION_EARNING,
        direction = direction,
        amountMinor = amountMinor,
        currency = "usd",
        sourceType = LedgerSourceType.CONTRIBUTION,
        sourceId = UUID.randomUUID(),
        externalReference = null,
        description = null,
        includedInTransferEntryId = null,
        effectiveAt = Instant.now(),
        createdAt = Instant.now(),
    )

    private fun confirmedContribution(amountMinor: Long) =
        Contribution(
            id = UUID.randomUUID(),
            organizationId = orgId,
            campaignId = UUID.randomUUID(),
            amountMinor = amountMinor,
            currency = "usd",
            supporterName = "Jane Doe",
            isAnonymous = false,
            supporterEmail = null,
            status = ContributionStatus.CONFIRMED,
            stripeCheckoutSessionId = "cs_test_1",
            stripePaymentIntentId = "pi_test_1",
            confirmedAt = Instant.now(),
            refundedAt = null,
            createdAt = Instant.now(),
        )

    private fun confirmedSponsorship(amountMinor: Long) =
        Sponsorship(
            id = UUID.randomUUID(),
            organizationId = orgId,
            packageId = UUID.randomUUID(),
            sponsorId = UUID.randomUUID(),
            amountMinor = amountMinor,
            currency = "usd",
            status = SponsorshipStatus.CONFIRMED,
            stripeCheckoutSessionId = "cs_test_3",
            stripePaymentIntentId = "pi_test_3",
            confirmedAt = Instant.now(),
            createdAt = Instant.now(),
        )

    private fun confirmedOrder() =
        Order(
            id = UUID.randomUUID(),
            organizationId = orgId,
            storeId = UUID.randomUUID(),
            status = OrderStatus.CONFIRMED,
            currency = "usd",
            supporterName = "Jane Doe",
            supporterEmail = null,
            shippingAddress = null,
            stripeCheckoutSessionId = "cs_test_2",
            stripePaymentIntentId = "pi_test_2",
            confirmedAt = Instant.now(),
            refundedAt = null,
            createdAt = Instant.now(),
        )

    private fun orderItem(
        orderId: UUID,
        quantity: Int,
        unitPriceMinor: Long,
        unitCostMinor: Long,
    ) = OrderItem(
        id = UUID.randomUUID(),
        orderId = orderId,
        productVariantId = UUID.randomUUID(),
        quantity = quantity,
        unitPriceMinor = unitPriceMinor,
        unitCostMinor = unitCostMinor,
    )
}
