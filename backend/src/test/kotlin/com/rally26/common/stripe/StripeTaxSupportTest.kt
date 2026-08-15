package com.rally26.common.stripe

import com.stripe.param.checkout.SessionCreateParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StripeTaxSupportTest {
    @Test
    fun `applyAutomaticTax enables automatic tax and requires billing address when requested`() {
        val builder = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.PAYMENT)

        StripeTaxSupport.applyAutomaticTax(builder, collectBillingAddress = true)
        val params = builder.build()

        assertEquals(true, params.automaticTax?.enabled)
        assertEquals(SessionCreateParams.BillingAddressCollection.REQUIRED, params.billingAddressCollection)
    }

    @Test
    fun `applyAutomaticTax leaves billing address collection unset when not requested`() {
        val builder = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.PAYMENT)

        StripeTaxSupport.applyAutomaticTax(builder, collectBillingAddress = false)
        val params = builder.build()

        assertEquals(true, params.automaticTax?.enabled)
        assertNull(params.billingAddressCollection)
    }

    @Test
    fun `applyTaxCode sets the tax code when present`() {
        val productData =
            SessionCreateParams.LineItem.PriceData.ProductData
                .builder()
                .setName("Widget")

        StripeTaxSupport.applyTaxCode(productData, "txcd_99999999")

        assertEquals("txcd_99999999", productData.build().taxCode)
    }

    @Test
    fun `applyTaxCode leaves the tax code unset when null or blank`() {
        val nullCode =
            SessionCreateParams.LineItem.PriceData.ProductData
                .builder()
                .setName("Widget")
        val blankCode =
            SessionCreateParams.LineItem.PriceData.ProductData
                .builder()
                .setName("Widget")

        StripeTaxSupport.applyTaxCode(nullCode, null)
        StripeTaxSupport.applyTaxCode(blankCode, "  ")

        assertNull(nullCode.build().taxCode)
        assertNull(blankCode.build().taxCode)
    }
}
