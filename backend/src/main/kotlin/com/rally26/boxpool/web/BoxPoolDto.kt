package com.rally26.boxpool.web

import com.rally26.boxpool.domain.BoxPool
import com.rally26.boxpool.domain.BoxPoolBox
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateBoxPoolRequest(
    @field:NotBlank @field:Size(max = 60) val sport: String,
    @field:Min(1) @field:Max(26) val rows: Int,
    @field:Min(1) @field:Max(26) val cols: Int,
    @field:NotNull @field:Min(1) val pricePerBoxMinor: Long,
    @field:Size(max = 60) val rowAxisLabel: String? = null,
    @field:Size(max = 60) val colAxisLabel: String? = null,
    @field:Size(max = 2000) val prizeDescription: String? = null,
)

data class BoxPoolBoxResponse(
    val id: UUID,
    val rowIndex: Int,
    val colIndex: Int,
    val status: String,
    /** Never includes claimantEmail — this response is shared by the public endpoint too. */
    val claimantName: String?,
)

data class BoxPoolResponse(
    val id: UUID,
    val campaignId: UUID,
    val sport: String,
    val rows: Int,
    val cols: Int,
    val pricePerBoxMinor: Long,
    val rowAxisLabel: String?,
    val colAxisLabel: String?,
    val prizeDescription: String?,
    val boxes: List<BoxPoolBoxResponse>,
    val createdAt: Instant,
)

fun BoxPoolBox.toResponse() = BoxPoolBoxResponse(id, rowIndex, colIndex, status.name, claimantName)

fun Pair<BoxPool, List<BoxPoolBox>>.toResponse(): BoxPoolResponse {
    val (pool, boxes) = this
    return BoxPoolResponse(
        pool.id,
        pool.campaignId,
        pool.sport,
        pool.rows,
        pool.cols,
        pool.pricePerBoxMinor,
        pool.rowAxisLabel,
        pool.colAxisLabel,
        pool.prizeDescription,
        boxes.map { it.toResponse() },
        pool.createdAt,
    )
}

data class ReserveBoxRequest(
    @field:NotBlank @field:Size(max = 120) val claimantName: String,
    @field:Email @field:Size(max = 320) val claimantEmail: String? = null,
    @field:NotBlank val successUrl: String,
    @field:NotBlank val cancelUrl: String,
)

data class ReserveBoxResponse(
    val contributionId: UUID,
    val checkoutUrl: String,
)
