package com.leaguelift.config

import com.stripe.StripeClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Builds the Stripe SDK client used by the payout module (ADR-005 — see `payout/infra/StripeConnectClient.kt` for the seam that wraps it). */
@Configuration
class StripeConfig(private val stripeProperties: StripeProperties) {

	@Bean
	fun stripeClient(): StripeClient = StripeClient(stripeProperties.secretKey)
}
