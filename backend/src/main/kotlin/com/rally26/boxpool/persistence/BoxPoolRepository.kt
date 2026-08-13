package com.rally26.boxpool.persistence

import com.rally26.boxpool.domain.BoxPool
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, campaign_id, organization_id, sport, rows, cols, price_per_box_minor, row_axis_label, col_axis_label, prize_description, " +
        "created_at, updated_at"

@Repository
class BoxPoolRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): BoxPool? =
        jdbcClient
            .sql("select $COLUMNS from box_pool where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByCampaignId(campaignId: UUID): BoxPool? =
        jdbcClient
            .sql("select $COLUMNS from box_pool where campaign_id = :campaignId")
            .param("campaignId", campaignId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        campaignId: UUID,
        organizationId: UUID,
        sport: String,
        rows: Int,
        cols: Int,
        pricePerBoxMinor: Long,
        rowAxisLabel: String?,
        colAxisLabel: String?,
        prizeDescription: String?,
    ): BoxPool {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into box_pool
                    (id, campaign_id, organization_id, sport, rows, cols, price_per_box_minor, row_axis_label, col_axis_label, prize_description, created_at, updated_at)
                values
                    (:id, :campaignId, :organizationId, :sport, :rows, :cols, :pricePerBoxMinor, :rowAxisLabel, :colAxisLabel, :prizeDescription, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("campaignId", campaignId)
            .param("organizationId", organizationId)
            .param("sport", sport)
            .param("rows", rows)
            .param("cols", cols)
            .param("pricePerBoxMinor", pricePerBoxMinor)
            .param("rowAxisLabel", rowAxisLabel)
            .param("colAxisLabel", colAxisLabel)
            .param("prizeDescription", prizeDescription)
            .param("now", java.sql.Timestamp.from(now))
            .update()
        return BoxPool(
            id = id,
            campaignId = campaignId,
            organizationId = organizationId,
            sport = sport,
            rows = rows,
            cols = cols,
            pricePerBoxMinor = pricePerBoxMinor,
            rowAxisLabel = rowAxisLabel,
            colAxisLabel = colAxisLabel,
            prizeDescription = prizeDescription,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): BoxPool =
        BoxPool(
            id = rs.getObject("id", UUID::class.java),
            campaignId = rs.getObject("campaign_id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            sport = rs.getString("sport"),
            rows = rs.getInt("rows"),
            cols = rs.getInt("cols"),
            pricePerBoxMinor = rs.getLong("price_per_box_minor"),
            rowAxisLabel = rs.getString("row_axis_label"),
            colAxisLabel = rs.getString("col_axis_label"),
            prizeDescription = rs.getString("prize_description"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
