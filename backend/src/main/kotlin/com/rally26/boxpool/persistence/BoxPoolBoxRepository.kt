package com.rally26.boxpool.persistence

import com.rally26.boxpool.domain.BoxPoolBox
import com.rally26.boxpool.domain.BoxPoolBoxStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, box_pool_id, row_index, col_index, status, claimant_name, claimant_email, contribution_id, reserved_until, claimed_at, " +
        "created_at, updated_at"

@Repository
class BoxPoolBoxRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listByPool(boxPoolId: UUID): List<BoxPoolBox> =
        jdbcClient
            .sql("select $COLUMNS from box_pool_box where box_pool_id = :boxPoolId order by row_index, col_index")
            .param("boxPoolId", boxPoolId)
            .query(::mapRow)
            .list()

    fun findById(id: UUID): BoxPoolBox? =
        jdbcClient
            .sql("select $COLUMNS from box_pool_box where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByContributionId(contributionId: UUID): BoxPoolBox? =
        jdbcClient
            .sql("select $COLUMNS from box_pool_box where contribution_id = :contributionId")
            .param("contributionId", contributionId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** A box is claimable if it's still OPEN, or RESERVED but that reservation has lazily expired — checked here rather than via a scheduled sweep job, matching this codebase's existing lazy-expiry conventions (e.g. invitation tokens). */
    fun findClaimableByPosition(
        boxPoolId: UUID,
        rowIndex: Int,
        colIndex: Int,
    ): BoxPoolBox? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from box_pool_box
                where box_pool_id = :boxPoolId and row_index = :rowIndex and col_index = :colIndex
                  and (status = 'OPEN' or (status = 'RESERVED' and reserved_until < :now))
                """.trimIndent(),
            ).param("boxPoolId", boxPoolId)
            .param("rowIndex", rowIndex)
            .param("colIndex", colIndex)
            .param("now", Timestamp.from(Instant.now()))
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insertGrid(
        boxPoolId: UUID,
        rows: Int,
        cols: Int,
    ): List<BoxPoolBox> {
        val now = Instant.now()
        val boxes = mutableListOf<BoxPoolBox>()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val id = UUID.randomUUID()
                jdbcClient
                    .sql(
                        """
                        insert into box_pool_box (id, box_pool_id, row_index, col_index, status, created_at, updated_at)
                        values (:id, :boxPoolId, :rowIndex, :colIndex, 'OPEN', :now, :now)
                        """.trimIndent(),
                    ).param("id", id)
                    .param("boxPoolId", boxPoolId)
                    .param("rowIndex", row)
                    .param("colIndex", col)
                    .param("now", Timestamp.from(now))
                    .update()
                boxes +=
                    BoxPoolBox(
                        id = id,
                        boxPoolId = boxPoolId,
                        rowIndex = row,
                        colIndex = col,
                        status = BoxPoolBoxStatus.OPEN,
                        claimantName = null,
                        claimantEmail = null,
                        contributionId = null,
                        reservedUntil = null,
                        claimedAt = null,
                        createdAt = now,
                        updatedAt = now,
                    )
            }
        }
        return boxes
    }

    /** Reserves an OPEN (or lazily-expired RESERVED) box against a newly-created pending contribution — a 15-minute window to complete Stripe checkout before another claimant can take it. */
    fun reserve(
        id: UUID,
        claimantName: String,
        claimantEmail: String?,
        contributionId: UUID,
        reservedUntil: Instant,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update box_pool_box
                set status = 'RESERVED', claimant_name = :claimantName, claimant_email = :claimantEmail,
                    contribution_id = :contributionId, reserved_until = :reservedUntil, updated_at = :now
                where id = :id and (status = 'OPEN' or (status = 'RESERVED' and reserved_until < :now))
                """.trimIndent(),
            ).param("id", id)
            .param("claimantName", claimantName)
            .param("claimantEmail", claimantEmail)
            .param("contributionId", contributionId)
            .param("reservedUntil", Timestamp.from(reservedUntil))
            .param("now", Timestamp.from(now))
            .update()
    }

    /** Flips a RESERVED box to CLAIMED once its linked contribution's payment is confirmed (called from `BoxPoolBoxClaimHandler`, not directly). */
    fun claim(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                "update box_pool_box set status = 'CLAIMED', claimed_at = :now, updated_at = :now where id = :id and status = 'RESERVED'",
            ).param("id", id)
            .param("now", Timestamp.from(now))
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): BoxPoolBox =
        BoxPoolBox(
            id = rs.getObject("id", UUID::class.java),
            boxPoolId = rs.getObject("box_pool_id", UUID::class.java),
            rowIndex = rs.getInt("row_index"),
            colIndex = rs.getInt("col_index"),
            status = BoxPoolBoxStatus.valueOf(rs.getString("status")),
            claimantName = rs.getString("claimant_name"),
            claimantEmail = rs.getString("claimant_email"),
            contributionId = rs.getObject("contribution_id", UUID::class.java),
            reservedUntil = rs.getTimestamp("reserved_until")?.toInstant(),
            claimedAt = rs.getTimestamp("claimed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
