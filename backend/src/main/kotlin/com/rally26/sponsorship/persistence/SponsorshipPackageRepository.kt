package com.rally26.sponsorship.persistence

import com.rally26.sponsorship.domain.SponsorshipPackage
import com.rally26.sponsorship.domain.SponsorshipPackageStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val COLUMNS = """
    id, organization_id, name, description, price_minor, currency, max_quantity, exclusive,
    placement_start_date, placement_end_date, status, created_at, updated_at
"""

@Repository
class SponsorshipPackageRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): SponsorshipPackage? =
        jdbcClient
            .sql("select $COLUMNS from sponsorship_package where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** No org scoping — used by the public checkout flow, which only ever has a `packageId` (mirrors `ContributionRepository.findById(id)`). Status/visibility is validated by the caller. */
    fun findById(id: UUID): SponsorshipPackage? =
        jdbcClient
            .sql("select $COLUMNS from sponsorship_package where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAll(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<SponsorshipPackage> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from sponsorship_package
                where organization_id = :organizationId
                order by created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient
            .sql("select count(*) from sponsorship_package where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun findPublished(organizationId: UUID): List<SponsorshipPackage> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from sponsorship_package
                where organization_id = :organizationId and status = 'PUBLISHED'
                order by created_at desc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    fun insert(
        organizationId: UUID,
        name: String,
        description: String?,
        priceMinor: Long,
        currency: String,
        maxQuantity: Int?,
        exclusive: Boolean,
        placementStartDate: LocalDate?,
        placementEndDate: LocalDate?,
    ): SponsorshipPackage {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into sponsorship_package
                	(id, organization_id, name, description, price_minor, currency, max_quantity, exclusive,
                	 placement_start_date, placement_end_date, status, created_at, updated_at)
                values
                	(:id, :organizationId, :name, :description, :priceMinor, :currency, :maxQuantity, :exclusive,
                	 :placementStartDate, :placementEndDate, 'DRAFT', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("description", description)
            .param("priceMinor", priceMinor)
            .param("currency", currency)
            .param("maxQuantity", maxQuantity)
            .param("exclusive", exclusive)
            .param("placementStartDate", placementStartDate?.let { Date.valueOf(it) })
            .param("placementEndDate", placementEndDate?.let { Date.valueOf(it) })
            .param("now", Timestamp.from(now))
            .update()
        return SponsorshipPackage(
            id,
            organizationId,
            name,
            description,
            priceMinor,
            currency,
            maxQuantity,
            exclusive,
            placementStartDate,
            placementEndDate,
            SponsorshipPackageStatus.DRAFT,
            now,
            now,
        )
    }

    fun update(
        id: UUID,
        organizationId: UUID,
        name: String?,
        description: String?,
        priceMinor: Long?,
        maxQuantity: Int?,
        exclusive: Boolean?,
        placementStartDate: LocalDate?,
        placementEndDate: LocalDate?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update sponsorship_package
                set name                 = coalesce(:name, name),
                    description          = coalesce(:description, description),
                    price_minor          = coalesce(:priceMinor, price_minor),
                    max_quantity         = coalesce(:maxQuantity, max_quantity),
                    exclusive            = coalesce(:exclusive, exclusive),
                    placement_start_date = coalesce(:placementStartDate, placement_start_date),
                    placement_end_date   = coalesce(:placementEndDate, placement_end_date),
                    updated_at           = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("name", name)
            .param("description", description)
            .param("priceMinor", priceMinor)
            .param("maxQuantity", maxQuantity)
            .param("exclusive", exclusive)
            .param("placementStartDate", placementStartDate?.let { Date.valueOf(it) })
            .param("placementEndDate", placementEndDate?.let { Date.valueOf(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: SponsorshipPackageStatus,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                "update sponsorship_package set status = :status, updated_at = :now where id = :id and organization_id = :organizationId",
            ).param("status", status.name)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): SponsorshipPackage =
        SponsorshipPackage(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            description = rs.getString("description"),
            priceMinor = rs.getLong("price_minor"),
            currency = rs.getString("currency"),
            maxQuantity = rs.getObject("max_quantity") as Int?,
            exclusive = rs.getBoolean("exclusive"),
            placementStartDate = rs.getDate("placement_start_date")?.toLocalDate(),
            placementEndDate = rs.getDate("placement_end_date")?.toLocalDate(),
            status = SponsorshipPackageStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
