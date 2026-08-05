package com.rally26.event.persistence

import com.rally26.event.domain.EventRsvp
import com.rally26.event.domain.RsvpResponse
import com.rally26.event.domain.RsvpSource
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, event_id, participant_id, response, note, responded_by_user_id, source, created_at, updated_at"

@Repository
class EventRsvpRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByEventAndParticipant(
        eventId: UUID,
        participantId: UUID,
    ): EventRsvp? =
        jdbcClient
            .sql("select $COLUMNS from event_rsvp where event_id = :eventId and participant_id = :participantId")
            .param("eventId", eventId)
            .param("participantId", participantId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByEvent(eventId: UUID): List<EventRsvp> =
        jdbcClient
            .sql("select $COLUMNS from event_rsvp where event_id = :eventId")
            .param("eventId", eventId)
            .query(::mapRow)
            .list()

    /**
     * "A later response replaces the effective response" (DESIGN-DOC.md section 14.1A) —
     * a single upsert on the `(event_id, participant_id)` unique constraint (V23),
     * not an append-only history table. `created_at` is preserved across an update via
     * the `ON CONFLICT` clause simply not touching it.
     */
    fun upsert(
        eventId: UUID,
        participantId: UUID,
        response: RsvpResponse,
        note: String?,
        respondedByUserId: UUID,
        source: RsvpSource,
    ): EventRsvp {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into event_rsvp (id, event_id, participant_id, response, note, responded_by_user_id, source, created_at, updated_at)
                values (:id, :eventId, :participantId, :response, :note, :respondedByUserId, :source, :now, :now)
                on conflict (event_id, participant_id) do update
                set response = :response, note = :note, responded_by_user_id = :respondedByUserId, source = :source, updated_at = :now
                """.trimIndent(),
            ).param("id", id)
            .param("eventId", eventId)
            .param("participantId", participantId)
            .param("response", response.name)
            .param("note", note)
            .param("respondedByUserId", respondedByUserId)
            .param("source", source.name)
            .param("now", java.sql.Timestamp.from(now))
            .update()
        return findByEventAndParticipant(eventId, participantId)!!
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): EventRsvp =
        EventRsvp(
            id = rs.getObject("id", UUID::class.java),
            eventId = rs.getObject("event_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            response = RsvpResponse.valueOf(rs.getString("response")),
            note = rs.getString("note"),
            respondedByUserId = rs.getObject("responded_by_user_id", UUID::class.java),
            source = RsvpSource.valueOf(rs.getString("source")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
