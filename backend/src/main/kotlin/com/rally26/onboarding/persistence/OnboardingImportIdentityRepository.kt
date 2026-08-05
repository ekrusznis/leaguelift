package com.rally26.onboarding.persistence

import com.rally26.onboarding.domain.OnboardingRecordType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class OnboardingImportIdentity(
    val id: UUID,
    val organizationId: UUID,
    val entityType: OnboardingRecordType,
    val externalId: String,
    val entityId: UUID,
    val createdByUserId: UUID,
    val createdAt: Instant,
)

@Repository
class OnboardingImportIdentityRepository(
    private val jdbcClient: JdbcClient,
) {
    fun find(
        organizationId: UUID,
        entityType: OnboardingRecordType,
        externalId: String,
    ): OnboardingImportIdentity? =
        jdbcClient
            .sql(
                """
                select id, organization_id, entity_type, external_id, entity_id, created_by_user_id, created_at
                from onboarding_import_identity
                where organization_id = :organizationId and entity_type = :entityType and external_id = :externalId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("entityType", entityType.name)
            .param("externalId", externalId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByEntity(
        organizationId: UUID,
        entityType: OnboardingRecordType,
        entityId: UUID,
    ): OnboardingImportIdentity? =
        jdbcClient
            .sql(
                """
                select id, organization_id, entity_type, external_id, entity_id, created_by_user_id, created_at
                from onboarding_import_identity
                where organization_id = :organizationId and entity_type = :entityType and entity_id = :entityId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("entityType", entityType.name)
            .param("entityId", entityId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        organizationId: UUID,
        entityType: OnboardingRecordType,
        externalId: String,
        entityId: UUID,
        createdByUserId: UUID,
    ): OnboardingImportIdentity {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into onboarding_import_identity
                	(id, organization_id, entity_type, external_id, entity_id, created_by_user_id, created_at)
                values
                	(:id, :organizationId, :entityType, :externalId, :entityId, :createdByUserId, :createdAt)
                on conflict do nothing
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("entityType", entityType.name)
            .param("externalId", externalId)
            .param("entityId", entityId)
            .param("createdByUserId", createdByUserId)
            .param("createdAt", Timestamp.from(now))
            .update()
        return find(organizationId, entityType, externalId)
            ?: findByEntity(organizationId, entityType, entityId)
            ?: error("Onboarding import identity was not persisted.")
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ) = OnboardingImportIdentity(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        entityType = OnboardingRecordType.valueOf(rs.getString("entity_type")),
        externalId = rs.getString("external_id"),
        entityId = rs.getObject("entity_id", UUID::class.java),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
