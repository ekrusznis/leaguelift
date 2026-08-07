package com.rally26.messaging.application

import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThread
import com.rally26.messaging.persistence.MessageRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class MessageMembershipReconciliationService(
    private val repository: MessageRepository,
    private val clock: Clock,
) {
    @Scheduled(cron = "\${rally26.messaging.membership-reconciliation-cron:0 20 * * * *}")
    @Transactional
    fun reconcileOpenThreads() {
        repository.listOpenConversationThreads().forEach { reconcileThread(it) }
    }

    @Transactional
    fun reconcileThread(thread: MessageThread) {
        val activeMembers = repository.listActiveThreadMembers(thread.id)
        val activeAthletes = repository.activeAthleteUserIdsForTeam(thread.organizationId, thread.scopeId)
        val activeGuardians = repository.activeGuardianUserIdsForTeam(thread.organizationId, thread.scopeId)
        val observerLinks = repository.listGuardianObserverLinks(thread.organizationId, thread.scopeId)
        val athleteMembers =
            activeMembers
                .filter {
                    it.memberType == MessageRecipientType.ATHLETE && it.userId in activeAthletes
                }.map { it.userId }
                .toSet()
        val desiredObservers = observerLinks.filter { it.athleteUserId in athleteMembers }.map { it.member }
        val desiredObserverIds = desiredObservers.map { it.userId }.toSet()
        val now = Instant.now(clock)

        for (member in activeMembers) {
            val keep = MessageMembershipReconciliationPolicy.shouldRemainActive(member, activeAthletes, activeGuardians, desiredObserverIds)
            if (!keep) repository.markThreadMemberLeft(thread.id, member.userId, now)
        }

        val stillActiveIds = repository.listActiveThreadMembers(thread.id).map { it.userId }.toSet()
        desiredObservers.filterNot { it.userId in stillActiveIds }.forEach { observer ->
            repository.insertThreadMember(
                thread.organizationId,
                thread.id,
                observer.copy(accessReason = MessageAccessReason.GUARDIAN_VISIBILITY, canReply = false),
                now,
            )
        }
    }
}
