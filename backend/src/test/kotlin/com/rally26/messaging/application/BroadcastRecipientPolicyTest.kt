package com.rally26.messaging.application

import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientCandidate
import com.rally26.messaging.domain.MessageRecipientType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BroadcastRecipientPolicyTest {
    @Test
    fun `targeted guardian record wins over transparency-only copy for same user`() {
        val userId = UUID.randomUUID()
        val visibility =
            MessageRecipientCandidate(
                MessageRecipientType.GUARDIAN,
                userId,
                UUID.randomUUID(),
                "Guardian",
                null,
                null,
                MessageAccessReason.GUARDIAN_VISIBILITY,
            )
        val targeted =
            MessageRecipientCandidate(
                MessageRecipientType.GUARDIAN,
                userId,
                visibility.householdId,
                "Guardian",
                "guardian@example.com",
                "+15555550100",
                MessageAccessReason.TARGETED,
            )

        val result = BroadcastRecipientPolicy.merge(listOf(visibility, targeted)).values.single()

        assertEquals(MessageAccessReason.TARGETED, result.accessReason)
        assertEquals("guardian@example.com", result.email)
        assertEquals("+15555550100", result.phone)
    }

    @Test
    fun `transparency-only guardian keeps external delivery coordinates absent`() {
        val candidate =
            MessageRecipientCandidate(
                MessageRecipientType.GUARDIAN,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Guardian",
                null,
                null,
                MessageAccessReason.GUARDIAN_VISIBILITY,
            )
        val result = BroadcastRecipientPolicy.merge(listOf(candidate)).values.single()
        assertEquals(MessageAccessReason.GUARDIAN_VISIBILITY, result.accessReason)
        assertNull(result.email)
        assertNull(result.phone)
    }
}
