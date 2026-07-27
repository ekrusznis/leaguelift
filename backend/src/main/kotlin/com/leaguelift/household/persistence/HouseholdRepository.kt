package com.leaguelift.household.persistence

import com.leaguelift.household.domain.AdultStatus
import com.leaguelift.household.domain.Household
import com.leaguelift.household.domain.HouseholdAdult
import com.leaguelift.household.domain.HouseholdStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val HOUSEHOLD_COLS =
    "id, organization_id, display_name, contact_email, contact_phone, notes, status, created_at, updated_at"

private const val ADULT_COLS =
    "id, household_id, organization_id, first_name, last_name, email, phone, relationship, is_primary, status, created_at, updated_at"

@Repository
class HouseholdRepository(private val jdbcClient: JdbcClient) {

    fun findById(id: UUID, organizationId: UUID): Household? =
        jdbcClient.sql("select $HOUSEHOLD_COLS from household where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapHousehold)
            .optional().orElse(null)

    fun findAll(organizationId: UUID, offset: Int, limit: Int): List<Household> =
        jdbcClient.sql(
            """
            select $HOUSEHOLD_COLS from household
            where organization_id = :organizationId
            order by display_name asc
            offset :offset limit :limit
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapHousehold).list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from household where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java).single()

    fun insert(organizationId: UUID, displayName: String, contactEmail: String?, contactPhone: String?, notes: String?): Household {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into household (id, organization_id, display_name, contact_email, contact_phone, notes, status, created_at, updated_at)
            values (:id, :organizationId, :displayName, :contactEmail, :contactPhone, :notes, 'ACTIVE', :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("organizationId", organizationId).param("displayName", displayName)
            .param("contactEmail", contactEmail).param("contactPhone", contactPhone).param("notes", notes)
            .param("now", Timestamp.from(now)).update()
        return Household(id, organizationId, displayName, contactEmail, contactPhone, notes, HouseholdStatus.ACTIVE, now, now)
    }

    fun update(id: UUID, organizationId: UUID, displayName: String?, contactEmail: String?, contactPhone: String?, notes: String?): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update household
            set display_name   = coalesce(:displayName, display_name),
                contact_email  = coalesce(:contactEmail, contact_email),
                contact_phone  = coalesce(:contactPhone, contact_phone),
                notes          = coalesce(:notes, notes),
                updated_at     = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("displayName", displayName).param("contactEmail", contactEmail)
            .param("contactPhone", contactPhone).param("notes", notes)
            .param("now", Timestamp.from(now)).param("id", id).param("organizationId", organizationId).update()
    }

    fun listAdults(householdId: UUID, organizationId: UUID): List<HouseholdAdult> =
        jdbcClient.sql(
            """
            select $ADULT_COLS from household_adult
            where household_id = :householdId and organization_id = :organizationId and status = 'ACTIVE'
            order by is_primary desc, last_name asc, first_name asc
            """.trimIndent(),
        )
            .param("householdId", householdId).param("organizationId", organizationId)
            .query(::mapAdult).list()

    fun insertAdult(
        householdId: UUID,
        organizationId: UUID,
        firstName: String,
        lastName: String,
        email: String?,
        phone: String?,
        relationship: String?,
        isPrimary: Boolean,
    ): HouseholdAdult {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into household_adult
                (id, household_id, organization_id, first_name, last_name, email, phone, relationship, is_primary, status, created_at, updated_at)
            values
                (:id, :householdId, :organizationId, :firstName, :lastName, :email, :phone, :relationship, :isPrimary, 'ACTIVE', :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("householdId", householdId).param("organizationId", organizationId)
            .param("firstName", firstName).param("lastName", lastName).param("email", email)
            .param("phone", phone).param("relationship", relationship).param("isPrimary", isPrimary)
            .param("now", Timestamp.from(now)).update()
        return HouseholdAdult(id, householdId, organizationId, firstName, lastName, email, phone, relationship, isPrimary, AdultStatus.ACTIVE, now, now)
    }

    fun archiveAdult(adultId: UUID, householdId: UUID, organizationId: UUID): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update household_adult set status = 'ARCHIVED', updated_at = :now
            where id = :id and household_id = :householdId and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("now", Timestamp.from(now)).param("id", adultId)
            .param("householdId", householdId).param("organizationId", organizationId).update()
    }

    private fun mapHousehold(rs: java.sql.ResultSet, row: Int) = Household(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        displayName = rs.getString("display_name"),
        contactEmail = rs.getString("contact_email"),
        contactPhone = rs.getString("contact_phone"),
        notes = rs.getString("notes"),
        status = HouseholdStatus.valueOf(rs.getString("status")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapAdult(rs: java.sql.ResultSet, row: Int) = HouseholdAdult(
        id = rs.getObject("id", UUID::class.java),
        householdId = rs.getObject("household_id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        firstName = rs.getString("first_name"),
        lastName = rs.getString("last_name"),
        email = rs.getString("email"),
        phone = rs.getString("phone"),
        relationship = rs.getString("relationship"),
        isPrimary = rs.getBoolean("is_primary"),
        status = AdultStatus.valueOf(rs.getString("status")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
