package com.rally26.foundingorg.persistence

import com.rally26.foundingorg.domain.FOUNDING_PILOT_DAYS
import com.rally26.foundingorg.domain.FoundingOrgPromoCode
import com.rally26.foundingorg.domain.FoundingPilotStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, code, reserved_by_user_id, reserved_at, organization_id, redeemed_at, pilot_ends_at, " +
        "pilot_status, next_reminder_index, created_at, updated_at"

@Repository
class FoundingOrgPromoCodeRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(code: String): FoundingOrgPromoCode {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into founding_org_promo_code (id, code, pilot_status, created_at, updated_at)
                values (:id, :code, 'UNREDEEMED', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("code", code)
            .param("now", Timestamp.from(now))
            .update()
        return FoundingOrgPromoCode(
            id = id,
            code = code,
            reservedByUserId = null,
            reservedAt = null,
            organizationId = null,
            redeemedAt = null,
            pilotEndsAt = null,
            pilotStatus = FoundingPilotStatus.UNREDEEMED,
            nextReminderIndex = 0,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun existsWithCode(code: String): Boolean =
        jdbcClient
            .sql("select 1 from founding_org_promo_code where code = :code")
            .param("code", code)
            .query(Int::class.java)
            .optional()
            .isPresent

    fun listAll(): List<FoundingOrgPromoCode> =
        jdbcClient
            .sql("select $COLUMNS from founding_org_promo_code order by created_at desc")
            .query(::mapRow)
            .list()

    fun findByCode(code: String): FoundingOrgPromoCode? =
        jdbcClient
            .sql("select $COLUMNS from founding_org_promo_code where code = :code")
            .param("code", code)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByCodeForUpdate(code: String): FoundingOrgPromoCode? =
        jdbcClient
            .sql("select $COLUMNS from founding_org_promo_code where code = :code for update")
            .param("code", code)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByReservedUserId(userId: UUID): FoundingOrgPromoCode? =
        jdbcClient
            .sql("select $COLUMNS from founding_org_promo_code where reserved_by_user_id = :userId and pilot_status = 'RESERVED'")
            .param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByOrganizationId(organizationId: UUID): FoundingOrgPromoCode? =
        jdbcClient
            .sql("select $COLUMNS from founding_org_promo_code where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Reserves an UNREDEEMED code for a just-registered (pre-organization) owner. */
    fun reserve(
        codeId: UUID,
        userId: UUID,
    ) {
        jdbcClient
            .sql(
                """
                update founding_org_promo_code
                set reserved_by_user_id = :userId, reserved_at = now(), pilot_status = 'RESERVED', updated_at = now()
                where id = :codeId and pilot_status = 'UNREDEEMED'
                """.trimIndent(),
            ).param("userId", userId)
            .param("codeId", codeId)
            .update()
    }

    /** Real redemption — the org now exists and the pilot subscription has just been activated. */
    fun activate(
        codeId: UUID,
        organizationId: UUID,
    ) {
        jdbcClient
            .sql(
                """
                update founding_org_promo_code
                set organization_id = :organizationId,
                    redeemed_at = now(),
                    pilot_ends_at = now() + interval '$FOUNDING_PILOT_DAYS days',
                    pilot_status = 'ACTIVE',
                    updated_at = now()
                where id = :codeId and pilot_status = 'RESERVED'
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("codeId", codeId)
            .update()
    }

    fun markConverted(organizationId: UUID) {
        jdbcClient
            .sql(
                "update founding_org_promo_code set pilot_status = 'CONVERTED', updated_at = now() " +
                    "where organization_id = :organizationId and pilot_status = 'ACTIVE'",
            ).param("organizationId", organizationId)
            .update()
    }

    fun markExpired(organizationId: UUID) {
        jdbcClient
            .sql(
                "update founding_org_promo_code set pilot_status = 'EXPIRED', updated_at = now() " +
                    "where organization_id = :organizationId and pilot_status = 'ACTIVE'",
            ).param("organizationId", organizationId)
            .update()
    }

    fun advanceReminderIndex(
        id: UUID,
        newIndex: Int,
    ) {
        jdbcClient
            .sql("update founding_org_promo_code set next_reminder_index = :newIndex, updated_at = now() where id = :id")
            .param("newIndex", newIndex)
            .param("id", id)
            .update()
    }

    fun listActivePilots(): List<FoundingOrgPromoCode> =
        jdbcClient
            .sql("select $COLUMNS from founding_org_promo_code where pilot_status = 'ACTIVE'")
            .query(::mapRow)
            .list()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): FoundingOrgPromoCode =
        FoundingOrgPromoCode(
            id = rs.getObject("id", UUID::class.java),
            code = rs.getString("code"),
            reservedByUserId = rs.getObject("reserved_by_user_id", UUID::class.java),
            reservedAt = rs.getTimestamp("reserved_at")?.toInstant(),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            redeemedAt = rs.getTimestamp("redeemed_at")?.toInstant(),
            pilotEndsAt = rs.getTimestamp("pilot_ends_at")?.toInstant(),
            pilotStatus = FoundingPilotStatus.valueOf(rs.getString("pilot_status")),
            nextReminderIndex = rs.getInt("next_reminder_index"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
