package com.rally26.audit.domain

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuditEventTest {
    @Test
    fun `legacy positional construction keeps safe phase 27 defaults`() {
        val actorId = UUID.randomUUID()
        val event =
            AuditEvent(
                UUID.randomUUID(),
                actorId,
                UUID.randomUUID(),
                "TEAM_UPDATED",
                "TEAM",
                UUID.randomUUID(),
                "{}",
                Instant.parse("2026-08-07T20:00:00Z"),
            )

        assertEquals(AuditActorType.USER, event.actorType)
        assertEquals(AuditResult.SUCCESS, event.result)
        assertEquals("TEAM_UPDATED", event.summary)
        assertNull(event.teamId)
        assertNull(event.householdId)
        assertNull(event.participantId)
        assertNull(event.targetUserId)
        assertNull(event.correlationId)
    }

    @Test
    fun `events without a user actor default to system actor type`() {
        val event =
            AuditEvent(
                id = UUID.randomUUID(),
                actorUserId = null,
                organizationId = null,
                action = "SCHEDULED_JOB_RAN",
                entityType = "SYSTEM",
                entityId = UUID.randomUUID(),
                metadata = "{}",
                createdAt = Instant.parse("2026-08-07T20:00:00Z"),
            )

        assertEquals(AuditActorType.SYSTEM, event.actorType)
    }
}
