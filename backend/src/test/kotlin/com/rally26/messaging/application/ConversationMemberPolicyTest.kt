package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.GuardianObserverLink
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThreadMemberCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ConversationMemberPolicyTest {
    private val coachId = UUID.randomUUID()
    private val athleteId = UUID.randomUUID()
    private val guardianId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()

    @Test
    fun `athlete target automatically gives linked guardian read-only visibility`() {
        val members = ConversationMemberPolicy.merge(coach(), listOf(athlete()), listOf(observer()))
        val guardian = members.single { it.userId == guardianId }
        assertEquals(MessageAccessReason.GUARDIAN_VISIBILITY, guardian.accessReason)
        assertFalse(guardian.canReply)
    }

    @Test
    fun `explicitly targeted guardian wins over observer and may reply`() {
        val members = ConversationMemberPolicy.merge(coach(), listOf(athlete(), guardian()), listOf(observer()))
        val guardian = members.single { it.userId == guardianId }
        assertEquals(MessageAccessReason.TARGETED, guardian.accessReason)
        assertTrue(guardian.canReply)
    }

    @Test
    fun `observer is not added when related athlete is not selected`() {
        val members = ConversationMemberPolicy.merge(coach(), listOf(guardian()), listOf(observer()))
        assertEquals(2, members.size)
    }

    @Test
    fun `creator cannot be selected as family target`() {
        assertThrows(ValidationException::class.java) {
            ConversationMemberPolicy.requireTargetIds(listOf(coachId), coachId)
        }
    }

    private fun coach() = MessageThreadMemberCandidate(coachId, MessageRecipientType.STAFF, null, null, "Coach", canReply = true)

    private fun athlete() =
        MessageThreadMemberCandidate(athleteId, MessageRecipientType.ATHLETE, householdId, UUID.randomUUID(), "Athlete", canReply = true)

    private fun guardian() =
        MessageThreadMemberCandidate(guardianId, MessageRecipientType.GUARDIAN, householdId, null, "Guardian", canReply = true)

    private fun observer() =
        GuardianObserverLink(
            athleteId,
            MessageThreadMemberCandidate(
                guardianId,
                MessageRecipientType.GUARDIAN,
                householdId,
                null,
                "Guardian",
                MessageAccessReason.GUARDIAN_VISIBILITY,
                canReply = false,
            ),
        )
}
