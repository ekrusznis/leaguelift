package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.web.OutboxHealthResponse
import com.leaguelift.dashboard.web.PlatformOrganizationRow
import com.leaguelift.dashboard.web.PlatformSummaryResponse
import com.leaguelift.dashboard.web.WebhookHealthResponse
import com.leaguelift.identity.persistence.AppUserRepository
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.outbox.persistence.OutboxEventRepository
import com.leaguelift.webhook.domain.WebhookProcessingStatus
import com.leaguelift.webhook.persistence.WebhookEventRepository
import org.springframework.stereotype.Service

private const val ORGANIZATION_LIST_LIMIT = 50

/**
 * Platform Administrator Dashboard (DESIGN-DOC.md section 10.2, new in Phase 7/
 * ADR-020 — this dashboard did not exist before this slice, and until this slice
 * `CurrentUser.platformAdministrator` was hardcoded `false` everywhere, so there was no
 * way for any account to actually reach it). Deliberately narrow: cross-org
 * organizations/users counts plus webhook/outbox operational health, the subset of
 * DESIGN-DOC.md's nav list (Organizations, Users, Integrations, Webhooks, Outbox) the
 * current schema genuinely supports — Pilot Applications, Subscriptions, Payments,
 * Payouts, Orders, Audit, Feature Flags, and Support have no backing aggregate query
 * built yet and are left out rather than faked (ADR-020 consequences).
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
}
