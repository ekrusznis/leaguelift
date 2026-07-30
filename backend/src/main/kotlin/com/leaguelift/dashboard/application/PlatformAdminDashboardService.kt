package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.OutboxHealthResponse
import com.leaguelift.dashboard.web.PlatformOrdersSummary
import com.leaguelift.dashboard.web.PlatformOrganizationRow
import com.leaguelift.dashboard.web.PlatformPaymentsSummary
import com.leaguelift.dashboard.web.PlatformPayoutsSummary
import com.leaguelift.dashboard.web.PlatformSummaryResponse
import com.leaguelift.dashboard.web.WebhookHealthResponse
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.ledger.domain.LedgerDirection
import com.leaguelift.ledger.domain.LedgerEntryType
import com.leaguelift.ledger.persistence.LedgerEntryRepository
import com.leaguelift.order.persistence.OrderRepository
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.outbox.persistence.OutboxEventRepository
import com.leaguelift.payout.persistence.OrganizationPayoutAccountRepository
import com.leaguelift.webhook.domain.WebhookProcessingStatus
import com.leaguelift.webhook.persistence.WebhookEventRepository
import org.springframework.stereotype.Service

private const val ORGANIZATION_LIST_LIMIT = 50

/**
 * Platform Administrator Dashboard (DESIGN-DOC.md section 10.2, new in Phase 7/
 * ADR-020 — this dashboard did not exist before this slice, and until this slice
 * `CurrentUser.platformAdministrator` was hardcoded `false` everywhere, so there was no
 * way for any account to actually reach it). Cross-org organizations/users counts,
 * webhook/outbox operational health, and — as of the Phase 7 completion effort —
 * platform-wide Orders/Payments/Payouts summaries (from `"order"`/`ledger_entry`/
 * `organization_payout_account`, the same tables every org-scoped Owner dashboard
 * already reads, just without an organization filter). The Audit item reuses
 * `ActivityFeedService`/`GET /me/activity`, which already returns a platform-wide feed
 * for a platform administrator — no separate audit endpoint needed. Pilot
 * Applications, Subscriptions, Feature Flags, and Support still have no backing
 * aggregate query and remain left out rather than faked (ADR-020 consequences).
 *
 * Every method requires a real `platform.*` capability via
 * [AuthorizationService.requirePlatformCapability] — there is no organization-scoped
 * fallback here, unlike every other dashboard service.
 */
@Service
class PlatformAdminDashboardService(
	private val authorizationService: AuthorizationService,
	private val organizationRepository: OrganizationRepository,
	private val appUserRepository: AppUserRepository,
	private val webhookEventRepository: WebhookEventRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val orderRepository: OrderRepository,
	private val ledgerEntryRepository: LedgerEntryRepository,
	private val organizationPayoutAccountRepository: OrganizationPayoutAccountRepository,
) {

	fun getSummary(currentUser: CurrentUser): PlatformSummaryResponse {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_ORG_VIEW)
		return PlatformSummaryResponse(
			organizationCount = organizationRepository.countAllForPlatformAdmin(),
			userCount = appUserRepository.countAll(),
		)
	}

	fun listOrganizations(currentUser: CurrentUser): List<PlatformOrganizationRow> {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_ORG_VIEW)
		return organizationRepository.findAllForPlatformAdmin(0, ORGANIZATION_LIST_LIMIT).map {
			PlatformOrganizationRow(it.id, it.name, it.slug, it.organizationType.name, it.status.name)
		}
	}

	fun getWebhookHealth(currentUser: CurrentUser): WebhookHealthResponse {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW)
		return WebhookHealthResponse(
			processed = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PROCESSED),
			failed = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.FAILED),
			ignored = webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.IGNORED),
		)
	}

	fun getOutboxHealth(currentUser: CurrentUser): OutboxHealthResponse {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW)
		return OutboxHealthResponse(
			pending = outboxEventRepository.countByStatus("PENDING"),
			processing = outboxEventRepository.countByStatus("PROCESSING"),
			processed = outboxEventRepository.countByStatus("PROCESSED"),
			failed = outboxEventRepository.countByStatus("FAILED"),
			deadLetter = outboxEventRepository.countByStatus("DEAD_LETTER"),
		)
	}

	fun getOrdersSummary(currentUser: CurrentUser): PlatformOrdersSummary {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW)
		val counts = orderRepository.countAllByStatus()
		return PlatformOrdersSummary(
			confirmed = counts["CONFIRMED"] ?: 0,
			refunded = counts["REFUNDED"] ?: 0,
			pending = counts["PENDING"] ?: 0,
		)
	}

	fun getPaymentsSummary(currentUser: CurrentUser): PlatformPaymentsSummary {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW)
		val gross = ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.GROSS_SALE, LedgerDirection.CREDIT) +
			ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.CONTRIBUTION, LedgerDirection.CREDIT)
		return PlatformPaymentsSummary(
			grossProcessedMinor = gross,
			platformFeesCollectedMinor = ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.LEAGUELIFT_PLATFORM_FEE, LedgerDirection.DEBIT),
			refundedMinor = ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.REFUND, LedgerDirection.DEBIT),
		)
	}

	fun getPayoutsSummary(currentUser: CurrentUser): PlatformPayoutsSummary {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_AUDIT_VIEW)
		return PlatformPayoutsSummary(
			organizationsPayoutEnabled = organizationPayoutAccountRepository.countPayoutsEnabled(),
			totalTransferredMinor = ledgerEntryRepository.sumAllByTypeAndDirection(LedgerEntryType.TRANSFER, LedgerDirection.DEBIT),
		)
	}
}
