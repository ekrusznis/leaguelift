package com.rally26.fundraising.domain

import java.time.Instant
import java.util.UUID

/**
 * Organization-level fundraising policy controlled by the organization owner.
 *
 * The absence of a persisted row intentionally resolves to DEFAULT so existing
 * organizations fail closed: a non-owner must obtain owner approval before a
 * fundraiser can become ACTIVE until the owner explicitly opts out.
 */
data class FundraisingSettings(
    val organizationId: UUID,
    val requireOwnerApproval: Boolean,
    val updatedByUserId: UUID?,
    val updatedAt: Instant?,
) {
    companion object {
        fun defaultFor(organizationId: UUID) =
            FundraisingSettings(
                organizationId = organizationId,
                requireOwnerApproval = true,
                updatedByUserId = null,
                updatedAt = null,
            )
    }
}
