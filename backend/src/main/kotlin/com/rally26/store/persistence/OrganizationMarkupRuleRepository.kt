package com.rally26.store.persistence

import com.rally26.store.domain.MarkupType
import com.rally26.store.domain.OrganizationMarkupRule
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, printify_blueprint_id, markup_type, markup_value, created_at, updated_at"

@Repository
class OrganizationMarkupRuleRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listForOrganization(organizationId: UUID): List<OrganizationMarkupRule> =
        jdbcClient
            .sql(
                "select $COLUMNS from organization_markup_rule where organization_id = :organizationId order by printify_blueprint_id nulls first",
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    /** Null blueprintId means the org-wide default rule. */
    fun findRule(
        organizationId: UUID,
        printifyBlueprintId: Long?,
    ): OrganizationMarkupRule? {
        val sql =
            if (printifyBlueprintId == null) {
                "select $COLUMNS from organization_markup_rule where organization_id = :organizationId and printify_blueprint_id is null"
            } else {
                "select $COLUMNS from organization_markup_rule where organization_id = :organizationId and printify_blueprint_id = :blueprintId"
            }
        var spec = jdbcClient.sql(sql).param("organizationId", organizationId)
        if (printifyBlueprintId != null) spec = spec.param("blueprintId", printifyBlueprintId)
        return spec.query(::mapRow).optional().orElse(null)
    }

    fun upsert(
        organizationId: UUID,
        printifyBlueprintId: Long?,
        markupType: MarkupType,
        markupValue: Int,
    ): OrganizationMarkupRule {
        val existing = findRule(organizationId, printifyBlueprintId)
        val now = Instant.now()
        if (existing != null) {
            jdbcClient
                .sql(
                    "update organization_markup_rule set markup_type = :markupType, markup_value = :markupValue, updated_at = :now where id = :id",
                ).param("markupType", markupType.name)
                .param("markupValue", markupValue)
                .param("now", Timestamp.from(now))
                .param("id", existing.id)
                .update()
            return existing.copy(markupType = markupType, markupValue = markupValue, updatedAt = now)
        }
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into organization_markup_rule
                    (id, organization_id, printify_blueprint_id, markup_type, markup_value, created_at, updated_at)
                values
                    (:id, :organizationId, :printifyBlueprintId, :markupType, :markupValue, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("printifyBlueprintId", printifyBlueprintId)
            .param("markupType", markupType.name)
            .param("markupValue", markupValue)
            .param("now", Timestamp.from(now))
            .update()
        return OrganizationMarkupRule(id, organizationId, printifyBlueprintId, markupType, markupValue, now, now)
    }

    fun delete(
        organizationId: UUID,
        id: UUID,
    ): Int =
        jdbcClient
            .sql("delete from organization_markup_rule where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): OrganizationMarkupRule =
        OrganizationMarkupRule(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            printifyBlueprintId = rs.getObject("printify_blueprint_id", java.lang.Long::class.java)?.toLong(),
            markupType = MarkupType.valueOf(rs.getString("markup_type")),
            markupValue = rs.getInt("markup_value"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
