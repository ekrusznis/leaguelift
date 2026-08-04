package com.rally26.event.persistence

import com.rally26.event.domain.EventTemplate
import com.rally26.event.domain.EventTemplateStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val EVENT_TEMPLATE_COLUMNS = """
    id, organization_id, name, event_type, title, description,
    duration_minutes, arrival_offset_minutes, meeting_offset_minutes,
    timezone, venue_name, address, area, meeting_point, directions_notes,
    visibility, status, created_by_user_id, updated_by_user_id, created_at, updated_at
"""

@Repository
class EventTemplateRepository(private val jdbcClient: JdbcClient) {

    fun insert(
        organizationId: UUID,
        name: String,
        eventType: EventType,
        title: String?,
        description: String?,
        durationMinutes: Int?,
        arrivalOffsetMinutes: Int?,
        meetingOffsetMinutes: Int?,
        timezone: String,
        venueName: String?,
        address: String?,
        area: String?,
        meetingPoint: String?,
        directionsNotes: String?,
        visibility: EventVisibility,
        userId: UUID,
    ): EventTemplate {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient.sql(
            """
            insert into event_template (
                id, organization_id, name, event_type, title, description,
                duration_minutes, arrival_offset_minutes, meeting_offset_minutes,
                timezone, venue_name, address, area, meeting_point, directions_notes,
                visibility, status, created_by_user_id, updated_by_user_id, created_at, updated_at
            ) values (
                :id, :organizationId, :name, :eventType, :title, :description,
                :durationMinutes, :arrivalOffsetMinutes, :meetingOffsetMinutes,
                :timezone, :venueName, :address, :area, :meetingPoint, :directionsNotes,
                :visibility, 'ACTIVE', :userId, :userId, :now, :now
            )
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("eventType", eventType.name)
            .param("title", title)
            .param("description", description)
            .param("durationMinutes", durationMinutes)
            .param("arrivalOffsetMinutes", arrivalOffsetMinutes)
            .param("meetingOffsetMinutes", meetingOffsetMinutes)
            .param("timezone", timezone)
            .param("venueName", venueName)
            .param("address", address)
            .param("area", area)
            .param("meetingPoint", meetingPoint)
            .param("directionsNotes", directionsNotes)
            .param("visibility", visibility.name)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun findById(id: UUID, organizationId: UUID): EventTemplate? =
        jdbcClient.sql(
            """
            select $EVENT_TEMPLATE_COLUMNS
            from event_template
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listForOrganization(organizationId: UUID, includeArchived: Boolean): List<EventTemplate> =
        jdbcClient.sql(
            """
            select $EVENT_TEMPLATE_COLUMNS
            from event_template
            where organization_id = :organizationId
              and (:includeArchived or status = 'ACTIVE')
            order by case when status = 'ACTIVE' then 0 else 1 end, lower(name), created_at
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("includeArchived", includeArchived)
            .query(::mapRow)
            .list()

    fun update(
        id: UUID,
        organizationId: UUID,
        name: String,
        eventType: EventType,
        title: String?,
        description: String?,
        durationMinutes: Int?,
        arrivalOffsetMinutes: Int?,
        meetingOffsetMinutes: Int?,
        timezone: String,
        venueName: String?,
        address: String?,
        area: String?,
        meetingPoint: String?,
        directionsNotes: String?,
        visibility: EventVisibility,
        userId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update event_template
            set name = :name,
                event_type = :eventType,
                title = :title,
                description = :description,
                duration_minutes = :durationMinutes,
                arrival_offset_minutes = :arrivalOffsetMinutes,
                meeting_offset_minutes = :meetingOffsetMinutes,
                timezone = :timezone,
                venue_name = :venueName,
                address = :address,
                area = :area,
                meeting_point = :meetingPoint,
                directions_notes = :directionsNotes,
                visibility = :visibility,
                updated_by_user_id = :userId,
                updated_at = :now
            where id = :id and organization_id = :organizationId and status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("name", name)
            .param("eventType", eventType.name)
            .param("title", title)
            .param("description", description)
            .param("durationMinutes", durationMinutes)
            .param("arrivalOffsetMinutes", arrivalOffsetMinutes)
            .param("meetingOffsetMinutes", meetingOffsetMinutes)
            .param("timezone", timezone)
            .param("venueName", venueName)
            .param("address", address)
            .param("area", area)
            .param("meetingPoint", meetingPoint)
            .param("directionsNotes", directionsNotes)
            .param("visibility", visibility.name)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun archive(id: UUID, organizationId: UUID, userId: UUID): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update event_template
            set status = 'ARCHIVED', updated_by_user_id = :userId, updated_at = :now
            where id = :id and organization_id = :organizationId and status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(rs: java.sql.ResultSet, row: Int): EventTemplate =
        EventTemplate(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            eventType = EventType.valueOf(rs.getString("event_type")),
            title = rs.getString("title"),
            description = rs.getString("description"),
            durationMinutes = rs.getObject("duration_minutes") as Int?,
            arrivalOffsetMinutes = rs.getObject("arrival_offset_minutes") as Int?,
            meetingOffsetMinutes = rs.getObject("meeting_offset_minutes") as Int?,
            timezone = rs.getString("timezone"),
            venueName = rs.getString("venue_name"),
            address = rs.getString("address"),
            area = rs.getString("area"),
            meetingPoint = rs.getString("meeting_point"),
            directionsNotes = rs.getString("directions_notes"),
            visibility = EventVisibility.valueOf(rs.getString("visibility")),
            status = EventTemplateStatus.valueOf(rs.getString("status")),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
            updatedByUserId = rs.getObject("updated_by_user_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
