package com.rally26.social.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.social.domain.SocialDraftSourceType
import com.rally26.social.domain.SocialPostDraft
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

private const val COLUMNS =
    "id, source_type, source_id, organization_id, team_id, title, caption, public_url, allowed_providers, created_by_user_id, created_at"

@Repository
class SocialPostDraftRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun insert(
        sourceType: SocialDraftSourceType,
        sourceId: UUID,
        organizationId: UUID,
        teamId: UUID?,
        title: String,
        caption: String,
        publicUrl: String,
        allowedProviders: List<IntegrationProvider>,
        createdByUserId: UUID,
    ): SocialPostDraft {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into social_post_draft
                    (id, source_type, source_id, organization_id, team_id, title, caption, public_url, allowed_providers, created_by_user_id)
                values (:id, :sourceType, :sourceId, :organizationId, :teamId, :title, :caption, :publicUrl, :allowedProviders::jsonb, :createdByUserId)
                """.trimIndent(),
            ).param("id", id)
            .param("sourceType", sourceType.name)
            .param("sourceId", sourceId)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("title", title)
            .param("caption", caption)
            .param("publicUrl", publicUrl)
            .param("allowedProviders", objectMapper.writeValueAsString(allowedProviders.map { it.name }))
            .param("createdByUserId", createdByUserId)
            .update()
        return requireNotNull(findById(id))
    }

    fun findById(id: UUID): SocialPostDraft? =
        jdbcClient
            .sql("select $COLUMNS from social_post_draft where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByIdForUser(
        id: UUID,
        userId: UUID,
    ): SocialPostDraft? =
        jdbcClient
            .sql("select $COLUMNS from social_post_draft where id = :id and created_by_user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Lets the Share Composer's editable caption (brief §6) actually change what gets posted — [com.rally26.social.application.SocialPublishService] publishes whatever caption is persisted here, not a request-time override. */
    fun updateCaption(
        id: UUID,
        userId: UUID,
        caption: String,
    ): SocialPostDraft? {
        val updated =
            jdbcClient
                .sql("update social_post_draft set caption = :caption where id = :id and created_by_user_id = :userId")
                .param("caption", caption)
                .param("id", id)
                .param("userId", userId)
                .update()
        if (updated == 0) return null
        return findByIdForUser(id, userId)
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): SocialPostDraft =
        SocialPostDraft(
            id = rs.getObject("id", UUID::class.java),
            sourceType = SocialDraftSourceType.valueOf(rs.getString("source_type")),
            sourceId = rs.getObject("source_id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java),
            title = rs.getString("title"),
            caption = rs.getString("caption"),
            publicUrl = rs.getString("public_url"),
            allowedProviders =
                objectMapper
                    .readValue(rs.getString("allowed_providers"), Array<String>::class.java)
                    .map { IntegrationProvider.valueOf(it) },
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
