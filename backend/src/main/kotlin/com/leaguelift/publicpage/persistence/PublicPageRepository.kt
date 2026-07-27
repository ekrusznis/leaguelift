package com.leaguelift.publicpage.persistence

import com.leaguelift.publicpage.domain.PageStatus
import com.leaguelift.publicpage.domain.PageType
import com.leaguelift.publicpage.domain.PublicPage
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val PAGE_COLUMNS =
    "id, organization_id, page_type, entity_id, slug, title, summary, status, published_at, created_at, updated_at"

@Repository
class PublicPageRepository(private val jdbcClient: JdbcClient) {

    fun findById(id: UUID, organizationId: UUID): PublicPage? =
        jdbcClient.sql("select $PAGE_COLUMNS from public_page where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findBySlug(slug: String): PublicPage? =
        jdbcClient.sql("select $PAGE_COLUMNS from public_page where lower(slug) = lower(:slug)")
            .param("slug", slug)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByEntityId(entityId: UUID): PublicPage? =
        jdbcClient.sql("select $PAGE_COLUMNS from public_page where entity_id = :entityId")
            .param("entityId", entityId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAllForOrg(organizationId: UUID, offset: Int, limit: Int): List<PublicPage> =
        jdbcClient.sql(
            """
            select $PAGE_COLUMNS from public_page
            where organization_id = :organizationId
            order by created_at desc
            offset :offset limit :limit
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countForOrg(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from public_page where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        pageType: PageType,
        entityId: UUID,
        slug: String,
        title: String,
        summary: String?,
    ): PublicPage {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into public_page
                (id, organization_id, page_type, entity_id, slug, title, summary, status, created_at, updated_at)
            values
                (:id, :organizationId, :pageType, :entityId, :slug, :title, :summary, 'DRAFT', :now, :now)
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .param("pageType", pageType.name)
            .param("entityId", entityId)
            .param("slug", slug)
            .param("title", title)
            .param("summary", summary)
            .param("now", Timestamp.from(now))
            .update()
        return PublicPage(
            id = id,
            organizationId = organizationId,
            pageType = pageType,
            entityId = entityId,
            slug = slug,
            title = title,
            summary = summary,
            status = PageStatus.DRAFT,
            publishedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(id: UUID, organizationId: UUID, title: String?, slug: String?, summary: String?): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update public_page
            set title      = coalesce(:title, title),
                slug       = coalesce(:slug, slug),
                summary    = coalesce(:summary, summary),
                updated_at = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("title", title)
            .param("slug", slug)
            .param("summary", summary)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun updateStatus(id: UUID, organizationId: UUID, status: PageStatus, publishedAt: Instant?): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update public_page
            set status       = :status,
                published_at = :publishedAt,
                updated_at   = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("status", status.name)
            .param("publishedAt", publishedAt?.let { Timestamp.from(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): PublicPage =
        PublicPage(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            pageType = PageType.valueOf(rs.getString("page_type")),
            entityId = rs.getObject("entity_id", UUID::class.java),
            slug = rs.getString("slug"),
            title = rs.getString("title"),
            summary = rs.getString("summary"),
            status = PageStatus.valueOf(rs.getString("status")),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
