package com.rally26.credit.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.credit.domain.CreditExpirationPolicy
import com.rally26.credit.domain.CreditSourceType
import com.rally26.credit.domain.CreditTransferStatus
import com.rally26.credit.domain.FamilyCreditBalance
import com.rally26.credit.domain.FamilyCreditGrant
import com.rally26.credit.domain.FamilyCreditGrantStatus
import com.rally26.credit.domain.FamilyCreditTransfer
import com.rally26.credit.domain.OrganizationCreditSettings
import com.rally26.credit.persistence.FamilyCreditApplicationRepository
import com.rally26.credit.persistence.FamilyCreditGrantRepository
import com.rally26.credit.persistence.FamilyCreditTransferRepository
import com.rally26.credit.persistence.OrganizationCreditSettingsRepository
import com.rally26.fee.application.FeeService
import com.rally26.household.persistence.HouseholdRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Real family credit ledger (Phase 23, DESIGN-DOC.md section 13/14.1, resolving
 * section 19.3 #16/#17) — replaces `ParentDashboardService.getFamilyCredits()`'s
 * previously 100% hardcoded demo data. Grants are real rows (`family_credit_grant`),
 * consumed FIFO oldest-expiring-first, with a full audit trail via
 * `family_credit_application`/`family_credit_transfer` junction/history tables.
 */
@Service
class FamilyCreditService(
    private val grantRepository: FamilyCreditGrantRepository,
    private val applicationRepository: FamilyCreditApplicationRepository,
    private val transferRepository: FamilyCreditTransferRepository,
    private val settingsRepository: OrganizationCreditSettingsRepository,
    private val householdRepository: HouseholdRepository,
    private val feeService: FeeService,
    private val authorizationService: AuthorizationService,
    private val membershipService: com.rally26.membership.application.MembershipService,
    private val auditService: AuditService,
) {
    fun getSettings(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationCreditSettings {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return settingsRepository.getOrCreateDefault(organizationId)
    }

    @Transactional
    fun updateSettings(
        organizationId: UUID,
        defaultCreditPercent: Int,
        expirationPolicy: CreditExpirationPolicy,
        expirationMonths: Int?,
        p2pTransferEnabled: Boolean,
        currentUser: CurrentUser,
    ): OrganizationCreditSettings {
        membershipService.requireManagerRole(organizationId, currentUser)
        if (expirationPolicy == CreditExpirationPolicy.EXPIRES && (expirationMonths == null || expirationMonths <= 0)) {
            throw ValidationException("An expiration window in months is required when the expiration policy is EXPIRES.")
        }
        val resolvedMonths = if (expirationPolicy == CreditExpirationPolicy.EXPIRES) expirationMonths else null
        val settings = settingsRepository.upsert(organizationId, defaultCreditPercent, expirationPolicy, resolvedMonths, p2pTransferEnabled)
        auditService.record(currentUser.userId, organizationId, "organization_credit_settings.updated", "organization", organizationId)
        return settings
    }

    /** Called from `ContributionService.confirmFromWebhook` only when the contribution has a real `attributedHouseholdId`. */
    @Transactional
    fun grantForContribution(
        organizationId: UUID,
        householdId: UUID,
        contributionId: UUID,
        contributionAmountMinor: Long,
        currency: String,
    ): FamilyCreditGrant =
        createGrant(organizationId, householdId, CreditSourceType.CAMPAIGN_ATTRIBUTION, contributionId, contributionAmountMinor, currency)

    /** Called from `OrderService.confirmFromWebhook` only when the order has a real `attributedHouseholdId` (Phase 24 slice 24.3 — set only for orders placed through a published athlete storefront). */
    @Transactional
    fun grantForStorefrontOrder(
        organizationId: UUID,
        householdId: UUID,
        orderId: UUID,
        orderAmountMinor: Long,
        currency: String,
    ): FamilyCreditGrant =
        createGrant(organizationId, householdId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId, orderAmountMinor, currency)

    private fun createGrant(
        organizationId: UUID,
        householdId: UUID,
        sourceType: CreditSourceType,
        sourceId: UUID,
        saleAmountMinor: Long,
        currency: String,
    ): FamilyCreditGrant {
        val settings = settingsRepository.getOrCreateDefault(organizationId)
        val amountMinor = (saleAmountMinor * settings.defaultCreditPercent) / 10_000
        if (amountMinor <= 0) {
            // A real but zero-value grant would just be noise — nothing to record.
            error("Computed credit amount for $sourceType $sourceId was zero or negative; caller should not have invoked this")
        }
        val expiresAt =
            if (settings.expirationPolicy == CreditExpirationPolicy.EXPIRES) {
                Instant.now().plus(Duration.ofDays((settings.expirationMonths ?: 12) * 30L))
            } else {
                null
            }
        val grant =
            grantRepository.insert(
                organizationId,
                householdId,
                amountMinor,
                currency,
                FamilyCreditGrantStatus.AVAILABLE,
                sourceType,
                sourceId,
                expiresAt,
            )
        auditService.record(null, organizationId, "family_credit.granted", "family_credit_grant", grant.id)
        return grant
    }

    /** Phase 24 slice 24.3 — used by the guardian dashboard's Recent Orders card to show one order's own credit state/amount, if any. Caller (`ParentDashboardService`) already gates household access; this is a narrow, order-scoped lookup, not a new authorization surface. */
    fun findGrantForOrder(
        organizationId: UUID,
        orderId: UUID,
    ) = grantRepository.findBySource(organizationId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId)

    /** Guardian-or-staff read access — same household-capability check as fee viewing. */
    fun getBalance(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): FamilyCreditBalance {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_CREDIT_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's credits.")
        }
        val available = grantRepository.sumAvailableForHousehold(organizationId, householdId)
        val pending =
            grantRepository.sumPendingForHousehold(organizationId, householdId) +
                transferRepository.sumPendingOutgoingForHousehold(organizationId, householdId)
        val expiringSoon = grantRepository.sumExpiringSoonForHousehold(organizationId, householdId, Instant.now().plus(30, ChronoUnit.DAYS))
        val applied = applicationRepository.sumAppliedForHousehold(organizationId, householdId)
        val currency = grantRepository.listForHousehold(organizationId, householdId).firstOrNull()?.currency ?: "USD"
        val p2pEnabled = settingsRepository.getOrCreateDefault(organizationId).p2pTransferEnabled
        return FamilyCreditBalance(currency, available, pending, expiringSoon, applied, p2pEnabled)
    }

    fun listGrants(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<FamilyCreditGrant> {
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_CREDIT_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's credits.")
        }
        return grantRepository.listForHousehold(organizationId, householdId)
    }

    /** Applies available credit against one of the household's own outstanding fees — FIFO across grants, oldest-expiring-first. */
    @Transactional
    fun applyToFee(
        organizationId: UUID,
        householdId: UUID,
        feeAssignmentId: UUID,
        amountMinor: Long,
        currentUser: CurrentUser,
    ) {
        if (amountMinor <= 0) throw ValidationException("Amount to apply must be greater than zero.")
        val available = grantRepository.listAvailableForHousehold(organizationId, householdId)
        val totalAvailable = available.sumOf { it.remainingMinor }
        if (amountMinor > totalAvailable) {
            throw ValidationException("This household only has $totalAvailable minor units of available credit.")
        }
        // FeeService owns fee_adjustment/authorization for "does this fee belong to this household" — the actual
        // adjustment row is created there, gated on HOUSEHOLD_CREDIT_APPLY, before any grant is consumed here.
        val (adjustment, _) = feeService.applyCreditFromHousehold(organizationId, feeAssignmentId, householdId, amountMinor, currentUser)
        var remaining = amountMinor
        for (grant in available) {
            if (remaining <= 0) break
            val consume = minOf(remaining, grant.remainingMinor)
            grantRepository.decrementRemaining(grant.id, organizationId, consume)
            applicationRepository.insert(organizationId, grant.id, adjustment.id, consume, currentUser.userId)
            remaining -= consume
        }
        auditService.record(currentUser.userId, organizationId, "family_credit.applied", "fee_adjustment", adjustment.id)
    }

    /**
     * Peer-to-peer transfer (Phase 23) — real mechanism, but ships inert everywhere:
     * requires `organization_credit_settings.p2p_transfer_enabled = true`, which
     * defaults false and stays false until an org owner explicitly turns it on for
     * their own organization, per the legal/payments review section 1's amended
     * Credit boundary hard rule still requires. Uses `hasGuardianRelationship` (the
     * exact-guardian, not "any active org staff" check) since this moves real value
     * out of a household — the same caution EventRsvpService/HOUSEHOLD_ORDER_CREATE
     * already apply to impersonation-risk actions.
     *
     * Requesting a transfer only HOLDS the amount (the sender's grants are decremented
     * immediately so it can't be double-spent, surfaced via `FamilyCreditBalance.pendingMinor`)
     * — the receiver's grant is only created once an org manager approves via
     * [approveTransfer]. This mirrors ProfileCorrectionRequest's PENDING/APPROVED/REJECTED
     * review pattern rather than moving real value on a guardian's unreviewed say-so.
     */
    @Transactional
    fun transfer(
        organizationId: UUID,
        fromHouseholdId: UUID,
        toHouseholdId: UUID,
        amountMinor: Long,
        currentUser: CurrentUser,
    ): FamilyCreditTransfer {
        val settings = settingsRepository.getOrCreateDefault(organizationId)
        if (!settings.p2pTransferEnabled) {
            throw ValidationException("Peer-to-peer credit transfer is not enabled for this organization.")
        }
        if (fromHouseholdId == toHouseholdId) throw ValidationException("Cannot transfer credit to the same household.")
        if (amountMinor <= 0) throw ValidationException("Amount to transfer must be greater than zero.")
        if (!authorizationService.hasGuardianRelationship(organizationId, fromHouseholdId, currentUser)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You must be a guardian of this household to transfer its credit.")
        }
        householdRepository.findById(toHouseholdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The recipient household could not be found.")
        val available = grantRepository.listAvailableForHousehold(organizationId, fromHouseholdId)
        val totalAvailable = available.sumOf { it.remainingMinor }
        if (amountMinor > totalAvailable) {
            throw ValidationException("This household only has $totalAvailable minor units of available credit.")
        }
        var remaining = amountMinor
        for (grant in available) {
            if (remaining <= 0) break
            val consume = minOf(remaining, grant.remainingMinor)
            grantRepository.decrementRemaining(grant.id, organizationId, consume)
            remaining -= consume
        }
        val transfer = transferRepository.insert(organizationId, fromHouseholdId, toHouseholdId, amountMinor, currentUser.userId)
        auditService.record(currentUser.userId, organizationId, "family_credit.transfer_requested", "family_credit_transfer", transfer.id)
        return transfer
    }

    fun listPendingTransfers(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<FamilyCreditTransfer> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return transferRepository.listPendingForOrganization(organizationId)
    }

    /** Moves the held amount to the receiver as a new grant. Manager-gated — this is the actual point value changes hands. */
    @Transactional
    fun approveTransfer(
        organizationId: UUID,
        transferId: UUID,
        currentUser: CurrentUser,
    ): FamilyCreditTransfer {
        membershipService.requireManagerRole(organizationId, currentUser)
        val transfer = pendingTransfer(organizationId, transferId)
        val currency = grantRepository.listForHousehold(organizationId, transfer.fromHouseholdId).firstOrNull()?.currency ?: "USD"
        grantRepository.insert(
            organizationId,
            transfer.toHouseholdId,
            transfer.amountMinor,
            currency,
            FamilyCreditGrantStatus.AVAILABLE,
            CreditSourceType.P2P_TRANSFER,
            transfer.id,
            null,
        )
        val updated = transferRepository.review(transfer.id, organizationId, CreditTransferStatus.APPROVED, currentUser.userId, null)
        if (updated == 0) throw ConflictException("TRANSFER_ALREADY_REVIEWED", "This transfer request is no longer pending.")
        auditService.record(currentUser.userId, organizationId, "family_credit.transfer_approved", "family_credit_transfer", transfer.id)
        return transferRepository.findById(transfer.id, organizationId)!!
    }

    /** Restores the held amount to the sender as a new grant. Manager-gated. */
    @Transactional
    fun rejectTransfer(
        organizationId: UUID,
        transferId: UUID,
        reviewNote: String?,
        currentUser: CurrentUser,
    ): FamilyCreditTransfer {
        membershipService.requireManagerRole(organizationId, currentUser)
        val transfer = pendingTransfer(organizationId, transferId)
        val currency = grantRepository.listForHousehold(organizationId, transfer.toHouseholdId).firstOrNull()?.currency ?: "USD"
        grantRepository.insert(
            organizationId,
            transfer.fromHouseholdId,
            transfer.amountMinor,
            currency,
            FamilyCreditGrantStatus.AVAILABLE,
            CreditSourceType.P2P_TRANSFER,
            transfer.id,
            null,
        )
        val note = reviewNote?.trim()?.takeIf { it.isNotEmpty() }
        val updated = transferRepository.review(transfer.id, organizationId, CreditTransferStatus.REJECTED, currentUser.userId, note)
        if (updated == 0) throw ConflictException("TRANSFER_ALREADY_REVIEWED", "This transfer request is no longer pending.")
        auditService.record(currentUser.userId, organizationId, "family_credit.transfer_rejected", "family_credit_transfer", transfer.id)
        return transferRepository.findById(transfer.id, organizationId)!!
    }

    private fun pendingTransfer(
        organizationId: UUID,
        transferId: UUID,
    ): FamilyCreditTransfer {
        val transfer =
            transferRepository.findById(transferId, organizationId)
                ?: throw NotFoundException("TRANSFER_NOT_FOUND", "The credit transfer request could not be found.")
        if (transfer.status != CreditTransferStatus.PENDING) {
            throw ConflictException("TRANSFER_ALREADY_REVIEWED", "This transfer request is no longer pending.")
        }
        return transfer
    }

    /**
     * Refund reversal policy (documented, not a silent corner case): only the
     * remaining, unapplied portion of a contribution's grant is revoked. Already-
     * applied credit is not clawed back from a fee after the fact — the same
     * posture this codebase already takes on below-cost orders (ADR-017).
     */
    @Transactional
    fun reverseForRefundedContribution(
        organizationId: UUID,
        contributionId: UUID,
    ) = reverseGrant(organizationId, CreditSourceType.CAMPAIGN_ATTRIBUTION, contributionId, "contribution")

    /** Called from `OrderService.refund` only when the order has a real `attributedHouseholdId`. Revokes only the remaining, unapplied portion — never rewrites an already-applied grant. */
    fun reverseForRefundedOrder(
        organizationId: UUID,
        orderId: UUID,
    ) = reverseGrant(organizationId, CreditSourceType.STOREFRONT_ATTRIBUTION, orderId, "order")

    private fun reverseGrant(
        organizationId: UUID,
        sourceType: CreditSourceType,
        sourceId: UUID,
        entityType: String,
    ) {
        val rows = grantRepository.revokeRemainingBySource(organizationId, sourceType, sourceId)
        if (rows > 0) {
            auditService.record(null, organizationId, "family_credit.revoked", entityType, sourceId)
        }
    }
}
