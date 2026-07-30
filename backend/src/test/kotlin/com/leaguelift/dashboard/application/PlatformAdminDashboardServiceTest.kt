package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.ledger.domain.LedgerDirection
import com.leaguelift.ledger.domain.LedgerEntryType
import com.leaguelift.ledger.persistence.LedgerEntryRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.outbox.persistence.OutboxEventRepository
import com.leaguelift.payout.persistence.OrganizationPayoutAccountRepository
import com.leaguelift.webhook.persistence.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlatformAdminDashboardServiceTest {

	private val authorizationService = mockk<AuthorizationService>()
	private val organizationRepository = mockk<OrganizationRepository>()
	private val appUserRepository = mockk<AppUserRepository>()
	private val webhookEventRepository = mockk<WebhookEventRepository>()
	private val outboxEventRepository = mockk<OutboxEventRepository>()
	private val orderRepository = mockk<OrderRepository>()
	private val ledgerEntryRepository = mockk<LedgerEntryRepository>()
	private val organizationPayoutAccountRepository = mockk<OrganizationPayoutAccountRepository>()

	private val service = PlatformAdminDashboardService(
		authorizationService, organizationRepository, appUserRepository, webhookEventRepository, outboxEventRepository,
		orderRepository, ledgerEntryRepository, organizationPayoutAccountRepository,
	)

	private val currentUser = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)

	@Test
	fun `getOrdersSummary requires the platform audit capability`() {
		every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW) } throws ForbiddenException("DENIED", "no")

		assertFailsWith<ForbiddenException> {
			service.getOrdersSummary(currentUser)
		}
	}

	@Test
	fun `getOrdersSummary maps status counts, defaulting missing statuses to zero`() {
		every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW) } returns Unit
		every { orderRepository.countAllByStatus() } returns mapOf("CONFIRMED" to 12L, "REFUNDED" to 2L)

		val result = service.getOrdersSummary(currentUser)

		assertEquals(12L, result.confirmed)
		assertEquals(2L, result.refunded)
		assertEquals(0L, result.pending)
	}

	@Test
	fun `getPaymentsSummary sums gross sale and contribution credits as the gross processed total`() {
		every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW) } returns Unit
		every { ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.GROSS_SALE, LedgerDirection.CREDIT) } returns 100_000L
		every { ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.CONTRIBUTION, LedgerDirection.CREDIT) } returns 50_000L
		every { ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.LEAGUELIFT_PLATFORM_FEE, LedgerDirection.DEBIT) } returns 7_500L
		every { ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.REFUND, LedgerDirection.DEBIT) } returns 3_000L

		val result = service.getPaymentsSummary(currentUser)

		assertEquals(150_000L, result.grossProcessedMinor)
		assertEquals(7_500L, result.platformFeesCollectedMinor)
		assertEquals(3_000L, result.refundedMinor)
	}

	@Test
	fun `getPayoutsSummary combines payout-enabled organization count with total transferred`() {
		every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW) } returns Unit
		every { organizationPayoutAccountRepository.countPayoutsEnabled() } returns 5L
		every { ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.TRANSFER, LedgerDirection.DEBIT) } returns 42_000L

		val result = service.getPayoutsSummary(currentUser)

		assertEquals(5L, result.organizationsPayoutEnabled)
		assertEquals(42_000L, result.totalTransferredMinor)
	}
}
