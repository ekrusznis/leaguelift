package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.stripe.*`. The same account-level secret powers the existing
 * test-mode commerce/Connect flows and Phase 24.6/26.1 organization subscription
 * Checkout. The key itself determines Stripe test vs live mode; Rally26 never toggles
 * that in application code. Neither value has a default in staging/prod. Locally both
 * may be blank, in which case real Stripe calls fail until test credentials are supplied.
 * [webhookSecret] verifies every event received at `POST /api/v1/webhooks/stripe`.
 */
@ConfigurationProperties(prefix = "rally26.stripe")
data class StripeProperties(
    val secretKey: String,
    val webhookSecret: String,
)
