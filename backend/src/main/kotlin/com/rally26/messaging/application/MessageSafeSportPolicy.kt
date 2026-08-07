@file:Suppress("ktlint:standard:filename")

package com.rally26.messaging.application

import com.rally26.common.error.ConflictException
import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.MessageSafeSportReviewStatus

object MessageSafeSportPolicyRules {
    fun normalizeReviewReference(
        status: MessageSafeSportReviewStatus,
        reference: String?,
    ): String? {
        if (status == MessageSafeSportReviewStatus.PENDING) return null
        val normalized = reference?.trim().orEmpty()
        if (normalized.length !in
            3..500
        ) {
            throw ValidationException("A recorded SafeSport/compliance review requires a 3-500 character reference.")
        }
        return normalized
    }

    fun requireEnablementAllowed(
        status: MessageSafeSportReviewStatus,
        enabled: Boolean,
        reference: String?,
    ) {
        if (!enabled) return
        if (status != MessageSafeSportReviewStatus.APPROVED || reference.isNullOrBlank()) {
            throw ConflictException(
                "ATHLETE_MESSAGING_REVIEW_REQUIRED",
                "Athlete-created messaging cannot be enabled until an approved SafeSport/compliance review is recorded.",
            )
        }
    }

    fun normalizeNote(
        note: String?,
        max: Int = 1000,
    ): String? =
        note?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > max) throw ValidationException("The safety note is too long.")
        }
}
