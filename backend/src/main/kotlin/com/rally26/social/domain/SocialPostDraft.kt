package com.rally26.social.domain

import com.rally26.integration.core.domain.IntegrationProvider
import java.time.Instant
import java.util.UUID

/**
 * Social Sharing & Connected Accounts, Slice 3. Only [FUNDRAISER] is wired to real
 * content generation this pass (see [com.rally26.social.application.SocialDraftService]) —
 * the others are real, named source types so the mobile/web Share Composer can be
 * built against the full enum now, but requesting a draft for them currently fails
 * with a clear "not yet supported" error rather than silently returning nothing.
 */
enum class SocialDraftSourceType { EVENT, FUNDRAISER, SPONSORSHIP, MEDIA, SWAG_PRODUCT, SWAG_SHOP }

data class SocialPostDraft(
    val id: UUID,
    val sourceType: SocialDraftSourceType,
    val sourceId: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val title: String,
    val caption: String,
    val publicUrl: String,
    val allowedProviders: List<IntegrationProvider>,
    val createdByUserId: UUID,
    val createdAt: Instant,
)
