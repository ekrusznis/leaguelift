package com.rally26.payout.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.ledger.application.LedgerService
import com.rally26.ledger.application.PayoutSummary
import com.rally26.ledger.domain.signedAmountMinor
import com.rally26.membership.application.MembershipService
import com.rally26.payout.domain.OrganizationPayoutAccount
import com.rally26.payout.infra.StripeConnectClient
import com.rally26.payout.persistence.OrganizationPayoutAccountRepository
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = LoggerFactory.getLogger(PayoutAccountService::class.java)

/** Rally26 is USD-only today (see FeeService.formatMinor's same assumption) — revisit if a non-USD currency is ever supported. */
private const val PAYOUT_CURRENCY = "usd"

/**
 * Stripe Connect Express onboarding (ADR-005), plus — as of Phase 5 slice 1
 * (ADR-017) — real transfers out of Rally26's Stripe balance into an org's
 * connected account. Manual-trigger-only this phase: no scheduler exists that
 * fires transfers automatically, an OWNER/ADMINISTRATOR must call [triggerTransfer].
 */
@Service
class PayoutAccountService(
    private val payoutAccountRepository: OrganizationPayoutAccountRepository,
    private val stripeConnectClient: StripeConnectClient,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val ledgerService: LedgerService,
) {
    /** Null means onboarding has never been started — a legitimate "not started" state, not an error. */
    fun getStatus(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationPayoutAccount? {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return payoutAccountRepository.findByOrganizationId(organizationId)
    }

    @Transactional
    fun startOnboarding(
        organizationId: UUID,
        refreshUrl: String,
        returnUrl: String,
        currentUser: CurrentUser,
    ): String {
        membershipService.requireOwnerRole(organizationId, currentUser)
        return withStripeErrorTranslation {
            val existing = payoutAccountRepository.findByOrganizationId(organizationId)
            val account =
                existing ?: run {
                    val stripeAccountId = stripeConnectClient.createExpressAccount()
                    payoutAccountRepository.insert(organizationId, stripeAccountId)
                }
            val onboardingUrl = stripeConnectClient.createOnboardingLink(account.stripeAccountId, refreshUrl, returnUrl)
            auditService.record(currentUser.userId, organizationId, "payout.onboarding_started", "organization_payout_account", account.id)
            onboardingUrl
        }
    }

    @Transactional
    fun refreshStatus(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OrganizationPayoutAccount {
        membershipService.requireManagerRole(organizationId, currentUser)
        val account =
            payoutAccountRepository.findByOrganizationId(organizationId)
                ?: throw NotFoundException("PAYOUT_ACCOUNT_NOT_FOUND", "Payout onboarding hasn't been started for this organization.")
        return withStripeErrorTranslation {
            val status = stripeConnectClient.retrieveAccountStatus(account.stripeAccountId)
            payoutAccountRepository.updateStatus(organizationId, status.detailsSubmitted, status.chargesEnabled, status.payoutsEnabled)
            payoutAccountRepository.findByOrganizationId(organizationId)!!
        }
    }

    fun getPayoutSummary(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): PayoutSummary {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return ledgerService.getPayoutSummary(organizationId)
    }

    /**
     * Manual-trigger-only (ADR-017) — computes the currently-transferable amount
     * (eligible earnings past the holding period, net of any pending refund/negative-
     * balance debits), calls Stripe, then records the TRANSFER entry. A zero or negative
     * amount is a clean no-op, not an error — there may simply be nothing eligible yet.
     */
    @Transactional
    fun triggerTransfer(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): PayoutSummary {
        membershipService.requireManagerRole(organizationId, currentUser)
        val account =
            payoutAccountRepository.findByOrganizationId(organizationId)
                ?: throw NotFoundException("PAYOUT_ACCOUNT_NOT_FOUND", "Payout onboarding hasn't been started for this organization.")
        if (!account.payoutsEnabled) {
            throw ValidationException("This organization's connected account isn't yet enabled to receive payouts.")
        }
        val entries = ledgerService.getTransferableEntries(organizationId)
        val amountMinor = entries.sumOf { it.signedAmountMinor() }
        if (amountMinor <= 0) {
            return ledgerService.getPayoutSummary(organizationId)
        }
        return withStripeErrorTranslation {
            val stripeTransferId = stripeConnectClient.createTransfer(account.stripeAccountId, amountMinor, PAYOUT_CURRENCY)
            ledgerService.recordTransfer(organizationId, amountMinor, PAYOUT_CURRENCY, stripeTransferId, entries.map { it.id })
            auditService.record(currentUser.userId, organizationId, "payout.transfer_triggered", "organization_payout_account", account.id)
            ledgerService.getPayoutSummary(organizationId)
        }
    }

    /**
     * Translates a raw Stripe SDK failure (most commonly: no test-mode key configured
     * yet, since STRIPE_SECRET_KEY is blank by default locally) into a clean, stable
     * client error instead of the generic 500 an unhandled StripeException would
     * otherwise produce. Full detail still reaches server logs via the exception cause.
     */
    private fun <T> withStripeErrorTranslation(block: () -> T): T =
        try {
            block()
        } catch (e: StripeException) {
            log.warn("Stripe API call failed: {}", e.message, e)
            throw ServiceUnavailableException(
                "PAYOUT_PROVIDER_UNAVAILABLE",
                "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set.",
            )
        }
}
