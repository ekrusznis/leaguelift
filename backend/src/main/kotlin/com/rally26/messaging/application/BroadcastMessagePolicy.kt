package com.rally26.messaging.application

import com.rally26.common.error.ValidationException

object BroadcastMessagePolicy {
    fun normalizeThread(
        title: String,
        idempotencyKey: String,
    ): ThreadInput {
        val normalizedTitle = title.trim()
        val normalizedKey = idempotencyKey.trim()
        if (normalizedTitle.length !in 3..180) throw ValidationException("Message thread title must be between 3 and 180 characters.")
        if (normalizedKey.length !in 8..160) throw ValidationException("Idempotency key must be between 8 and 160 characters.")
        return ThreadInput(normalizedTitle, normalizedKey)
    }

    fun normalizeMessage(
        body: String,
        idempotencyKey: String,
    ): MessageInput {
        val normalizedBody = body.trim()
        val normalizedKey = idempotencyKey.trim()
        if (normalizedBody.length !in 1..5000) throw ValidationException("Message body must be between 1 and 5,000 characters.")
        if (normalizedKey.length !in 8..160) throw ValidationException("Idempotency key must be between 8 and 160 characters.")
        return MessageInput(normalizedBody, normalizedKey)
    }

    data class ThreadInput(
        val title: String,
        val idempotencyKey: String,
    )

    data class MessageInput(
        val body: String,
        val idempotencyKey: String,
    )
}
