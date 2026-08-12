package com.rally26.common.stripe

import com.stripe.param.checkout.SessionCreateParams

/**
 * Shared automatic_tax wiring for the four Checkout Session builders
 * (DESIGN-DOC.md §14.6 item #3) — kept in one place so each flow applies it
 * identically rather than four slightly-different copies drifting apart.
 */
object StripeTaxSupport {
    /**
     * Enables Stripe's automatic tax calculation on a Checkout Session. Stripe
     * requires either shipping or billing address collection to be configured
     * whenever automatic_tax is on (it needs a location to calculate against) —
     * [collectBillingAddress] should be true for any flow that doesn't already
     * collect a shipping address (every flow except orders).
     */
    fun applyAutomaticTax(
        builder: SessionCreateParams.Builder,
        collectBillingAddress: Boolean,
    ): SessionCreateParams.Builder {
        builder.setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build())
        if (collectBillingAddress) {
            builder.setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
        }
        return builder
    }

    /** Applies a tax_code to a line item's ProductData, if one is configured — a blank/null code is left unset rather than guessed. */
    fun applyTaxCode(
        productData: SessionCreateParams.LineItem.PriceData.ProductData.Builder,
        taxCode: String?,
    ): SessionCreateParams.LineItem.PriceData.ProductData.Builder {
        if (!taxCode.isNullOrBlank()) {
            productData.setTaxCode(taxCode)
        }
        return productData
    }
}
