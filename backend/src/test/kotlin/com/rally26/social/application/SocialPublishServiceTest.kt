package com.rally26.social.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationAccessToken
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.domain.IntegrationAuthMode
import com.rally26.integration.core.domain.IntegrationCategory
import com.rally26.integration.core.domain.IntegrationConnection
import com.rally26.integration.core.domain.IntegrationConnectionStatus
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.persistence.IntegrationConnectionRepository
import com.rally26.social.domain.SocialDraftSourceType
import com.rally26.social.domain.SocialPostDraft
import com.rally26.social.domain.SocialPublishingHistory
import com.rally26.social.domain.SocialPublishingStatus
import com.rally26.social.persistence.SocialPostDraftRepository
import com.rally26.social.persistence.SocialPublishingHistoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SocialPublishServiceTest {
    private val draftRepository = mockk<SocialPostDraftRepository>()
    private val historyRepository = mockk<SocialPublishingHistoryRepository>(relaxed = true)
    private val connectionRepository = mockk<IntegrationConnectionRepository>()
    private val oauthService = mockk<IntegrationOAuthService>()
    private val publishingAdapterRegistry = mockk<SocialPublishingAdapterRegistry>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val service =
        SocialPublishService(
            draftRepository,
            historyRepository,
            connectionRepository,
            oauthService,
            publishingAdapterRegistry,
            auditService,
        )

    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
    private val organizationId = UUID.randomUUID()
    private val draftId = UUID.randomUUID()

    private fun draft(allowedProviders: List<IntegrationProvider> = listOf(IntegrationProvider.X)) =
        SocialPostDraft(
            id = draftId,
            sourceType = SocialDraftSourceType.FUNDRAISER,
            sourceId = UUID.randomUUID(),
            organizationId = organizationId,
            teamId = null,
            title = "12U National",
            caption = "Help us! https://rally26.com/campaigns/x",
            publicUrl = "https://rally26.com/campaigns/x",
            allowedProviders = allowedProviders,
            createdByUserId = currentUser.userId,
            createdAt = Instant.now(),
        )

    private fun connection() =
        IntegrationConnection(
            id = UUID.randomUUID(),
            provider = IntegrationProvider.X,
            category = IntegrationCategory.SOCIAL,
            ownerType = IntegrationOwnerType.USER,
            organizationId = null,
            userId = currentUser.userId,
            authMode = IntegrationAuthMode.OAUTH2,
            status = IntegrationConnectionStatus.CONNECTED,
            grantedScopes = emptyList(),
            externalAccountId = null,
            externalAccountName = "@rally26",
            credentialId = UUID.randomUUID(),
            accessTokenExpiresAt = null,
            refreshLockedAt = null,
            refreshLockedByUserId = null,
            lastSuccessfulSyncAt = null,
            lastHealthCheckAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            legacyResourceType = null,
            legacyResourceId = null,
            createdByUserId = currentUser.userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            connectedAt = Instant.now(),
            revokedAt = null,
            disconnectedAt = null,
        )

    private fun pendingHistory(connectionId: UUID) =
        SocialPublishingHistory(
            id = UUID.randomUUID(),
            draftId = draftId,
            userId = currentUser.userId,
            organizationId = organizationId,
            provider = IntegrationProvider.X,
            socialConnectionId = connectionId,
            sourceType = SocialDraftSourceType.FUNDRAISER,
            sourceId = UUID.randomUUID(),
            captionSnapshot = "Help us! https://rally26.com/campaigns/x",
            publicUrl = "https://rally26.com/campaigns/x",
            providerPostId = null,
            providerPostUrl = null,
            status = SocialPublishingStatus.PUBLISHING,
            failureCode = null,
            failureMessageSafe = null,
            publishedAt = null,
            createdAt = Instant.now(),
        )

    @Test
    fun `publishing to a provider the draft doesn't allow is rejected before touching any connection`() {
        every { draftRepository.findByIdForUser(draftId, currentUser.userId) } returns
            draft(allowedProviders = listOf(IntegrationProvider.FACEBOOK))

        assertFailsWith<ValidationException> {
            service.publish(draftId, IntegrationProvider.X, currentUser)
        }

        verify(exactly = 0) { connectionRepository.findActiveForUser(any(), any()) }
    }

    @Test
    fun `publishing with no adapter registered falls back to a clear error, not a crash`() {
        every { draftRepository.findByIdForUser(draftId, currentUser.userId) } returns
            draft(allowedProviders = listOf(IntegrationProvider.INSTAGRAM))
        every { publishingAdapterRegistry.find(IntegrationProvider.INSTAGRAM) } returns null

        assertFailsWith<ValidationException> {
            service.publish(draftId, IntegrationProvider.INSTAGRAM, currentUser)
        }
    }

    @Test
    fun `publishing with no active connection tells the user to connect first`() {
        every { draftRepository.findByIdForUser(draftId, currentUser.userId) } returns draft()
        every { publishingAdapterRegistry.find(IntegrationProvider.X) } returns mockk()
        every { connectionRepository.findActiveForUser(currentUser.userId, IntegrationProvider.X) } returns null

        assertFailsWith<ValidationException> {
            service.publish(draftId, IntegrationProvider.X, currentUser)
        }
    }

    @Test
    fun `a successful publish records history as PUBLISHED with the real provider post URL`() {
        val conn = connection()
        val adapter = mockk<SocialPublishingAdapter>()
        every { draftRepository.findByIdForUser(draftId, currentUser.userId) } returns draft()
        every { publishingAdapterRegistry.find(IntegrationProvider.X) } returns adapter
        every { connectionRepository.findActiveForUser(currentUser.userId, IntegrationProvider.X) } returns conn
        every { oauthService.accessTokenForUserConnection(conn.id, currentUser) } returns IntegrationAccessToken(conn, "real-token")
        every { adapter.publish(any()) } returns SocialPublishResult("tweet-123", "https://x.com/i/web/status/tweet-123")
        val history = pendingHistory(conn.id)
        every {
            historyRepository.insertPublishing(
                draftId = draftId,
                userId = currentUser.userId,
                organizationId = organizationId,
                provider = IntegrationProvider.X,
                socialConnectionId = conn.id,
                sourceType = SocialDraftSourceType.FUNDRAISER,
                sourceId = any(),
                captionSnapshot = any(),
                publicUrl = any(),
            )
        } returns history
        every { historyRepository.findById(history.id) } returns
            history.copy(
                status = SocialPublishingStatus.PUBLISHED,
                providerPostId = "tweet-123",
                providerPostUrl = "https://x.com/i/web/status/tweet-123",
            )

        val result = service.publish(draftId, IntegrationProvider.X, currentUser)

        verify(exactly = 1) { historyRepository.markPublished(history.id, "tweet-123", "https://x.com/i/web/status/tweet-123") }
        kotlin.test.assertEquals(SocialPublishingStatus.PUBLISHED, result.status)
    }

    @Test
    fun `a failed publish records history as FAILED and still surfaces the error`() {
        val conn = connection()
        val adapter = mockk<SocialPublishingAdapter>()
        every { draftRepository.findByIdForUser(draftId, currentUser.userId) } returns draft()
        every { publishingAdapterRegistry.find(IntegrationProvider.X) } returns adapter
        every { connectionRepository.findActiveForUser(currentUser.userId, IntegrationProvider.X) } returns conn
        every { oauthService.accessTokenForUserConnection(conn.id, currentUser) } returns IntegrationAccessToken(conn, "real-token")
        every { adapter.publish(any()) } throws ServiceUnavailableException("X_PUBLISH_FAILED", "X did not accept this post.")
        val history = pendingHistory(conn.id)
        every {
            historyRepository.insertPublishing(
                draftId = draftId,
                userId = currentUser.userId,
                organizationId = organizationId,
                provider = IntegrationProvider.X,
                socialConnectionId = conn.id,
                sourceType = SocialDraftSourceType.FUNDRAISER,
                sourceId = any(),
                captionSnapshot = any(),
                publicUrl = any(),
            )
        } returns history

        assertFailsWith<ServiceUnavailableException> {
            service.publish(draftId, IntegrationProvider.X, currentUser)
        }

        verify(exactly = 1) { historyRepository.markFailed(history.id, "X_PUBLISH_FAILED", any()) }
    }
}
