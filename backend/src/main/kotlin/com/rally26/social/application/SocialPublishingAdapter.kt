package com.rally26.social.application

import com.rally26.integration.core.domain.IntegrationProvider

data class SocialPublishRequest(
    val provider: IntegrationProvider,
    val accessToken: String,
    val externalAccountId: String?,
    val caption: String,
    val publicUrl: String,
)

data class SocialPublishResult(
    val providerPostId: String?,
    val providerPostUrl: String?,
)

/**
 * Social Sharing & Connected Accounts, Slice 5. The direct-publish half of brief §4 —
 * separate from [com.rally26.integration.core.application.IntegrationAuthorizationAdapter],
 * which only manages the OAuth connection. Only [FacebookPublishingAdapter] and
 * [XPublishingAdapter] exist — there's deliberately no Instagram implementation:
 * Instagram feed posts always require real media (image/video), and
 * [com.rally26.social.domain.SocialPostDraft] has no media attached yet (Slice 4,
 * not built this pass), so it would never have anything valid to post anyway.
 * [SocialPublishService] falls back to native mobile/web sharing (brief §4.B)
 * whenever no adapter claims the provider — this is what makes that fallback trigger
 * correctly for Instagram today, matching brief §6's example UX exactly.
 */
interface SocialPublishingAdapter {
    fun supports(provider: IntegrationProvider): Boolean

    fun publish(request: SocialPublishRequest): SocialPublishResult
}
