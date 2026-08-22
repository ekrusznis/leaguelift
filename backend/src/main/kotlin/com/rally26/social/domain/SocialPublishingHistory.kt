package com.rally26.social.domain

import com.rally26.integration.core.domain.IntegrationProvider
import java.time.Instant
import java.util.UUID

enum class SocialPublishingStatus { PUBLISHING, PUBLISHED, FAILED }

data class SocialPublishingHistory(
    val id: UUID,
    val draftId: UUID,
    val userId: UUID,
    val organizationId: UUID,
    val provider: IntegrationProvider,
    val socialConnectionId: UUID?,
    val sourceType: SocialDraftSourceType,
    val sourceId: UUID,
    val captionSnapshot: String,
    val publicUrl: String,
    val providerPostId: String?,
    val providerPostUrl: String?,
    val status: SocialPublishingStatus,
    val failureCode: String?,
    val failureMessageSafe: String?,
    val publishedAt: Instant?,
    val createdAt: Instant,
)
