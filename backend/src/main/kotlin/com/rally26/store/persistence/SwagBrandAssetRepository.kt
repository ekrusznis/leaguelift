package com.rally26.store.persistence

import com.rally26.store.domain.SwagBrandAsset
import com.rally26.store.domain.SwagBrandAssetCategory
import com.rally26.store.domain.SwagBrandAssetStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val BRAND_ASSET_COLUMNS = """
    id, organization_id, team_id, media_asset_id, name, category, status,
    created_by_user_id, created_at, updated_at
"""

@Repository
class SwagBrandAssetRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): SwagBrandAsset? =
        jdbcClient
            .sql("select $BRAND_ASSET_COLUMNS from swag_brand_asset where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Team workspaces can use organization-wide assets plus assets scoped to that team. */
    fun findAvailable(
        organizationId: UUID,
        teamId: UUID?,
        includeArchived: Boolean,
    ): List<SwagBrandAsset> {
        val scopeClause =
            if (teamId == null) {
                "team_id is null and (:includeArchived = true or status = 'ACTIVE')"
            } else {
                "((team_id = :teamId and (:includeArchived = true or status = 'ACTIVE')) or (team_id is null and status = 'ACTIVE'))"
            }
        val sql =
            """
            select $BRAND_ASSET_COLUMNS
            from swag_brand_asset
            where organization_id = :organizationId
              and $scopeClause
            order by case when team_id is null then 0 else 1 end, name asc, created_at asc
            """.trimIndent()
        val query =
            jdbcClient
                .sql(sql)
                .param("organizationId", organizationId)
                .param("includeArchived", includeArchived)
        return if (teamId == null) {
            query.query(::mapRow).list()
        } else {
            query.param("teamId", teamId).query(::mapRow).list()
        }
    }

    fun insert(
        organizationId: UUID,
        teamId: UUID?,
        mediaAssetId: UUID,
        name: String,
        category: SwagBrandAssetCategory,
        createdByUserId: UUID,
    ): SwagBrandAsset {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into swag_brand_asset
                    (id, organization_id, team_id, media_asset_id, name, category, status,
                     created_by_user_id, created_at, updated_at)
                values
                    (:id, :organizationId, :teamId, :mediaAssetId, :name, :category, 'ACTIVE',
                     :createdByUserId, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("mediaAssetId", mediaAssetId)
            .param("name", name)
            .param("category", category.name)
            .param("createdByUserId", createdByUserId)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: SwagBrandAssetStatus,
    ): Int =
        jdbcClient
            .sql("update swag_brand_asset set status = :status, updated_at = :now where id = :id and organization_id = :organizationId")
            .param("status", status.name)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: ResultSet,
        _rowNum: Int,
    ): SwagBrandAsset =
        SwagBrandAsset(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java),
            mediaAssetId = rs.getObject("media_asset_id", UUID::class.java),
            name = rs.getString("name"),
            category = SwagBrandAssetCategory.valueOf(rs.getString("category")),
            status = SwagBrandAssetStatus.valueOf(rs.getString("status")),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
