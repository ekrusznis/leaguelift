package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.printify.*`. Catalog reads (blueprints/print-providers/
 * variants) only need [apiToken]; creating a draft order additionally needs
 * [shopId] — Printify's Orders API is scoped under a specific shop
 * (`/v1/shops/{shopId}/orders.json`), and a "Manual/API" shop is exactly the
 * shop type Printify offers for a custom API-driven app like this one (no
 * Shopify/Etsy connection). Neither has a default in staging/prod so a missing
 * value fails startup; both default to blank locally — calls simply fail with a
 * clear error until a founder supplies a real Printify account/shop.
 *
 * Phase 24 slice 24.4 (ADR-070): still deliberately one shared Rally26-controlled
 * account/shop, not one per organization — organization/team ownership is
 * enforced instead by DB-unique external-id columns (`product_variant.printify_product_id`,
 * `fulfillment.printify_order_id`) and an internal org/store SKU-naming prefix
 * (`PrintifyOwnershipPrefixService`), each snapshotted per-row at creation time
 * from whatever [shopId] was configured then. [apiToken]/[shopId] currently
 * still point at a temporary founder-owned Printify account; swapping to a real
 * Rally26-owned one is a config-only change (see ADR-070's manual token-rotation
 * runbook) — historical rows keep pointing at the shop they were actually
 * created under even after that swap.
 */
@ConfigurationProperties(prefix = "rally26.printify")
data class PrintifyProperties(
    val apiToken: String,
    val shopId: String,
    val webhookSecret: String = "",
    val webhookEnabled: Boolean = false,
)
