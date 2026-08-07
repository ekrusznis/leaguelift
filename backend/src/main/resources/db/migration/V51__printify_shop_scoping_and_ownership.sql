-- Phase 24 slice 24.4 (DESIGN-DOC.md section 14.1G, ADR-070): Printify shop
-- separation and credential ownership. Keeps the single Rally26-controlled
-- Printify account model (explicitly NOT per-organization Printify accounts)
-- but makes shop/organization ownership real and DB-enforced instead of
-- implicit in one shared config value.
--
-- printify_product_id lands on product_variant, not product: each
-- ProductService.createVariant() call creates one full Printify "product"
-- scoped to a single variant (PrintifyProductClient.createProduct() is
-- always called with printifyVariantIds = listOf(one variant)) — the real
-- 1:1 relationship is variant <-> Printify product.
--
-- printify_shop_id is snapshotted on both tables at creation time from
-- whatever rally26.printify.shop-id was configured then. Today that's
-- always the one shared shop; freezing it per row (rather than only
-- reading the current config) means historical rows keep pointing at the
-- shop they were actually created under even after a future token/shop
-- rotation — see ADR-070's manual token-rotation runbook.
--
-- Both external ids get a plain `unique` constraint — Postgres allows
-- multiple NULLs under `unique` (same pattern as
-- contribution.stripe_checkout_session_id in V12 and
-- organization_payout_account.stripe_account_id in V11) — so a Printify
-- external id can never resolve to more than one Rally26 row. This is the
-- DB-enforced cross-organization isolation guarantee this slice requires.

alter table product_variant
    add column printify_product_id text unique,
    add column printify_shop_id    text;

alter table fulfillment
    add column printify_shop_id text,
    add constraint fulfillment_printify_order_id_unique unique (printify_order_id);
