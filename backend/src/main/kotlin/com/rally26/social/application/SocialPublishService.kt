package com.rally26.social.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ApiException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.persistence.IntegrationConnectionRepository
import com.rally26.social.domain.SocialPublishingHistory
import com.rally26.social.persistence.SocialPostDraftRepository
import com.rally26.social.persistence.SocialPublishingHistoryRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Social Sharing & Connected Accounts, Slice 5 — brief §4.A "Direct Publish". Brief
 * §4.B "Native Mobile Share" (the always-available fallback) is deliberately a
 * client-side concern (the OS share sheet), not this service — see
 * [SocialPublishingAdapterRegistry]'s doc comment for how the client knows to fall
 * back (no adapter found here means [ValidationException], which the mobile/web
 * Share Composer shows as "Direct publishing is unavailable for this post type"
 * per brief §6, then offers native share instead).
 */
@Service
class SocialPublishService(
    private val draftRepository: SocialPostDraftRepository,
    private val historyRepository: SocialPublishingHistoryRepository,
    private val connectionRepository: IntegrationConnectionRepository,
    private val oauthService: IntegrationOAuthService,
    private val publishingAdapterRegistry: SocialPublishingAdapterRegistry,
    private val auditService: AuditService,
) {
    fun publish(
        draftId: UUID,
        provider: IntegrationProvider,
        currentUser: CurrentUser,
    ): SocialPublishingHistory {
        val draft =
            draftRepository.findByIdForUser(draftId, currentUser.userId)
                ?: throw ValidationException("This draft could not be found.")
        if (provider !in draft.allowedProviders) {
            throw ValidationException("This content can't be posted to ${provider.name.lowercase().replaceFirstChar(Char::uppercase)}.")
        }
        val adapter =
            publishingAdapterRegistry.find(provider)
                ?: throw ValidationException("Direct publishing is unavailable for this post type.")
        val connection =
            connectionRepository.findActiveForUser(currentUser.userId, provider)
                ?: throw ValidationException("Connect ${provider.name.lowercase().replaceFirstChar(Char::uppercase)} before sharing to it.")

        val history =
            historyRepository.insertPublishing(
                draftId = draft.id,
                userId = currentUser.userId,
                organizationId = draft.organizationId,
                provider = provider,
                socialConnectionId = connection.id,
                sourceType = draft.sourceType,
                sourceId = draft.sourceId,
                captionSnapshot = draft.caption,
                publicUrl = draft.publicUrl,
            )

        return try {
            // Revalidated here, not just at connect time — the connection may have
            // expired/been revoked provider-side since the draft was opened.
            val accessToken = oauthService.accessTokenForUserConnection(connection.id, currentUser).accessToken
            val result =
                adapter.publish(
                    SocialPublishRequest(
                        provider = provider,
                        accessToken = accessToken,
                        externalAccountId = connection.externalAccountId,
                        caption = draft.caption,
                        publicUrl = draft.publicUrl,
                    ),
                )
            historyRepository.markPublished(history.id, result.providerPostId, result.providerPostUrl)
            auditService.record(
                currentUser.userId,
                draft.organizationId,
                "social.published",
                "social_publishing_history",
                history.id,
                metadataJson = """{"provider":"${provider.name}","sourceType":"${draft.sourceType.name}"}""",
            )
            requireNotNull(historyRepository.findById(history.id))
        } catch (ex: Exception) {
            val code = (ex as? ApiException)?.code ?: "SOCIAL_PUBLISH_FAILED"
            historyRepository.markFailed(history.id, code, "This post could not be published. Please try again.")
            auditService.record(
                currentUser.userId,
                draft.organizationId,
                "social.publish_failed",
                "social_publishing_history",
                history.id,
                metadataJson = """{"provider":"${provider.name}"}""",
            )
            throw ex
        }
    }

    fun history(
        currentUser: CurrentUser,
        limit: Int = 50,
    ): List<SocialPublishingHistory> = historyRepository.listForUser(currentUser.userId, limit.coerceIn(1, 200))
}
