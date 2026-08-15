package com.rally26.fundraising.persistence

import com.rally26.fundraising.domain.CampaignListCriteria
import com.rally26.fundraising.domain.CampaignListSort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CampaignSearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun searchIds(
        organizationId: UUID,
        criteria: CampaignListCriteria,
        offset: Int,
        limit: Int,
    ): List<UUID> {
        val built = buildSql(criteria, countOnly = false)

        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("offset", offset)
                .param("limit", limit)

        built.second.forEach { (name, value) ->
            statement = statement.param(name, value)
        }

        return statement
            .query(UUID::class.java)
            .list()
            .filterNotNull()
    }

    fun count(
        organizationId: UUID,
        criteria: CampaignListCriteria,
    ): Long {
        val built = buildSql(criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first).param("organizationId", organizationId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSql(
        criteria: CampaignListCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from campaign c where c.organization_id = :organizationId"
                } else {
                    "select c.id from campaign c where c.organization_id = :organizationId"
                },
            )
        val params = linkedMapOf<String, Any>()

        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                """
                and (
                   lower(c.name) like :keyword
                   or lower(c.slug) like :keyword
                   or lower(coalesce(c.description, '')) like :keyword
                   or lower(coalesce(c.event_location_name, '')) like :keyword
                   or lower(coalesce(c.event_address, '')) like :keyword
                )
                """.trimIndent(),
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }
        criteria.status?.let {
            sql.append(" and c.status = :status")
            params["status"] = it.name
        }
        criteria.campaignType?.let {
            sql.append(" and c.campaign_type = :campaignType")
            params["campaignType"] = it.name
        }
        criteria.templateKey?.let {
            sql.append(" and c.template_key = :templateKey")
            params["templateKey"] = it.name
        }
        criteria.teamId?.let {
            sql.append(" and c.team_id = :teamId")
            params["teamId"] = it
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    CampaignListSort.NEWEST -> {
                        " order by c.created_at desc"
                    }

                    CampaignListSort.NAME_ASC -> {
                        " order by lower(c.name) asc, c.created_at desc"
                    }

                    CampaignListSort.START_DATE_ASC -> {
                        " order by c.start_date asc nulls last, c.created_at desc"
                    }

                    CampaignListSort.END_DATE_ASC -> {
                        " order by c.end_date asc nulls last, c.created_at desc"
                    }

                    CampaignListSort.RAISED_DESC -> {
                        """
                        order by (
                           select coalesce(sum(con.amount_minor), 0)
                           from contribution con
                           where con.campaign_id = c.id and con.status = 'CONFIRMED'
                        ) desc, c.created_at desc
                        """.trimIndent()
                    }

                    CampaignListSort.GOAL_DESC -> {
                        " order by c.goal_amount_minor desc, c.created_at desc"
                    }
                },
            )
        }
        return sql.toString() to params
    }
}
