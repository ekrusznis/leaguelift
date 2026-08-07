package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.GuardianObserverLink
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThreadMemberCandidate
import java.util.UUID

object AthleteConversationPolicy {
    fun requireTargetIds(
        targetUserIds: Collection<UUID>,
        senderUserId: UUID,
    ): Set<UUID> {
        val targets = targetUserIds.toSet()
        if (targets.isEmpty()) throw ValidationException("Choose at least one teammate for the conversation.")
        if (targets.size > 20) throw ValidationException("Athlete group conversations can include at most 20 selected teammates.")
        if (senderUserId in targets) throw ValidationException("You cannot select yourself as a conversation recipient.")
        return targets
    }

    fun merge(
        creator: MessageThreadMemberCandidate,
        targetedAthletes: List<MessageThreadMemberCandidate>,
        guardianObservers: List<GuardianObserverLink>,
    ): List<MessageThreadMemberCandidate> {
        require(creator.memberType == MessageRecipientType.ATHLETE)
        require(targetedAthletes.all { it.memberType == MessageRecipientType.ATHLETE })
        val athleteIds = (targetedAthletes.map { it.userId } + creator.userId).toSet()
        val candidates =
            buildList {
                add(creator.copy(accessReason = MessageAccessReason.TARGETED, canReply = true))
                addAll(targetedAthletes.map { it.copy(accessReason = MessageAccessReason.TARGETED, canReply = true) })
                addAll(guardianObservers.filter { it.athleteUserId in athleteIds }.map { it.member.copy(canReply = false) })
            }
        val merged = linkedMapOf<UUID, MessageThreadMemberCandidate>()
        for (candidate in candidates) {
            val prior = merged[candidate.userId]
            if (prior == null) {
                merged[candidate.userId] = candidate
            } else {
                val targeted = prior.accessReason == MessageAccessReason.TARGETED || candidate.accessReason == MessageAccessReason.TARGETED
                val preferred = if (prior.accessReason == MessageAccessReason.TARGETED) prior else candidate
                val other = if (preferred === prior) candidate else prior
                merged[candidate.userId] =
                    preferred.copy(
                        householdId = preferred.householdId ?: other.householdId,
                        participantId = preferred.participantId ?: other.participantId,
                        accessReason = if (targeted) MessageAccessReason.TARGETED else MessageAccessReason.GUARDIAN_VISIBILITY,
                        canReply = targeted && (prior.canReply || candidate.canReply),
                    )
            }
        }
        return merged.values.toList()
    }
}
