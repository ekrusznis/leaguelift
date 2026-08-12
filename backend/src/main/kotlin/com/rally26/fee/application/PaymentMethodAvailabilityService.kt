package com.rally26.fee.application

import com.rally26.common.error.NotFoundException
import com.rally26.config.AffirmProperties
import com.rally26.config.PayPalProperties
import com.rally26.config.SquareProperties
import com.rally26.organization.persistence.OrganizationRepository
import org.springframework.stereotype.Service
import java.util.UUID

data class PaymentMethodAvailability(
    val method: String,
    val displayName: String,
    val available: Boolean,
    val note: String?,
)

/**
 * Phase 32 (Payment Choice Expansion) scaffold: what a guardian can pay a fee
 * with, alongside the existing Stripe card checkout. Deliberately not routed
 * through the `integration/core` catalog (`IntegrationCatalogService`) — that
 * machinery models per-org/per-user OAuth consent to an *external* data provider
 * (SportsEngine, TeamSnap, QuickBooks), gated `listForOrganization`-only to org
 * managers. Venmo/Cash App/Affirm are Rally26's own single merchant-of-record
 * accounts (matching Stripe/Printify's existing shape, ADR-005), and this
 * availability data carries no secrets, so it's exposed directly to any
 * authenticated user — a guardian included, not just staff.
 */
@Service
class PaymentMethodAvailabilityService(
    private val paypalProperties: PayPalProperties,
    private val squareProperties: SquareProperties,
    private val affirmProperties: AffirmProperties,
    private val organizationRepository: OrganizationRepository,
) {
    fun list(organizationId: UUID): List<PaymentMethodAvailability> {
        val organization =
            organizationRepository.findById(organizationId)
                ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
        val zelleHandle = organization.zelleHandle
        return listOf(
            PaymentMethodAvailability("STRIPE_ONLINE", "Credit or debit card", available = true, note = null),
            PaymentMethodAvailability(
                "VENMO",
                "Venmo",
                available = paypalProperties.configured,
                note = if (paypalProperties.configured) null else "Coming soon",
            ),
            PaymentMethodAvailability(
                "CASH_APP_PAY",
                "Cash App Pay",
                available = squareProperties.configured,
                note = if (squareProperties.configured) null else "Coming soon",
            ),
            PaymentMethodAvailability(
                "AFFIRM",
                "Pay over time (Affirm)",
                available = affirmProperties.configured,
                note = if (affirmProperties.configured) null else "Coming soon",
            ),
            PaymentMethodAvailability(
                "ZELLE",
                "Zelle",
                available = zelleHandle != null,
                note =
                    if (zelleHandle != null) {
                        "Send to $zelleHandle, then let staff know so they can confirm your payment."
                    } else {
                        "Not set up by this organization yet"
                    },
            ),
        )
    }
}
