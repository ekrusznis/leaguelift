package com.rally26.identityintegrity.domain

import java.time.Instant
import java.util.UUID

enum class IdentityResolutionOperationType { LINK_GUARDIAN_SHELL, MERGE_APP_USERS }

enum class IdentityResolutionOperationStatus { COMPLETED, ROLLED_BACK }

data class IdentityResolutionOutcome(
    val membershipsMoved: Int = 0,
    val membershipsDeduplicated: Int = 0,
    val roleAssignmentsMoved: Int = 0,
    val roleAssignmentsDeduplicated: Int = 0,
    val guardianRelationshipsMoved: Int = 0,
    val guardianRelationshipsDeduplicated: Int = 0,
    val guardianRelationshipCreated: Boolean = false,
    val authTokensInvalidated: Int = 0,
)

data class IdentityResolutionReceipt(
    val operationId: UUID,
    val operationType: IdentityResolutionOperationType,
    val status: IdentityResolutionOperationStatus,
    val source: IdentityRef,
    val target: IdentityRef,
    val organizationId: UUID,
    val supportAccessId: UUID,
    val previewHash: String,
    val completedAt: Instant,
    val outcome: IdentityResolutionOutcome,
)
