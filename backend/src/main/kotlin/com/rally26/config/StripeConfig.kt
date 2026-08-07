package com.rally26.config

import com.stripe.StripeClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Shared Stripe SDK client for payouts, Checkout commerce, and organization subscription billing. */
@Configuration
class StripeConfig(
    private val stripeProperties: StripeProperties,
) {
    @Bean
    fun stripeClient(): StripeClient = StripeClient(stripeProperties.secretKey)
}
