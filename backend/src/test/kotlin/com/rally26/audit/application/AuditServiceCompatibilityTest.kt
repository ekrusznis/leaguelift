package com.rally26.audit.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test

class AuditServiceCompatibilityTest {
    @Test
    fun `legacy six argument mock stub remains compatible with phase 27 audit defaults`() {
        val auditService = mockk<AuditService>()
        val actorUserId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val entityId = UUID.randomUUID()

        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        auditService.record(
            actorUserId = actorUserId,
            organizationId = organizationId,
            action = "entity.updated",
            entityType = "ENTITY",
            entityId = entityId,
            metadataJson = "{}",
        )

        verify(exactly = 1) {
            auditService.record(
                actorUserId = actorUserId,
                organizationId = organizationId,
                action = "entity.updated",
                entityType = "ENTITY",
                entityId = entityId,
                metadataJson = "{}",
            )
        }
    }
}
