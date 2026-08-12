package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.paypal.*`. Phase 32 scaffold (Venmo, via approved PayPal
 * Business Checkout) — blank by default everywhere, matching every other
 * provider-key scaffold in this codebase (StripeProperties/PrintifyProperties):
 * blank means "not yet configured," never a crash. No API calls are made from
 * this property alone — [PaymentMethodAvailabilityService][com.rally26.fee.application.PaymentMethodAvailabilityService]
 * only uses it to decide whether Venmo shows as available or "coming soon."
 */
@ConfigurationProperties(prefix = "rally26.paypal")
data class PayPalProperties(
    val clientId: String = "",
    val clientSecret: String = "",
) {
    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}
