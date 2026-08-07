package com.rally26.messaging.application

import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThreadMember
import java.util.UUID

object MessageMembershipReconciliationPolicy {
    fun shouldRemainActive(
        member: MessageThreadMember,
        activeAthleteUserIds: Set<UUID>,
        activeTargetedGuardianUserIds: Set<UUID>,
        currentGuardianObserverUserIds: Set<UUID>,
    ): Boolean =
        when (member.memberType) {
            MessageRecipientType.STAFF -> true // send-time authorization is authoritative for staff.
            MessageRecipientType.ATHLETE -> member.userId in activeAthleteUserIds
            MessageRecipientType.GUARDIAN ->
                when (member.accessReason) {
                    MessageAccessReason.TARGETED -> member.userId in activeTargetedGuardianUserIds
                    MessageAccessReason.GUARDIAN_VISIBILITY -> member.userId in currentGuardianObserverUserIds
                }
        }
}
