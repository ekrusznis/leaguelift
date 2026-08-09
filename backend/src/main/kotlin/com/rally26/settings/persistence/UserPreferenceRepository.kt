package com.rally26.settings.persistence

import com.rally26.settings.domain.AppearancePreference
import com.rally26.settings.domain.UserPreference
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserPreferenceRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByUserId(userId: UUID): UserPreference? =
        jdbcClient
            .sql(
                """
                select user_id, appearance, created_at, updated_at
                from user_preference
                where user_id = :userId
                """.trimIndent(),
            ).param("userId", userId)
            .query { rs, _ ->
                UserPreference(
                    userId = rs.getObject("user_id", UUID::class.java),
                    appearance = AppearancePreference.valueOf(rs.getString("appearance")),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    updatedAt = rs.getTimestamp("updated_at").toInstant(),
                )
            }.optional()
            .orElse(null)

    fun upsertAppearance(
        userId: UUID,
        appearance: AppearancePreference,
    ): UserPreference {
        jdbcClient
            .sql(
                """
                insert into user_preference (user_id, appearance)
                values (:userId, :appearance)
                on conflict (user_id)
                do update set
                    appearance = excluded.appearance,
                    updated_at = now()
                """.trimIndent(),
            ).param("userId", userId)
            .param("appearance", appearance.name)
            .update()

        return requireNotNull(findByUserId(userId)) {
            "User preference row was not available after upsert."
        }
    }
}
