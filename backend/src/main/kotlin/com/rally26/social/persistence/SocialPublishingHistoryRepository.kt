package com.rally26.social.persistence

import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.social.domain.SocialDraftSourceType
import com.rally26.social.domain.SocialPublishingHistory
import com.rally26.social.domain.SocialPublishingStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    """
    id, draft_id, user_id, organization_id, provider, social_connection_id, source_type, source_id,
    caption_snapshot, public_url, provider_post_id, provider_post_url, status, failure_code,
    failure_message_safe, published_at, created_at
    """

@Repository
class SocialPublishingHistoryRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insertPublishing(
        draftId: UUID,
        userId: UUID,
        organizationId: UUID,
        provider: IntegrationProvider,
        socialConnectionId: UUID?,
        sourceType: SocialDraftSourceType,
        sourceId: UUID,
        captionSnapshot: String,
        publicUrl: String,
    ): SocialPublishingHistory {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into social_publishing_history
                    (id, draft_id, user_id, organization_id, provider, social_connection_id, source_type, source_id,
                     caption_snapshot, public_url, status)
                values (:id, :draftId, :userId, :organizationId, :provider, :socialConnectionId, :sourceType, :sourceId,
                        :captionSnapshot, :publicUrl, 'PUBLISHING')
                """.trimIndent(),
            ).param("id", id)
            .param("draftId", draftId)
            .param("userId", userId)
            .param("organizationId", organizationId)
            .param("provider", provider.name)
            .param("socialConnectionId", socialConnectionId)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .param("captionSnapshot", captionSnapshot)
            .param("publicUrl", publicUrl)
            .update()
        return requireNotNull(findById(id))
    }

    fun markPublished(
        id: UUID,
        providerPostId: String?,
        providerPostUrl: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update social_publishing_history
                set status = 'PUBLISHED', provider_post_id = :providerPostId, provider_post_url = :providerPostUrl, published_at = :now
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .param("providerPostId", providerPostId)
            .param("providerPostUrl", providerPostUrl)
            .param("now", Timestamp.from(now))
            .update()
    }

    fun markFailed(
        id: UUID,
        failureCode: String,
        failureMessageSafe: String,
    ): Int =
        jdbcClient
            .sql(
                """
                update social_publishing_history
                set status = 'FAILED', failure_code = :failureCode, failure_message_safe = :failureMessageSafe
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .param("failureCode", failureCode)
            .param("failureMessageSafe", failureMessageSafe)
            .update()

    fun findById(id: UUID): SocialPublishingHistory? =
        jdbcClient
            .sql("select $COLUMNS from social_publishing_history where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listForUser(
        userId: UUID,
        limit: Int,
    ): List<SocialPublishingHistory> =
        jdbcClient
            .sql("select $COLUMNS from social_publishing_history where user_id = :userId order by created_at desc limit :limit")
            .param("userId", userId)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): SocialPublishingHistory =
        SocialPublishingHistory(
            id = rs.getObject("id", UUID::class.java),
            draftId = rs.getObject("draft_id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            provider = IntegrationProvider.valueOf(rs.getString("provider")),
            socialConnectionId = rs.getObject("social_connection_id", UUID::class.java),
            sourceType = SocialDraftSourceType.valueOf(rs.getString("source_type")),
            sourceId = rs.getObject("source_id", UUID::class.java),
            captionSnapshot = rs.getString("caption_snapshot"),
            publicUrl = rs.getString("public_url"),
            providerPostId = rs.getString("provider_post_id"),
            providerPostUrl = rs.getString("provider_post_url"),
            status = SocialPublishingStatus.valueOf(rs.getString("status")),
            failureCode = rs.getString("failure_code"),
            failureMessageSafe = rs.getString("failure_message_safe"),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
