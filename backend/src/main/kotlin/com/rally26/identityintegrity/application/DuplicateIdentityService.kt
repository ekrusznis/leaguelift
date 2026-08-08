package com.rally26.identityintegrity.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.identityintegrity.domain.DuplicateCandidateGroup
import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateMergePreview
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.identityintegrity.persistence.DuplicateIdentityRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DuplicateIdentityService(
    private val authorizationService: AuthorizationService,
    private val repository: DuplicateIdentityRepository,
) {
    fun candidates(
        currentUser: CurrentUser,
        query: String?,
        size: Int,
    ): List<DuplicateCandidateGroup> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_USER_VIEW)
        require(size in 1..100) { "Candidate page size must be between 1 and 100." }
        require((query?.length ?: 0) <= 200) { "Candidate search is too long." }
        return repository.findCandidates(query, size)
    }

    fun preview(
        currentUser: CurrentUser,
        sourceKind: DuplicateIdentityKind,
        sourceId: UUID,
        targetKind: DuplicateIdentityKind,
        targetId: UUID,
    ): DuplicateMergePreview {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_USER_VIEW)
        return buildPreview(IdentityRef(sourceKind, sourceId), IdentityRef(targetKind, targetId))
    }

    internal fun buildPreview(
        sourceRef: IdentityRef,
        targetRef: IdentityRef,
    ): DuplicateMergePreview {
        require(sourceRef != targetRef) { "Source and target identities must be different." }
        val source =
            repository.findIdentity(sourceRef)
                ?: throw NotFoundException("DUPLICATE_IDENTITY_NOT_FOUND", "Source identity not found.")
        val target =
            repository.findIdentity(targetRef)
                ?: throw NotFoundException("DUPLICATE_IDENTITY_NOT_FOUND", "Target identity not found.")
        return DuplicateMergePlanner.plan(
            source = source,
            target = target,
            dependencies = repository.dependencyInventory(sourceRef),
            sharedEvidence = repository.sharedMatchEvidence(sourceRef, targetRef),
        )
    }
}
