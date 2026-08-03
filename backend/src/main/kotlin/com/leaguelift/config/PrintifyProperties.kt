package com.leaguelift.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `leaguelift.printify.*`. Catalog reads (blueprints/print-providers/
 * variants) only need [apiToken]; creating a draft order additionally needs
 * [shopId] — Printify's Orders API is scoped under a specific shop
 * (`/v1/shops/{shopId}/orders.json`), and a "Manual/API" shop is exactly the
 * shop type Printify offers for a custom API-driven app like this one (no
 * Shopify/Etsy connection). Neither has a default in staging/prod so a missing
 * value fails startup; both default to blank locally — calls simply fail with a
 * clear error until a founder supplies a real Printify account/shop.
 */
@ConfigurationProperties(prefix = "leaguelift.printify")
data class PrintifyProperties(
	val apiToken: String,
	val shopId: String,
	val webhookSecret: String = "",
	val webhookEnabled: Boolean = false,
)
