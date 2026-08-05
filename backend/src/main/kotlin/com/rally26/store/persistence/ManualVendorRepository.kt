package com.rally26.store.persistence

import com.rally26.store.domain.ManualVendor
import com.rally26.store.domain.ManualVendorStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val VENDOR_COLUMNS =
    "id, organization_id, name, contact_name, contact_email, phone, website_url, notes, status, created_at, updated_at"

@Repository
class ManualVendorRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): ManualVendor? =
        jdbcClient
            .sql("select $VENDOR_COLUMNS from manual_vendor where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findActiveByName(
        organizationId: UUID,
        name: String,
    ): ManualVendor? =
        jdbcClient
            .sql(
                "select $VENDOR_COLUMNS from manual_vendor where organization_id = :organizationId and lower(name) = lower(:name) and status = 'ACTIVE'",
            ).param("organizationId", organizationId)
            .param("name", name)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun list(
        organizationId: UUID,
        includeArchived: Boolean,
    ): List<ManualVendor> {
        val statusClause = if (includeArchived) "" else "and status = 'ACTIVE'"
        return jdbcClient
            .sql(
                "select $VENDOR_COLUMNS from manual_vendor where organization_id = :organizationId $statusClause order by lower(name), created_at",
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()
    }

    fun insert(
        organizationId: UUID,
        name: String,
        contactName: String?,
        contactEmail: String?,
        phone: String?,
        websiteUrl: String?,
        notes: String?,
    ): ManualVendor {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into manual_vendor
                	(id, organization_id, name, contact_name, contact_email, phone, website_url, notes, status, created_at, updated_at)
                values
                	(:id, :organizationId, :name, :contactName, :contactEmail, :phone, :websiteUrl, :notes, 'ACTIVE', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("contactName", contactName)
            .param("contactEmail", contactEmail)
            .param("phone", phone)
            .param("websiteUrl", websiteUrl)
            .param("notes", notes)
            .param("now", Timestamp.from(now))
            .update()
        return ManualVendor(
            id,
            organizationId,
            name,
            contactName,
            contactEmail,
            phone,
            websiteUrl,
            notes,
            ManualVendorStatus.ACTIVE,
            now,
            now,
        )
    }

    fun update(
        id: UUID,
        organizationId: UUID,
        name: String,
        contactName: String?,
        contactEmail: String?,
        phone: String?,
        websiteUrl: String?,
        notes: String?,
    ): Int =
        jdbcClient
            .sql(
                """
                update manual_vendor
                set name = :name, contact_name = :contactName, contact_email = :contactEmail,
                    phone = :phone, website_url = :websiteUrl, notes = :notes, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("name", name)
            .param("contactName", contactName)
            .param("contactEmail", contactEmail)
            .param("phone", phone)
            .param("websiteUrl", websiteUrl)
            .param("notes", notes)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun archive(
        id: UUID,
        organizationId: UUID,
    ): Int =
        jdbcClient
            .sql(
                "update manual_vendor set status = 'ARCHIVED', updated_at = :now where id = :id and organization_id = :organizationId and status = 'ACTIVE'",
            ).param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): ManualVendor =
        ManualVendor(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            contactName = rs.getString("contact_name"),
            contactEmail = rs.getString("contact_email"),
            phone = rs.getString("phone"),
            websiteUrl = rs.getString("website_url"),
            notes = rs.getString("notes"),
            status = ManualVendorStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
