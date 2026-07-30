package com.leaguelift.dashboard.web

import java.util.UUID

/** Real: counts from `organization` and `app_user` directly. */
data class PlatformSummaryResponse(
	val organizationCount: Long,
	val userCount: Long,
)

data class PlatformOrganizationRow(
	val organizationId: UUID,
	val name: String,
	val slug: String,
	val organizationType: String,
	val status: String,
)

/** Real: `webhook_event` rows grouped by `processing_status` (DESIGN-DOC.md section 18.2 webhook backlog/failures). */
data class WebhookHealthResponse(
	val processed: Long,
	val failed: Long,
	val ignored: Long,
)

/** Real: `outbox_event` rows grouped by `status` (DESIGN-DOC.md section 18.2 outbox backlog — still entirely unconsumed, see section 17). */
data class OutboxHealthResponse(
	val pending: Long,
	val processing: Long,
	val processed: Long,
	val failed: Long,
	val deadLetter: Long,
)

/** Real, platform-wide (Phase 7 completion): `"order"` rows by status, no organization filter. */
data class PlatformOrdersSummary(
	val confirmed: Long,
	val refunded: Long,
	val pending: Long,
)

/** Real, platform-wide (Phase 7 completion): `ledger_entry` sums across every organization. */
data class PlatformPaymentsSummary(
	val grossProcessedMinor: Long,
	val platformFeesCollectedMinor: Long,
	val refundedMinor: Long,
)

/** Real, platform-wide (Phase 7 completion): `organization_payout_account` + `ledger_entry` TRANSFER sums. */
data class PlatformPayoutsSummary(
	val organizationsPayoutEnabled: Long,
	val totalTransferredMinor: Long,
)
