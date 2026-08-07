package com.rally26.messaging.application

import com.rally26.common.error.ConflictException
import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.MessageSafeSportReviewStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MessageSafeSportPolicyTest {
    @Test fun `pending review clears external reference`() =
        assertNull(MessageSafeSportPolicyRules.normalizeReviewReference(MessageSafeSportReviewStatus.PENDING, "stale"))

    @Test fun `approved review requires durable reference`() {
        assertFailsWith<ValidationException> {
            MessageSafeSportPolicyRules.normalizeReviewReference(
                MessageSafeSportReviewStatus.APPROVED,
                " ",
            )
        }
    }

    @Test fun `athlete messaging cannot enable before approval`() {
        assertFailsWith<ConflictException> {
            MessageSafeSportPolicyRules.requireEnablementAllowed(MessageSafeSportReviewStatus.PENDING, true, null)
        }
    }

    @Test fun `approved gate can enable`() {
        val ref =
            MessageSafeSportPolicyRules.normalizeReviewReference(
                MessageSafeSportReviewStatus.APPROVED,
                "SafeSport review ticket R26-2026-001",
            )
        MessageSafeSportPolicyRules.requireEnablementAllowed(MessageSafeSportReviewStatus.APPROVED, true, ref)
        assertEquals("SafeSport review ticket R26-2026-001", ref)
    }
}
