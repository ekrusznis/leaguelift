package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.MessageSafetyReportStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MessageSafetyPolicyTest {
    @Test
    fun `details are trimmed and optional`() {
        assertEquals("context", MessageSafetyPolicy.normalizeDetails("  context  "))
        assertNull(MessageSafetyPolicy.normalizeDetails("   "))
    }

    @Test
    fun `resolved and dismissed reports require a note`() {
        assertFailsWith<ValidationException> {
            MessageSafetyPolicy.normalizeReview(MessageSafetyReportStatus.IN_REVIEW, MessageSafetyReportStatus.RESOLVED, " ")
        }
        assertEquals(
            "Reviewed with family",
            MessageSafetyPolicy.normalizeReview(
                MessageSafetyReportStatus.IN_REVIEW,
                MessageSafetyReportStatus.DISMISSED,
                " Reviewed with family ",
            ),
        )
    }

    @Test
    fun `moderators cannot move report back to open`() {
        assertFailsWith<ValidationException> {
            MessageSafetyPolicy.normalizeReview(MessageSafetyReportStatus.OPEN, MessageSafetyReportStatus.OPEN, null)
        }
    }

    @Test
    fun `closed reports cannot be reopened`() {
        assertFailsWith<ValidationException> {
            MessageSafetyPolicy.normalizeReview(MessageSafetyReportStatus.RESOLVED, MessageSafetyReportStatus.IN_REVIEW, null)
        }
    }

    @Test
    fun `lock and unlock notes are bounded`() {
        assertEquals("Safety review", MessageSafetyPolicy.normalizeLockReason(" Safety review "))
        assertEquals("Reviewed", MessageSafetyPolicy.normalizeUnlockNote(" Reviewed "))
        assertFailsWith<ValidationException> { MessageSafetyPolicy.normalizeLockReason("bad") }
    }
}
