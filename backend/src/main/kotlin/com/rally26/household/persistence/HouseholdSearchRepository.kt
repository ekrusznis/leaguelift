package com.rally26.household.persistence

import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdSearchCriteria
import com.rally26.household.domain.HouseholdSearchSort
import com.rally26.household.domain.HouseholdStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

private const val HOUSEHOLD_COLS =
    "h.id, h.organization_id, h.display_name, h.contact_email, h.contact_phone, h.notes, " +
        "h.email_reminders_opt_out, h.sms_reminders_opt_in, h.status, h.created_at, h.updated_at"

/**
 * `/households/search` — see `TeamSearchRepository`'s own comment (LR-016): the
 * frontend's household list has always called this endpoint
 * (`frontend/src/features/households/searchApi.ts`) but no backend mapping for it
 * ever existed. `keyword`/`teamId` use `exists` subqueries (not a join) so a household
 * with several matching adults/participants is never returned more than once.
 */
@Repository
class HouseholdSearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun search(
        organizationId: UUID,
        criteria: HouseholdSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<Household> {
        val built = buildSql(organizationId, criteria, countOnly = false)
        var statement = jdbcClient.sql("${built.first} offset :offset limit :limit").param("offset", offset).param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapRow).list()
    }

    fun count(
        organizationId: UUID,
        criteria: HouseholdSearchCriteria,
    ): Long {
        val built = buildSql(organizationId, criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSql(
        organizationId: UUID,
        criteria: HouseholdSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from household h where h.organization_id = :organizationId"
                } else {
                    "select $HOUSEHOLD_COLS from household h where h.organization_id = :organizationId"
                },
            )
        val params = linkedMapOf<String, Any>("organizationId" to organizationId)

        criteria.status?.let {
            sql.append(" and h.status = :status")
            params["status"] = it.name
        }
        criteria.teamId?.let {
            sql.append(
                " and exists (" +
                    " select 1 from participant p" +
                    " join participant_team pt on pt.participant_id = p.id and pt.organization_id = p.organization_id" +
                    " where p.household_id = h.id and pt.team_id = :teamId and pt.status = 'ACTIVE'" +
                    " )",
            )
            params["teamId"] = it
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                " and (" +
                    " lower(h.display_name) like :keyword" +
                    " or lower(coalesce(h.contact_email, '')) like :keyword" +
                    " or exists (" +
                    "   select 1 from household_adult ha where ha.household_id = h.id" +
                    "   and (lower(coalesce(ha.email, '')) like :keyword" +
                    "     or lower(ha.first_name || ' ' || ha.last_name) like :keyword)" +
                    " )" +
                    " or exists (" +
                    "   select 1 from participant p where p.household_id = h.id" +
                    "   and lower(p.first_name || ' ' || p.last_name) like :keyword" +
                    " )" +
                    " )",
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    HouseholdSearchSort.NAME_ASC -> " order by lower(h.display_name) asc, h.created_at asc"
                    HouseholdSearchSort.NAME_DESC -> " order by lower(h.display_name) desc, h.created_at desc"
                    HouseholdSearchSort.NEWEST -> " order by h.created_at desc, lower(h.display_name) asc"
                    HouseholdSearchSort.OLDEST -> " order by h.created_at asc, lower(h.display_name) asc"
                },
            )
        }
        return sql.toString() to params
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Household =
        Household(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            displayName = rs.getString("display_name"),
            contactEmail = rs.getString("contact_email"),
            contactPhone = rs.getString("contact_phone"),
            notes = rs.getString("notes"),
            emailRemindersOptOut = rs.getBoolean("email_reminders_opt_out"),
            smsRemindersOptIn = rs.getBoolean("sms_reminders_opt_in"),
            status = HouseholdStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
