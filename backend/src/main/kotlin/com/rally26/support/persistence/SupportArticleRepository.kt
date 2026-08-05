package com.rally26.support.persistence

import com.rally26.common.web.PageRequest
import com.rally26.support.domain.SupportArticle
import com.rally26.support.domain.SupportArticleStatus
import com.rally26.support.domain.SupportAudience
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val ARTICLE_COLUMNS =
    "id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at, created_by, updated_by, created_at, " +
        "updated_at"

@Repository
class SupportArticleRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listPublished(
        audiences: Set<SupportAudience>,
        query: String?,
        category: String?,
    ): List<SupportArticle> {
        val audienceSql = audiences.joinToString(",") { "'${it.name}'" }
        val filters = mutableListOf("status = 'PUBLISHED'", "audience in ($audienceSql)")
        if (!query.isNullOrBlank()) {
            filters +=
                "(lower(title) like :query or lower(summary) like :query or lower(body_markdown) like :query or lower(category) like :query)"
        }
        if (!category.isNullOrBlank()) filters += "lower(category) = lower(:category)"
        var spec =
            jdbcClient.sql(
                "select $ARTICLE_COLUMNS from support_article where ${filters.joinToString(" and ")} order by sort_order, title",
            )
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (!category.isNullOrBlank()) spec = spec.param("category", category.trim())
        return spec.query(::mapRow).list()
    }

    fun findPublishedBySlug(
        slug: String,
        audiences: Set<SupportAudience>,
    ): SupportArticle? {
        val audienceSql = audiences.joinToString(",") { "'${it.name}'" }
        return jdbcClient
            .sql(
                "select $ARTICLE_COLUMNS from support_article where lower(slug) = lower(:slug) and status = 'PUBLISHED' and audience in ($audienceSql)",
            ).param("slug", slug)
            .query(::mapRow)
            .optional()
            .orElse(null)
    }

    fun listAll(
        query: String?,
        status: SupportArticleStatus?,
        audience: SupportAudience?,
        category: String?,
        page: PageRequest,
    ): List<SupportArticle> {
        val where = filters(query, status, audience, category)
        var spec =
            jdbcClient
                .sql("select $ARTICLE_COLUMNS from support_article ${where.sql} order by updated_at desc limit :limit offset :offset")
                .param("limit", page.size)
                .param("offset", page.offset)
        spec = bind(spec, query, status, audience, category)
        return spec.query(::mapRow).list()
    }

    fun countAll(
        query: String?,
        status: SupportArticleStatus?,
        audience: SupportAudience?,
        category: String?,
    ): Long {
        val where = filters(query, status, audience, category)
        var spec = jdbcClient.sql("select count(*) from support_article ${where.sql}")
        spec = bind(spec, query, status, audience, category)
        return spec.query(Long::class.java).single()
    }

    fun findById(id: UUID): SupportArticle? =
        jdbcClient
            .sql("select $ARTICLE_COLUMNS from support_article where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        slug: String,
        title: String,
        summary: String,
        bodyMarkdown: String,
        category: String,
        audience: SupportAudience,
        sortOrder: Int,
        actorUserId: UUID,
    ): SupportArticle =
        jdbcClient
            .sql(
                """
                insert into support_article
                    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, created_by, updated_by, created_at, updated_at)
                values
                    (:id, :slug, :title, :summary, :body, :category, :audience, 'DRAFT', :sortOrder, :actor, :actor, now(), now())
                returning $ARTICLE_COLUMNS
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("slug", slug)
            .param("title", title)
            .param("summary", summary)
            .param("body", bodyMarkdown)
            .param("category", category)
            .param("audience", audience.name)
            .param("sortOrder", sortOrder)
            .param("actor", actorUserId)
            .query(::mapRow)
            .single()

    fun update(
        id: UUID,
        slug: String,
        title: String,
        summary: String,
        bodyMarkdown: String,
        category: String,
        audience: SupportAudience,
        sortOrder: Int,
        actorUserId: UUID,
    ): Int =
        jdbcClient
            .sql(
                """
                update support_article set slug = :slug, title = :title, summary = :summary, body_markdown = :body,
                    category = :category, audience = :audience, sort_order = :sortOrder, updated_by = :actor, updated_at = now()
                where id = :id and status <> 'ARCHIVED'
                """.trimIndent(),
            ).param("id", id)
            .param("slug", slug)
            .param("title", title)
            .param("summary", summary)
            .param("body", bodyMarkdown)
            .param("category", category)
            .param("audience", audience.name)
            .param("sortOrder", sortOrder)
            .param("actor", actorUserId)
            .update()

    fun publish(
        id: UUID,
        actorUserId: UUID,
        publishedAt: Instant,
    ): Int =
        jdbcClient
            .sql(
                "update support_article set status = 'PUBLISHED', published_at = :publishedAt, updated_by = :actor, updated_at = now() where id = :id and status <> 'ARCHIVED'",
            ).param("id", id)
            .param("actor", actorUserId)
            .param("publishedAt", Timestamp.from(publishedAt))
            .update()

    fun archive(
        id: UUID,
        actorUserId: UUID,
    ): Int =
        jdbcClient
            .sql(
                "update support_article set status = 'ARCHIVED', updated_by = :actor, updated_at = now() where id = :id and status <> 'ARCHIVED'",
            ).param("id", id)
            .param("actor", actorUserId)
            .update()

    private data class FilterSql(
        val sql: String,
    )

    private fun filters(
        query: String?,
        status: SupportArticleStatus?,
        audience: SupportAudience?,
        category: String?,
    ): FilterSql {
        val clauses = mutableListOf<String>()
        if (!query.isNullOrBlank()) clauses += "(lower(title) like :query or lower(summary) like :query or lower(slug) like :query)"
        if (status != null) clauses += "status = :status"
        if (audience != null) clauses += "audience = :audience"
        if (!category.isNullOrBlank()) clauses += "lower(category) = lower(:category)"
        return FilterSql(if (clauses.isEmpty()) "" else "where ${clauses.joinToString(" and ")}")
    }

    private fun bind(
        initial: JdbcClient.StatementSpec,
        query: String?,
        status: SupportArticleStatus?,
        audience: SupportAudience?,
        category: String?,
    ): JdbcClient.StatementSpec {
        var spec = initial
        if (!query.isNullOrBlank()) spec = spec.param("query", "%${query.trim().lowercase()}%")
        if (status != null) spec = spec.param("status", status.name)
        if (audience != null) spec = spec.param("audience", audience.name)
        if (!category.isNullOrBlank()) spec = spec.param("category", category.trim())
        return spec
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ) = SupportArticle(
        id = rs.getObject("id", UUID::class.java),
        slug = rs.getString("slug"),
        title = rs.getString("title"),
        summary = rs.getString("summary"),
        bodyMarkdown = rs.getString("body_markdown"),
        category = rs.getString("category"),
        audience = SupportAudience.valueOf(rs.getString("audience")),
        status = SupportArticleStatus.valueOf(rs.getString("status")),
        sortOrder = rs.getInt("sort_order"),
        publishedAt = rs.getTimestamp("published_at")?.toInstant(),
        createdBy = rs.getObject("created_by", UUID::class.java),
        updatedBy = rs.getObject("updated_by", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
