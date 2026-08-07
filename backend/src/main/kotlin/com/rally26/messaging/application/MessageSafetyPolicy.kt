package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.MessageSafetyReportStatus

object MessageSafetyPolicy {
    fun normalizeDetails(details: String?): String? {
        val normalized = details?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && normalized.length > 2000) {
            throw ValidationException("Report details must be 2,000 characters or fewer.")
        }
        return normalized
    }

    fun normalizeLockReason(reason: String): String {
        val normalized = reason.trim()
        if (normalized.length !in 5..1000) {
            throw ValidationException("Safety lock reason must be between 5 and 1,000 characters.")
        }
        return normalized
    }

    fun normalizeReview(
        currentStatus: MessageSafetyReportStatus,
        status: MessageSafetyReportStatus,
        note: String?,
    ): String? {
        if (status == MessageSafetyReportStatus.OPEN) {
            throw ValidationException("A moderator cannot move a report back to OPEN.")
        }
        if (currentStatus in setOf(MessageSafetyReportStatus.RESOLVED, MessageSafetyReportStatus.DISMISSED) && status != currentStatus) {
            throw ValidationException("A closed message safety report cannot be reopened or changed to another terminal state.")
        }
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && normalized.length > 2000) {
            throw ValidationException("Moderation notes must be 2,000 characters or fewer.")
        }
        if (status in setOf(MessageSafetyReportStatus.RESOLVED, MessageSafetyReportStatus.DISMISSED) && normalized == null) {
            throw ValidationException("A resolution note is required when resolving or dismissing a report.")
        }
        return normalized
    }

    fun normalizeUnlockNote(note: String): String {
        val normalized = note.trim()
        if (normalized.length !in 3..2000) {
            throw ValidationException("An unlock note between 3 and 2,000 characters is required.")
        }
        return normalized
    }
}
