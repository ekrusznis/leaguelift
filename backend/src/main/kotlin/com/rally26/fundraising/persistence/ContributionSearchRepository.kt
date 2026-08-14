package com.rally26.fundraising.persistence

import com.rally26.fundraising.domain.ContributionListCriteria
import com.rally26.fundraising.domain.ContributionListSort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ContributionSearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun searchIds(
        organizationId: UUID,
        campaignId: UUID,
        criteria: ContributionListCriteria,
        offset: Int,
        limit: Int,
    ): List<UUID> {
        val built = buildSql(criteria, countOnly = false)

        var statement =
            jdbcClient
                .sql("${built.first} offset :offset limit :limit")
                .param("organizationId", organizationId)
                .param("campaignId", campaignId)
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
        campaignId: UUID,
        criteria: ContributionListCriteria,
    ): Long {
        val built = buildSql(criteria, countOnly = true)
        var statement =
            jdbcClient
                .sql(built.first)
                .param("organizationId", organizationId)
                .param("campaignId", campaignId)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSql(
        criteria: ContributionListCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    """
                    select count(*)
                    from contribution c
                    where c.organization_id = :organizationId
                      and c.campaign_id = :campaignId
                      and c.status in ('CONFIRMED', 'REFUNDED')
                    """.trimIndent()
                } else {
                    """
                    select c.id
                    from contribution c
                    where c.organization_id = :organizationId
                      and c.campaign_id = :campaignId
                      and c.status in ('CONFIRMED', 'REFUNDED')
                    """.trimIndent()
                },
            )
        val params = linkedMapOf<String, Any>()

        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                """
                and (
                   lower(coalesce(c.supporter_name, '')) like :keyword
                   or lower(coalesce(c.supporter_email, '')) like :keyword
                )
                """.trimIndent(),
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }
        criteria.status?.let {
            sql.append(" and c.status = :status")
            params["status"] = it.name
        }
        criteria.paymentSource?.let {
            sql.append(" and c.payment_source = :paymentSource")
            params["paymentSource"] = it.name
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    ContributionListSort.NEWEST -> {
                        " order by coalesce(c.confirmed_at, c.created_at) desc"
                    }

                    ContributionListSort.OLDEST -> {
                        " order by coalesce(c.confirmed_at, c.created_at) asc"
                    }

                    ContributionListSort.AMOUNT_DESC -> {
                        " order by c.amount_minor desc, c.created_at desc"
                    }

                    ContributionListSort.AMOUNT_ASC -> {
                        " order by c.amount_minor asc, c.created_at desc"
                    }

                    ContributionListSort.SUPPORTER_ASC -> {
                        " order by lower(coalesce(c.supporter_name, c.supporter_email, '')) asc, c.created_at desc"
                    }
                },
            )
        }
        return sql.toString() to params
    }
}
