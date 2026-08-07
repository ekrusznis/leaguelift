package com.rally26.messaging.application

import com.rally26.common.error.ValidationException
import com.rally26.messaging.domain.GuardianObserverLink
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThreadMemberCandidate
import java.util.UUID

object ConversationMemberPolicy {
    fun requireTargetIds(
        targetUserIds: Collection<UUID>,
        senderUserId: UUID,
    ): Set<UUID> {
        val normalized = targetUserIds.toSet()
        if (normalized.isEmpty()) throw ValidationException("Choose at least one family recipient for the conversation.")
        if (normalized.size > 250) throw ValidationException("A conversation can include at most 250 selected family recipients.")
        if (senderUserId in normalized) throw ValidationException("The conversation creator cannot also be selected as a family recipient.")
        return normalized
    }

    fun merge(
        creator: MessageThreadMemberCandidate,
        targeted: List<MessageThreadMemberCandidate>,
        guardianObservers: List<GuardianObserverLink>,
    ): List<MessageThreadMemberCandidate> {
        require(creator.memberType == MessageRecipientType.STAFF)
        val selectedAthleteIds = targeted.filter { it.memberType == MessageRecipientType.ATHLETE }.map { it.userId }.toSet()
        val candidates =
            buildList {
                add(creator.copy(accessReason = MessageAccessReason.TARGETED, canReply = true))
                addAll(targeted.map { it.copy(accessReason = MessageAccessReason.TARGETED, canReply = true) })
                addAll(guardianObservers.filter { it.athleteUserId in selectedAthleteIds }.map { it.member })
            }
        val merged = linkedMapOf<UUID, MessageThreadMemberCandidate>()
        for (candidate in candidates) {
            val prior = merged[candidate.userId]
            if (prior == null) {
                merged[candidate.userId] = candidate
                continue
            }
            val targetedWins = prior.accessReason == MessageAccessReason.TARGETED || candidate.accessReason == MessageAccessReason.TARGETED
            val preferred = if (prior.accessReason == MessageAccessReason.TARGETED) prior else candidate
            val other = if (preferred === prior) candidate else prior
            merged[candidate.userId] =
                preferred.copy(
                    householdId = preferred.householdId ?: other.householdId,
                    participantId = preferred.participantId ?: other.participantId,
                    accessReason = if (targetedWins) MessageAccessReason.TARGETED else MessageAccessReason.GUARDIAN_VISIBILITY,
                    canReply = if (targetedWins) (prior.canReply || candidate.canReply) else false,
                )
        }
        return merged.values.toList()
    }
}
