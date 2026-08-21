package com.rally26.identityintegrity.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateResolutionStrategy
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.identityintegrity.domain.IdentityResolutionOperationType
import com.rally26.identityintegrity.domain.IdentityResolutionOutcome
import com.rally26.identityintegrity.domain.IdentityResolutionReceipt
import com.rally26.identityintegrity.persistence.IdentityResolutionRepository
import com.rally26.platformadmin.application.PlatformAdminConsoleService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class DuplicateIdentityResolutionService(
    private val authorizationService: AuthorizationService,
    private val duplicateIdentityService: DuplicateIdentityService,
    private val repository: IdentityResolutionRepository,
    private val platformAdminConsoleService: PlatformAdminConsoleService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun resolve(
        currentUser: CurrentUser,
        sourceRef: IdentityRef,
        targetRef: IdentityRef,
        previewHash: String,
        supportAccessId: UUID,
        reason: String,
        confirmedTargetEmail: String,
    ): IdentityResolutionReceipt {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_USER_MANAGE)
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_SUPPORT_ACCESS)
        if (sourceRef == targetRef) throw ValidationException("Source and target identities must be different.")
        val normalizedReason = reason.trim()
        if (normalizedReason.length !in
            10..500
        ) {
            throw ValidationException("Identity resolution requires a reason between 10 and 500 characters.")
        }
        if (!PREVIEW_HASH.matches(previewHash)) throw ValidationException("A valid preview hash is required.")
        val normalizedPreviewHash = previewHash.lowercase()

        listOf(sourceRef, targetRef)
            .distinct()
            .sortedBy { "${it.kind.name}:${it.id}" }
            .forEach(repository::lock)
        repository.lockAssociations(sourceRef)
        repository.lockAssociations(targetRef)

        repository.findCompletedBySource(sourceRef)?.let { existing ->
            if (existing.target != targetRef || existing.previewHash != normalizedPreviewHash) {
                throw ConflictException(
                    "IDENTITY_ALREADY_RESOLVED",
                    "The source identity was already resolved to a different target or preview.",
                )
            }
            return existing.toReceipt(objectMapper)
        }

        val preview = duplicateIdentityService.buildPreview(sourceRef, targetRef)
        if (preview.previewHash != normalizedPreviewHash) {
            throw ConflictException(
                "DUPLICATE_PREVIEW_STALE",
                "The duplicate preview changed. Review the refreshed plan before resolving it.",
            )
        }
        if (!preview.canProceedToMutationSlice) {
            throw ConflictException(
                "DUPLICATE_RESOLUTION_BLOCKED",
                "The current duplicate preview contains blockers and cannot be executed.",
            )
        }
        val organizationId =
            preview.requiredSupportOrganizationId
                ?: throw ConflictException("SUPPORT_SCOPE_REQUIRED", "This resolution does not have one safe organization support scope.")
        repository.lockSupportAccess(supportAccessId)
        platformAdminConsoleService.requireActiveSupportAccess(currentUser, supportAccessId, organizationId)

        val targetEmail =
            preview.target.email
                ?.trim()
                ?.lowercase()
                ?: throw ConflictException("TARGET_EMAIL_REQUIRED", "The surviving app user must have an email address.")
        if (confirmedTargetEmail.trim().lowercase() != targetEmail) {
            throw ValidationException("Type the surviving target account email exactly to confirm the resolution.")
        }

        val now = Instant.now(clock)
        val operationId = UUID.randomUUID()
        val result =
            when (preview.strategy) {
                DuplicateResolutionStrategy.LINK_SHELL_TO_EXISTING_USER ->
                    resolveShellLink(
                        preview.source.ref,
                        preview.target.ref,
                        preview.source.organizationId,
                        preview.source.householdId,
                        now,
                    )
                DuplicateResolutionStrategy.MERGE_USER_ACCOUNTS ->
                    resolveUserMerge(
                        preview.source.ref,
                        preview.target.ref,
                        preview.source.status,
                        now,
                    )
                else -> throw ConflictException(
                    "DUPLICATE_RESOLUTION_BLOCKED",
                    "This preview strategy is review-only and cannot mutate records.",
                )
            }

        val operationType =
            when (preview.strategy) {
                DuplicateResolutionStrategy.LINK_SHELL_TO_EXISTING_USER -> IdentityResolutionOperationType.LINK_GUARDIAN_SHELL
                DuplicateResolutionStrategy.MERGE_USER_ACCOUNTS -> IdentityResolutionOperationType.MERGE_APP_USERS
                else -> error("unreachable")
            }
        repository.insertCompleted(
            id = operationId,
            operationType = operationType,
            source = sourceRef,
            target = targetRef,
            organizationId = organizationId,
            platformAdminUserId = currentUser.userId,
            supportAccessId = supportAccessId,
            reason = normalizedReason,
            previewHash = preview.previewHash,
            outcomeJson = objectMapper.writeValueAsString(result.outcome),
            recoveryJson = objectMapper.writeValueAsString(result.recovery),
            now = now,
        )
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action =
                if (operationType ==
                    IdentityResolutionOperationType.LINK_GUARDIAN_SHELL
                ) {
                    "platform.identity.guardian_shell.linked"
                } else {
                    "platform.identity.user.merged"
                },
            entityType = "IDENTITY_RESOLUTION",
            entityId = operationId,
            metadataJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "operationType" to operationType.name,
                        "sourceKind" to sourceRef.kind.name,
                        "sourceId" to sourceRef.id.toString(),
                        "targetKind" to targetRef.kind.name,
                        "targetId" to targetRef.id.toString(),
                        "supportAccessId" to supportAccessId.toString(),
                        "previewHash" to preview.previewHash,
                    ),
                ),
            householdId = preview.source.householdId,
            targetUserId = targetRef.id.takeIf { targetRef.kind == DuplicateIdentityKind.APP_USER },
            summary =
                if (operationType ==
                    IdentityResolutionOperationType.LINK_GUARDIAN_SHELL
                ) {
                    "Guardian profile linked to existing user"
                } else {
                    "Duplicate user account merged"
                },
            correlationId = operationId,
        )
        return IdentityResolutionReceipt(
            operationId = operationId,
            operationType = operationType,
            status = com.rally26.identityintegrity.domain.IdentityResolutionOperationStatus.COMPLETED,
            source = sourceRef,
            target = targetRef,
            organizationId = organizationId,
            supportAccessId = supportAccessId,
            previewHash = preview.previewHash,
            completedAt = now,
            outcome = result.outcome,
        )
    }

    private fun resolveShellLink(
        source: IdentityRef,
        target: IdentityRef,
        organizationId: UUID?,
        householdId: UUID?,
        now: Instant,
    ): MutationResult {
        require(source.kind == DuplicateIdentityKind.GUARDIAN_SHELL && target.kind == DuplicateIdentityKind.APP_USER)
        val orgId = organizationId ?: throw ConflictException("SHELL_ORGANIZATION_MISSING", "Guardian shell organization is missing.")
        val homeId = householdId ?: throw ConflictException("SHELL_HOUSEHOLD_MISSING", "Guardian shell household is missing.")
        repository.activeGuardianUserForAdult(source.id)?.let {
            throw ConflictException("SHELL_ALREADY_LINKED", "The guardian profile was linked by another operation. Refresh the preview.")
        }
        val relationshipId = repository.createGuardianRelationship(orgId, homeId, source.id, target.id, now)
        return MutationResult(
            outcome = IdentityResolutionOutcome(guardianRelationshipCreated = true),
            recovery =
                mapOf(
                    "createdGuardianRelationshipId" to relationshipId.toString(),
                    "recoveryRule" to "Revoke the created guardian relationship; do not delete household_adult.",
                ),
        )
    }

    private fun resolveUserMerge(
        source: IdentityRef,
        target: IdentityRef,
        sourceStatus: String,
        now: Instant,
    ): MutationResult {
        require(source.kind == DuplicateIdentityKind.APP_USER && target.kind == DuplicateIdentityKind.APP_USER)
        var membershipsMoved = 0
        var membershipsDeduplicated = 0
        var rolesMoved = 0
        var rolesDeduplicated = 0
        var guardiansMoved = 0
        var guardiansDeduplicated = 0
        var messageThreadMembershipsMoved = 0
        var messageThreadMembershipsDeduplicated = 0
        var messageRecipientAccessMoved = 0
        var messageRecipientAccessAlreadyPresent = 0
        var announcementRecipientAccessMoved = 0
        var announcementRecipientAccessAlreadyPresent = 0
        val recovery =
            mutableMapOf<String, Any>(
                "sourceStatusBefore" to sourceStatus,
                "movedMembershipIds" to mutableListOf<String>(),
                "revokedMembershipIds" to mutableListOf<String>(),
                "movedRoleAssignmentIds" to mutableListOf<String>(),
                "revokedRoleAssignmentIds" to mutableListOf<String>(),
                "movedGuardianRelationshipIds" to mutableListOf<String>(),
                "revokedGuardianRelationshipIds" to mutableListOf<String>(),
                "movedMessageThreadMembershipIds" to mutableListOf<String>(),
                "closedMessageThreadMembershipIds" to mutableListOf<String>(),
                "movedMessageRecipientIds" to mutableListOf<String>(),
                "preservedMessageRecipientIdsWithTargetAccess" to mutableListOf<String>(),
                "movedAnnouncementRecipientIds" to mutableListOf<String>(),
                "preservedAnnouncementRecipientIdsWithTargetAccess" to mutableListOf<String>(),
                "authTokensReactivatedOnRecovery" to false,
            )

        val targetMemberships = repository.memberships(target.id).associateBy { it.organizationId }
        repository.memberships(source.id).filter { it.status != "REVOKED" }.forEach { row ->
            val targetRow = targetMemberships[row.organizationId]
            when {
                targetRow == null -> {
                    if (repository.moveMembership(row.id, target.id, now) != 1) conflictDuringMutation()
                    membershipsMoved++
                    recovery.list("movedMembershipIds").add(row.id.toString())
                }
                targetRow.role == row.role && targetRow.status == row.status -> {
                    if (repository.revokeMembership(row.id, now) != 1) conflictDuringMutation()
                    membershipsDeduplicated++
                    recovery.list("revokedMembershipIds").add(row.id.toString())
                }
                else -> conflictDuringMutation()
            }
        }

        val targetRoles = repository.activeRoleAssignments(target.id).groupBy { Triple(it.organizationId, it.contextType, it.resourceId) }
        repository.activeRoleAssignments(source.id).forEach { row ->
            val atTarget = targetRoles[Triple(row.organizationId, row.contextType, row.resourceId)].orEmpty()
            when {
                atTarget.any { it.role == row.role } -> {
                    if (repository.revokeRoleAssignment(row.id, now) != 1) conflictDuringMutation()
                    rolesDeduplicated++
                    recovery.list("revokedRoleAssignmentIds").add(row.id.toString())
                }
                atTarget.isEmpty() -> {
                    if (repository.moveRoleAssignment(row.id, target.id, now) != 1) conflictDuringMutation()
                    rolesMoved++
                    recovery.list("movedRoleAssignmentIds").add(row.id.toString())
                }
                else -> conflictDuringMutation()
            }
        }

        val targetGuardianAdults = repository.activeGuardianRelationships(target.id).map { it.householdAdultId }.toSet()
        repository.activeGuardianRelationships(source.id).forEach { row ->
            if (row.householdAdultId in targetGuardianAdults) {
                if (repository.revokeGuardianRelationship(row.id, now) != 1) conflictDuringMutation()
                guardiansDeduplicated++
                recovery.list("revokedGuardianRelationshipIds").add(row.id.toString())
            } else {
                if (repository.moveGuardianRelationship(row.id, target.id, now) != 1) conflictDuringMutation()
                guardiansMoved++
                recovery.list("movedGuardianRelationshipIds").add(row.id.toString())
            }
        }

        val targetThreadMemberships = repository.activeMessageThreadMemberships(target.id).associateBy { it.threadId }
        repository.activeMessageThreadMemberships(source.id).forEach { row ->
            val targetRow = targetThreadMemberships[row.threadId]
            when {
                targetRow == null -> {
                    if (repository.moveMessageThreadMembership(row.id, target.id) != 1) conflictDuringMutation()
                    messageThreadMembershipsMoved++
                    recovery.list("movedMessageThreadMembershipIds").add(row.id.toString())
                }
                row.sameAccessAs(targetRow) -> {
                    if (repository.closeMessageThreadMembership(row.id, now) != 1) conflictDuringMutation()
                    messageThreadMembershipsDeduplicated++
                    recovery.list("closedMessageThreadMembershipIds").add(row.id.toString())
                }
                else -> conflictDuringMutation()
            }
        }

        val targetMessageIds = repository.visibleMessageRecipients(target.id).mapTo(mutableSetOf()) { it.parentId }
        repository.visibleMessageRecipients(source.id).forEach { row ->
            if (row.parentId in targetMessageIds) {
                messageRecipientAccessAlreadyPresent++
                recovery.list("preservedMessageRecipientIdsWithTargetAccess").add(row.id.toString())
            } else {
                if (repository.moveVisibleMessageRecipient(row.id, target.id, now) != 1) conflictDuringMutation()
                targetMessageIds += row.parentId
                messageRecipientAccessMoved++
                recovery.list("movedMessageRecipientIds").add(row.id.toString())
            }
        }

        val targetAnnouncementIds = repository.visibleAnnouncementRecipients(target.id).mapTo(mutableSetOf()) { it.parentId }
        repository.visibleAnnouncementRecipients(source.id).forEach { row ->
            if (row.parentId in targetAnnouncementIds) {
                announcementRecipientAccessAlreadyPresent++
                recovery.list("preservedAnnouncementRecipientIdsWithTargetAccess").add(row.id.toString())
            } else {
                if (repository.moveVisibleAnnouncementRecipient(row.id, target.id, now) != 1) conflictDuringMutation()
                targetAnnouncementIds += row.parentId
                announcementRecipientAccessMoved++
                recovery.list("movedAnnouncementRecipientIds").add(row.id.toString())
            }
        }

        val invalidatedTokens = repository.invalidateAuthenticationTokens(source.id, now)
        if (repository.retireSourceUser(source.id, target.id, now) != 1) conflictDuringMutation()
        recovery["retiredSourceUserId"] = source.id.toString()
        recovery["survivingTargetUserId"] = target.id.toString()

        return MutationResult(
            outcome =
                IdentityResolutionOutcome(
                    membershipsMoved = membershipsMoved,
                    membershipsDeduplicated = membershipsDeduplicated,
                    roleAssignmentsMoved = rolesMoved,
                    roleAssignmentsDeduplicated = rolesDeduplicated,
                    guardianRelationshipsMoved = guardiansMoved,
                    guardianRelationshipsDeduplicated = guardiansDeduplicated,
                    messageThreadMembershipsMoved = messageThreadMembershipsMoved,
                    messageThreadMembershipsDeduplicated = messageThreadMembershipsDeduplicated,
                    messageRecipientAccessMoved = messageRecipientAccessMoved,
                    messageRecipientAccessAlreadyPresent = messageRecipientAccessAlreadyPresent,
                    announcementRecipientAccessMoved = announcementRecipientAccessMoved,
                    announcementRecipientAccessAlreadyPresent = announcementRecipientAccessAlreadyPresent,
                    authTokensInvalidated = invalidatedTokens,
                ),
            recovery = recovery,
        )
    }

    private fun conflictDuringMutation(): Nothing =
        throw ConflictException(
            "DUPLICATE_RESOLUTION_CHANGED",
            "Identity relationships changed during resolution. The transaction was rolled back; refresh the preview.",
        )

    @Suppress("UNCHECKED_CAST")
    private fun MutableMap<String, Any>.list(key: String): MutableList<String> = this[key] as MutableList<String>

    private data class MutationResult(
        val outcome: IdentityResolutionOutcome,
        val recovery: Map<String, Any>,
    )

    companion object {
        private val PREVIEW_HASH = Regex("^[0-9a-fA-F]{64}$")
    }
}

private fun com.rally26.identityintegrity.persistence.ResolutionRecord.toReceipt(objectMapper: ObjectMapper): IdentityResolutionReceipt =
    IdentityResolutionReceipt(
        operationId = id,
        operationType = operationType,
        status = status,
        source = source,
        target = target,
        organizationId = organizationId,
        supportAccessId = supportAccessId,
        previewHash = previewHash,
        completedAt = completedAt,
        outcome = objectMapper.readValue(outcomeJson, IdentityResolutionOutcome::class.java),
    )
