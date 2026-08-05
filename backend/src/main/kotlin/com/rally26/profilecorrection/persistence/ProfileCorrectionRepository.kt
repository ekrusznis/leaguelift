package com.rally26.profilecorrection.persistence

import com.rally26.profilecorrection.domain.ProfileCorrectionField
import com.rally26.profilecorrection.domain.ProfileCorrectionRequest
import com.rally26.profilecorrection.domain.ProfileCorrectionStatus
import com.rally26.profilecorrection.domain.ProfileCorrectionTargetType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val SELECT_COLUMNS = """
    r.id, r.organization_id, r.household_id, r.target_type, r.target_id, r.field,
    r.target_label, r.current_value, r.proposed_value, r.reason, r.status,
    r.requested_by, requester.display_name as requester_name, requester.email as requester_email,
    r.reviewed_by, reviewer.display_name as reviewer_name, r.review_note,
    r.requested_at, r.reviewed_at, r.updated_at
"""

@Repository
class ProfileCorrectionRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(
        organizationId: UUID,
        householdId: UUID,
        targetType: ProfileCorrectionTargetType,
        targetId: UUID,
        field: ProfileCorrectionField,
        targetLabel: String,
        currentValue: String?,
        proposedValue: String,
        reason: String,
        requestedBy: UUID,
    ): ProfileCorrectionRequest {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into profile_correction_request (
                    id, organization_id, household_id, target_type, target_id, field,
                    target_label, current_value, proposed_value, reason, status,
                    requested_by, requested_at, updated_at
                ) values (
                    :id, :organizationId, :householdId, :targetType, :targetId, :field,
                    :targetLabel, :currentValue, :proposedValue, :reason, 'PENDING',
                    :requestedBy, :now, :now
                )
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("targetType", targetType.name)
            .param("targetId", targetId)
            .param("field", field.name)
            .param("targetLabel", targetLabel)
            .param("currentValue", currentValue)
            .param("proposedValue", proposedValue)
            .param("reason", reason)
            .param("requestedBy", requestedBy)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun findById(
        id: UUID,
        organizationId: UUID,
    ): ProfileCorrectionRequest? =
        jdbcClient
            .sql(
                """
                select $SELECT_COLUMNS
                from profile_correction_request r
                join app_user requester on requester.id = r.requested_by
                left join app_user reviewer on reviewer.id = r.reviewed_by
                where r.id = :id and r.organization_id = :organizationId
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listForOrganization(
        organizationId: UUID,
        status: ProfileCorrectionStatus?,
        offset: Int,
        limit: Int,
    ): List<ProfileCorrectionRequest> =
        jdbcClient
            .sql(
                """
                select $SELECT_COLUMNS
                from profile_correction_request r
                join app_user requester on requester.id = r.requested_by
                left join app_user reviewer on reviewer.id = r.reviewed_by
                where r.organization_id = :organizationId
                  and (cast(:status as varchar) is null or r.status = cast(:status as varchar))
                order by case when r.status = 'PENDING' then 0 else 1 end, r.requested_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("status", status?.name)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countForOrganization(
        organizationId: UUID,
        status: ProfileCorrectionStatus?,
    ): Long =
        jdbcClient
            .sql(
                """
                select count(*)
                from profile_correction_request
                where organization_id = :organizationId
                  and (cast(:status as varchar) is null or status = cast(:status as varchar))
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("status", status?.name)
            .query(Long::class.java)
            .single()

    fun listForHousehold(
        organizationId: UUID,
        householdId: UUID,
    ): List<ProfileCorrectionRequest> =
        jdbcClient
            .sql(
                """
                select $SELECT_COLUMNS
                from profile_correction_request r
                join app_user requester on requester.id = r.requested_by
                left join app_user reviewer on reviewer.id = r.reviewed_by
                where r.organization_id = :organizationId and r.household_id = :householdId
                order by case when r.status = 'PENDING' then 0 else 1 end, r.requested_at desc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("householdId", householdId)
            .query(::mapRow)
            .list()

    fun hasPending(
        organizationId: UUID,
        targetType: ProfileCorrectionTargetType,
        targetId: UUID,
        field: ProfileCorrectionField,
    ): Boolean =
        jdbcClient
            .sql(
                """
                select exists(
                    select 1 from profile_correction_request
                    where organization_id = :organizationId
                      and target_type = :targetType
                      and target_id = :targetId
                      and field = :field
                      and status = 'PENDING'
                )
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("targetType", targetType.name)
            .param("targetId", targetId)
            .param("field", field.name)
            .query(Boolean::class.java)
            .single()

    fun review(
        id: UUID,
        organizationId: UUID,
        status: ProfileCorrectionStatus,
        reviewedBy: UUID,
        reviewNote: String?,
    ): Int {
        require(status == ProfileCorrectionStatus.APPROVED || status == ProfileCorrectionStatus.REJECTED)
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update profile_correction_request
                set status = :status,
                    reviewed_by = :reviewedBy,
                    review_note = :reviewNote,
                    reviewed_at = :now,
                    updated_at = :now
                where id = :id and organization_id = :organizationId and status = 'PENDING'
                """.trimIndent(),
            ).param("status", status.name)
            .param("reviewedBy", reviewedBy)
            .param("reviewNote", reviewNote)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun withdraw(
        id: UUID,
        organizationId: UUID,
        requestedBy: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update profile_correction_request
                set status = 'WITHDRAWN', reviewed_at = :now, updated_at = :now
                where id = :id and organization_id = :organizationId
                  and requested_by = :requestedBy and status = 'PENDING'
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .param("requestedBy", requestedBy)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        row: Int,
    ) = ProfileCorrectionRequest(
        id = rs.getObject("id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        householdId = rs.getObject("household_id", UUID::class.java),
        targetType = ProfileCorrectionTargetType.valueOf(rs.getString("target_type")),
        targetId = rs.getObject("target_id", UUID::class.java),
        field = ProfileCorrectionField.valueOf(rs.getString("field")),
        targetLabel = rs.getString("target_label"),
        currentValue = rs.getString("current_value"),
        proposedValue = rs.getString("proposed_value"),
        reason = rs.getString("reason"),
        status = ProfileCorrectionStatus.valueOf(rs.getString("status")),
        requestedBy = rs.getObject("requested_by", UUID::class.java),
        requesterName = rs.getString("requester_name"),
        requesterEmail = rs.getString("requester_email"),
        reviewedBy = rs.getObject("reviewed_by", UUID::class.java),
        reviewerName = rs.getString("reviewer_name"),
        reviewNote = rs.getString("review_note"),
        requestedAt = rs.getTimestamp("requested_at").toInstant(),
        reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
