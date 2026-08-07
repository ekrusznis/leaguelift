package com.rally26.store.persistence

import com.rally26.store.domain.AthleteStorefront
import com.rally26.store.domain.AthleteStorefrontStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, participant_id, team_id, store_id, slug, status, published_at, created_at, updated_at"

@Repository
class AthleteStorefrontRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): AthleteStorefront? =
        jdbcClient
            .sql("select $COLUMNS from athlete_storefront where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findBySlug(slug: String): AthleteStorefront? =
        jdbcClient
            .sql("select $COLUMNS from athlete_storefront where lower(slug) = lower(:slug)")
            .param("slug", slug)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAll(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<AthleteStorefront> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from athlete_storefront
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
            .sql("select count(*) from athlete_storefront where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        participantId: UUID,
        teamId: UUID?,
        storeId: UUID,
        slug: String,
    ): AthleteStorefront {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into athlete_storefront
                	(id, organization_id, participant_id, team_id, store_id, slug, status, created_at, updated_at)
                values
                	(:id, :organizationId, :participantId, :teamId, :storeId, :slug, 'DRAFT', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("participantId", participantId)
            .param("teamId", teamId)
            .param("storeId", storeId)
            .param("slug", slug)
            .param("now", Timestamp.from(now))
            .update()
        return AthleteStorefront(id, organizationId, participantId, teamId, storeId, slug, AthleteStorefrontStatus.DRAFT, null, now, now)
    }

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: AthleteStorefrontStatus,
        publishedAt: Instant?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update athlete_storefront
                set status = :status, published_at = :publishedAt, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("status", status.name)
            .param("publishedAt", publishedAt?.let { Timestamp.from(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun listProductIds(storefrontId: UUID): List<UUID> =
        jdbcClient
            .sql("select product_id from athlete_storefront_product where storefront_id = :storefrontId")
            .param("storefrontId", storefrontId)
            .query { rs, _ -> rs.getObject("product_id", UUID::class.java) }
            .list()

    /** Replaces the full approved-product set in one transaction (caller-managed via `@Transactional`). */
    fun replaceProducts(
        storefrontId: UUID,
        productIds: List<UUID>,
    ) {
        jdbcClient
            .sql("delete from athlete_storefront_product where storefront_id = :storefrontId")
            .param("storefrontId", storefrontId)
            .update()
        productIds.forEach { productId ->
            jdbcClient
                .sql(
                    "insert into athlete_storefront_product (storefront_id, product_id) values (:storefrontId, :productId)",
                ).param("storefrontId", storefrontId)
                .param("productId", productId)
                .update()
        }
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): AthleteStorefront =
        AthleteStorefront(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java),
            storeId = rs.getObject("store_id", UUID::class.java),
            slug = rs.getString("slug"),
            status = AthleteStorefrontStatus.valueOf(rs.getString("status")),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
