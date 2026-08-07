package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.GuardianObserverLink
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThreadMemberCandidate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AthleteConversationPolicyTest {
    private val athleteA = UUID.randomUUID()
    private val athleteB = UUID.randomUUID()
    private val guardian = UUID.randomUUID()

    @Test fun `athlete cannot select self`() {
        assertFailsWith<ValidationException> { AthleteConversationPolicy.requireTargetIds(listOf(athleteA), athleteA) }
    }

    @Test fun `guardian visibility is automatic and read only for creator and target athletes`() {
        val creator = MessageThreadMemberCandidate(athleteA, MessageRecipientType.ATHLETE, null, UUID.randomUUID(), "Athlete A")
        val target = MessageThreadMemberCandidate(athleteB, MessageRecipientType.ATHLETE, null, UUID.randomUUID(), "Athlete B")
        val observers =
            listOf(
                GuardianObserverLink(
                    athleteA,
                    MessageThreadMemberCandidate(
                        guardian,
                        MessageRecipientType.GUARDIAN,
                        null,
                        null,
                        "Guardian",
                        MessageAccessReason.GUARDIAN_VISIBILITY,
                        false,
                    ),
                ),
            )
        val merged = AthleteConversationPolicy.merge(creator, listOf(target), observers)
        val g = merged.single { it.userId == guardian }
        assertEquals(MessageAccessReason.GUARDIAN_VISIBILITY, g.accessReason)
        assertFalse(g.canReply)
        assertTrue(merged.single { it.userId == athleteA }.canReply)
        assertTrue(merged.single { it.userId == athleteB }.canReply)
    }
}
