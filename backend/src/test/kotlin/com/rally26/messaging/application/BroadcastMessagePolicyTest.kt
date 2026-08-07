package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BroadcastMessagePolicyTest {
    @Test
    fun `thread input is trimmed and accepted`() {
        val result = BroadcastMessagePolicy.normalizeThread("  Practice updates  ", "  thread-key-123  ")
        assertEquals("Practice updates", result.title)
        assertEquals("thread-key-123", result.idempotencyKey)
    }

    @Test
    fun `message body requires content`() {
        assertFailsWith<ValidationException> { BroadcastMessagePolicy.normalizeMessage("   ", "message-key-123") }
    }

    @Test
    fun `idempotency key is bounded`() {
        assertFailsWith<ValidationException> {
            BroadcastMessagePolicy.normalizeThread("Updates", "short")
        }
    }
}
