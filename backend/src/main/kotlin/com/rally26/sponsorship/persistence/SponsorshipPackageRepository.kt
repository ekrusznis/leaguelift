package com.rally26.sponsorship.persistence

import com.rally26.sponsorship.domain.SponsorshipPackage
import com.rally26.sponsorship.domain.SponsorshipPackageSearchCriteria
import com.rally26.sponsorship.domain.SponsorshipPackageSearchRow
import com.rally26.sponsorship.domain.SponsorshipPackageSearchSort
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

private const val SEARCH_COLUMNS = """
    sp.id, sp.organization_id, sp.name, sp.description, sp.price_minor, sp.currency, sp.max_quantity, sp.exclusive,
    sp.placement_start_date, sp.placement_end_date, sp.status, sp.created_at, sp.updated_at
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

    /**
     * `/sponsorship-packages/search` — the frontend's package list has always called
     * this endpoint (`frontend/src/features/sponsorship/searchApi.ts`), but no backend
     * mapping for it ever existed (LR-026, same class as LR-016/018/020/025). The
     * confirmed-sponsorship count is computed in this query (a correlated subquery)
     * rather than N+1'd per row the way the plain [findAll]-based `list` endpoint does,
     * since search also needs to sort by it (`SPONSORS_DESC`).
     */
    fun search(
        organizationId: UUID,
        criteria: SponsorshipPackageSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<SponsorshipPackageSearchRow> {
        val built = buildSearchSql(organizationId, criteria, countOnly = false)
        var statement = jdbcClient.sql("${built.first} offset :offset limit :limit").param("offset", offset).param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapSearchRow).list()
    }

    fun countSearch(
        organizationId: UUID,
        criteria: SponsorshipPackageSearchCriteria,
    ): Long {
        val built = buildSearchSql(organizationId, criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSearchSql(
        organizationId: UUID,
        criteria: SponsorshipPackageSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val confirmedCountSubquery =
            "(select count(*) from sponsorship s where s.package_id = sp.id and s.status in ('CONFIRMED', 'REFUNDED'))"
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from sponsorship_package sp"
                } else {
                    "select $SEARCH_COLUMNS, $confirmedCountSubquery as confirmed_count from sponsorship_package sp"
                },
            )
        sql.append(" where sp.organization_id = :organizationId")
        val params = linkedMapOf<String, Any>("organizationId" to organizationId)

        criteria.status?.let {
            sql.append(" and sp.status = :status")
            params["status"] = it.name
        }
        criteria.exclusive?.let {
            sql.append(" and sp.exclusive = :exclusive")
            params["exclusive"] = it
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(" and (lower(sp.name) like :keyword or lower(coalesce(sp.description, '')) like :keyword)")
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    SponsorshipPackageSearchSort.NEWEST -> " order by sp.created_at desc"
                    SponsorshipPackageSearchSort.OLDEST -> " order by sp.created_at asc"
                    SponsorshipPackageSearchSort.NAME_ASC -> " order by lower(sp.name) asc"
                    SponsorshipPackageSearchSort.NAME_DESC -> " order by lower(sp.name) desc"
                    SponsorshipPackageSearchSort.PRICE_ASC -> " order by sp.price_minor asc"
                    SponsorshipPackageSearchSort.PRICE_DESC -> " order by sp.price_minor desc"
                    SponsorshipPackageSearchSort.SPONSORS_DESC -> " order by $confirmedCountSubquery desc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun mapSearchRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): SponsorshipPackageSearchRow =
        SponsorshipPackageSearchRow(
            sponsorshipPackage = mapRow(rs, rowNum),
            confirmedCount = rs.getLong("confirmed_count"),
        )

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
