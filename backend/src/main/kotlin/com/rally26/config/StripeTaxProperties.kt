package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.stripe.tax.*` (DESIGN-DOC.md §14.6 item #3). Each Checkout
 * flow is independently gated — Rally26 builds no in-app tax calculation of its
 * own (§19.3 #4); this only tells Stripe Checkout to calculate automatically, and
 * only once a flow's tax_code classification has actually been confirmed and its
 * *Enabled flag flipped on.
 *
 * Every flag defaults to disabled, and every tax code below defaults to null,
 * except [orderTaxCode]: physical merchandise (Swag Shop) is the one flow whose
 * classification isn't ambiguous — `txcd_99999999` is Stripe's own documented
 * general tangible-goods code. Campaign contributions, sponsorships, and
 * participation fees are NOT given a default tax code here — whether each is even
 * taxable (donations are frequently exempt; sponsorship is sometimes treated as
 * advertising; fee taxability varies by state and by what the fee covers) is a
 * real founder/accountant decision, not something to guess at. Enabling tax on
 * any of the three without first confirming the correct classification risks
 * charging sales tax on a donation — a visible, real compliance problem, not
 * just a cosmetic one.
 */
@ConfigurationProperties(prefix = "rally26.stripe.tax")
data class StripeTaxProperties(
    val orderEnabled: Boolean = false,
    val orderTaxCode: String? = "txcd_99999999",
    val contributionEnabled: Boolean = false,
    val contributionTaxCode: String? = null,
    val sponsorshipEnabled: Boolean = false,
    val sponsorshipTaxCode: String? = null,
    val feePaymentEnabled: Boolean = false,
    val feePaymentTaxCode: String? = null,
)
