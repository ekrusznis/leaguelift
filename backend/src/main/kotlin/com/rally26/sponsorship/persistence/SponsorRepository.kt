package com.rally26.sponsorship.persistence

import com.rally26.sponsorship.domain.Sponsor
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, name, contact_email, phone, company_name, notes, created_at, updated_at"

@Repository
class SponsorRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): Sponsor? =
        jdbcClient
            .sql("select $COLUMNS from sponsor where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Sponsor? =
        jdbcClient
            .sql("select $COLUMNS from sponsor where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Inserted inline by `SponsorshipService.createCheckoutSession` — see `Sponsor`'s class doc for why there's no separate manual-entry admin flow this slice. `phone`/`companyName`/`notes` are never collected at checkout (the public request only ever supplies name/email) — an org admin fills them in afterward via `SponsorshipPackageService.updateSponsor`. */
    fun insert(
        organizationId: UUID,
        name: String,
        contactEmail: String?,
        phone: String? = null,
        companyName: String? = null,
        notes: String? = null,
    ): Sponsor {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into sponsor (id, organization_id, name, contact_email, phone, company_name, notes, created_at, updated_at)
                values (:id, :organizationId, :name, :contactEmail, :phone, :companyName, :notes, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("contactEmail", contactEmail)
            .param("phone", phone)
            .param("companyName", companyName)
            .param("notes", notes)
            .param("now", Timestamp.from(now))
            .update()
        return Sponsor(id, organizationId, name, contactEmail, phone, companyName, notes, now, now)
    }

    /** CRM update (Phase 6 remainder, ADR-019) — every field is optional/coalesced, mirroring `SponsorshipPackageRepository.update`. */
    fun update(
        id: UUID,
        organizationId: UUID,
        name: String?,
        contactEmail: String?,
        phone: String?,
        companyName: String?,
        notes: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update sponsor
                set name          = coalesce(:name, name),
                    contact_email = coalesce(:contactEmail, contact_email),
                    phone         = coalesce(:phone, phone),
                    company_name  = coalesce(:companyName, company_name),
                    notes         = coalesce(:notes, notes),
                    updated_at    = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("name", name)
            .param("contactEmail", contactEmail)
            .param("phone", phone)
            .param("companyName", companyName)
            .param("notes", notes)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Sponsor =
        Sponsor(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            contactEmail = rs.getString("contact_email"),
            phone = rs.getString("phone"),
            companyName = rs.getString("company_name"),
            notes = rs.getString("notes"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
