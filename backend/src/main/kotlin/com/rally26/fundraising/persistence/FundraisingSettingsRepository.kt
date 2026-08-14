package com.rally26.fundraising.persistence

import com.rally26.fundraising.domain.FundraisingSettings
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class FundraisingSettingsRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByOrganizationId(organizationId: UUID): FundraisingSettings? =
        jdbcClient
            .sql(
                """
                select organization_id, require_owner_approval, updated_by_user_id, updated_at
                from organization_fundraising_settings
                where organization_id = :organizationId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query { rs, _ ->
                FundraisingSettings(
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    requireOwnerApproval = rs.getBoolean("require_owner_approval"),
                    updatedByUserId = rs.getObject("updated_by_user_id", UUID::class.java),
                    updatedAt = rs.getTimestamp("updated_at")?.toInstant(),
                )
            }.optional()
            .orElse(null)

    fun upsert(
        organizationId: UUID,
        requireOwnerApproval: Boolean,
        updatedByUserId: UUID,
    ): FundraisingSettings {
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into organization_fundraising_settings (
                    organization_id, require_owner_approval, updated_by_user_id, updated_at
                ) values (
                    :organizationId, :requireOwnerApproval, :updatedByUserId, :updatedAt
                )
                on conflict (organization_id) do update
                set require_owner_approval = excluded.require_owner_approval,
                    updated_by_user_id = excluded.updated_by_user_id,
                    updated_at = excluded.updated_at
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("requireOwnerApproval", requireOwnerApproval)
            .param("updatedByUserId", updatedByUserId)
            .param("updatedAt", Timestamp.from(now))
            .update()

        return FundraisingSettings(
            organizationId = organizationId,
            requireOwnerApproval = requireOwnerApproval,
            updatedByUserId = updatedByUserId,
            updatedAt = now,
        )
    }
}
