package com.rally26.messaging.application

import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThreadMember
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageMembershipReconciliationPolicyTest {
    private fun member(
        type: MessageRecipientType,
        reason: MessageAccessReason,
    ) = MessageThreadMember(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        type,
        UUID.randomUUID(),
        if (type ==
            MessageRecipientType.ATHLETE
        ) {
            UUID.randomUUID()
        } else {
            null
        },
        "Member",
        reason,
        reason == MessageAccessReason.TARGETED,
        Instant.now(),
        null,
    )

    @Test fun `athlete leaving team becomes inactive for future sends`() {
        val m = member(MessageRecipientType.ATHLETE, MessageAccessReason.TARGETED)
        assertFalse(MessageMembershipReconciliationPolicy.shouldRemainActive(m, emptySet(), emptySet(), emptySet()))
    }

    @Test fun `current athlete remains active`() {
        val m = member(MessageRecipientType.ATHLETE, MessageAccessReason.TARGETED)
        assertTrue(MessageMembershipReconciliationPolicy.shouldRemainActive(m, setOf(m.userId), emptySet(), emptySet()))
    }

    @Test fun `old guardian observer is removed when relationship changes`() {
        val m = member(MessageRecipientType.GUARDIAN, MessageAccessReason.GUARDIAN_VISIBILITY)
        assertFalse(MessageMembershipReconciliationPolicy.shouldRemainActive(m, emptySet(), emptySet(), emptySet()))
    }

    @Test fun `current guardian observer remains read visible`() {
        val m = member(MessageRecipientType.GUARDIAN, MessageAccessReason.GUARDIAN_VISIBILITY)
        assertTrue(MessageMembershipReconciliationPolicy.shouldRemainActive(m, emptySet(), emptySet(), setOf(m.userId)))
    }

    @Test fun `staff stays in history but send time authorization decides participation`() {
        val m = member(MessageRecipientType.STAFF, MessageAccessReason.TARGETED)
        assertTrue(MessageMembershipReconciliationPolicy.shouldRemainActive(m, emptySet(), emptySet(), emptySet()))
    }
}
