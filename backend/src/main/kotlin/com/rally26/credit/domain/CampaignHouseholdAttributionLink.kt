package com.rally26.credit.domain

import java.time.Instant
import java.util.UUID

/**
 * A household's private sharing link for one campaign (Phase 23). `code` is
 * never publicly displayed by itself — it's a URL parameter (`?ref=`) resolved
 * server-side back to the household. `publicDisplayName` is the separate,
 * supporter-facing opt-in name shown on the campaign page, matching the
 * founder's "both, supporter's choice" attribution decision.
 */
data class CampaignHouseholdAttributionLink(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val campaignId: UUID,
    val code: String,
    val publicDisplayName: String?,
    val createdAt: Instant,
)
