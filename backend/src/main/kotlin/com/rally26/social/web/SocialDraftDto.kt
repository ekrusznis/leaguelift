package com.rally26.social.web

import com.rally26.social.domain.SocialPostDraft
import com.rally26.social.domain.SocialPublishingHistory
import java.time.Instant
import java.util.UUID

data class CreateSocialDraftRequest(
    val organizationId: UUID,
    val sourceType: String,
    val sourceId: UUID,
)

data class PublishSocialDraftRequest(
    val provider: String,
)

data class UpdateSocialDraftCaptionRequest(
    val caption: String,
)

data class SocialPublishingHistoryResponse(
    val id: UUID,
    val draftId: UUID,
    val provider: String,
    val sourceType: String,
    val sourceId: UUID,
    val publicUrl: String,
    val providerPostUrl: String?,
    val status: String,
    val failureMessageSafe: String?,
    val publishedAt: Instant?,
    val createdAt: Instant,
)

fun SocialPublishingHistory.toResponse() =
    SocialPublishingHistoryResponse(
        id = id,
        draftId = draftId,
        provider = provider.name,
        sourceType = sourceType.name,
        sourceId = sourceId,
        publicUrl = publicUrl,
        providerPostUrl = providerPostUrl,
        status = status.name,
        failureMessageSafe = failureMessageSafe,
        publishedAt = publishedAt,
        createdAt = createdAt,
    )

data class SocialPostDraftResponse(
    val id: UUID,
    val sourceType: String,
    val sourceId: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val title: String,
    val caption: String,
    val publicUrl: String,
    val allowedProviders: List<String>,
    val createdAt: Instant,
)

fun SocialPostDraft.toResponse() =
    SocialPostDraftResponse(
        id = id,
        sourceType = sourceType.name,
        sourceId = sourceId,
        organizationId = organizationId,
        teamId = teamId,
        title = title,
        caption = caption,
        publicUrl = publicUrl,
        allowedProviders = allowedProviders.map { it.name },
        createdAt = createdAt,
    )
