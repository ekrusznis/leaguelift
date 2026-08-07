package com.rally26.messaging.application

import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientCandidate

object BroadcastRecipientPolicy {
    fun merge(candidates: List<MessageRecipientCandidate>): Map<String, MessageRecipientCandidate> {
        val merged = linkedMapOf<String, MessageRecipientCandidate>()
        for (candidate in candidates) {
            val key = keyFor(candidate) ?: continue
            val prior = merged[key]
            if (prior == null) {
                merged[key] = candidate
                continue
            }
            val targeted = if (prior.accessReason == MessageAccessReason.TARGETED) prior else candidate
            val other = if (targeted === prior) candidate else prior
            merged[key] =
                targeted.copy(
                    userId = targeted.userId ?: other.userId,
                    householdId = targeted.householdId ?: other.householdId,
                    email = targeted.email ?: other.email,
                    phone = targeted.phone ?: other.phone,
                    accessReason =
                        if (prior.accessReason == MessageAccessReason.TARGETED || candidate.accessReason == MessageAccessReason.TARGETED) {
                            MessageAccessReason.TARGETED
                        } else {
                            MessageAccessReason.GUARDIAN_VISIBILITY
                        },
                )
        }
        return merged
    }

    fun keyFor(candidate: MessageRecipientCandidate): String? =
        when {
            candidate.userId != null -> "user:${candidate.userId}"
            !candidate.email.isNullOrBlank() -> "email:${candidate.email.trim().lowercase()}"
            !candidate.phone.isNullOrBlank() -> "phone:${candidate.phone.trim()}"
            else -> null
        }
}
