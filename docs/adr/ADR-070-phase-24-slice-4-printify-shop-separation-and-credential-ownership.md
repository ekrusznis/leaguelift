# ADR-070: Phase 24 Slice 24.4 — Printify Shop Separation and Credential Ownership

## Status
Accepted

## Context
Rally26's Printify integration has always used exactly one global `shopId`/`apiToken` (`PrintifyProperties`), shared across every organization on the platform, with no organization/team dimension anywhere. `PrintifyProductClient.createProduct` returns Printify's own external product id, but it was silently discarded — never persisted. Product titles and order `external_id`s sent to Printify were raw/unprefixed. No DB uniqueness constraint existed on any Printify external id, and no `PrintifyWebhookController` existed at all — only unused config flags (`PRINTIFY_WEBHOOK_SECRET`/`PRINTIFY_WEBHOOK_ENABLED`) referenced by a status-only Phase 19 contract narrative.

DESIGN-DOC.md §14.1G's target for 24.4:

> Keep one Rally26-controlled Printify account; persist real external shop_id ownership and encode organization/team ownership in internal SKU/naming prefixes without exposing Printify to customers. Enforce provider-shop, organization, team, product, order, and webhook isolation. No product/order may cross organization scope because of an external identifier collision. Replace the temporary founder-owned private token with a Rally26-controlled token, rotate it, and verify catalog/create-product/mockup/order/webhook behavior against the intended shop model.

**This slice does not perform the actual token rotation** — that requires a real external Printify account only the founder can provision. Confirmed explicitly with the user ahead of implementation: build the isolation/scoping/persistence/webhook-receiver architecture against the current shared token/shop so a future swap is config-only, and document the manual rotation runbook rather than attempting it.

Precedent this slice mirrors: `organization_payout_account` (V11) enforces org↔external-id ownership with a `unique` constraint both ways, and `PayoutAccountService` always resolves by our own org-scoped id first, never by reverse-looking-up an external id from an untrusted caller. `StripeWebhookController` shows the exact shape for a provider webhook receiver: HMAC signature verification, `webhook_event` table dedupe via `(provider, external_event_id)`, and routing to the right internal entity via a value *we* minted and made DB-unique — never by trusting org/entity claims embedded in the payload itself.

## Decision

**SKU/naming prefix**: `${organization.slug}/${store.slug}` — both already-validated, human-readable slugs. A store already carries `organizationId` and an optional `teamId`, so it's the existing org/team-scoping boundary object; reusing its slug avoided inventing a second, parallel team-encoding scheme. Applied as:
- Printify product title: `"[${orgSlug}/${storeSlug}] ${productName}"` (defensively truncated to 250 chars — Printify's real title length limit was not verified against live docs in this session)
- Printify order `external_id`: `"${orgSlug}/${storeSlug}:${orderId}"`

This prefix is internal traceability/defense-in-depth only, never shown to a buyer or admin in Rally26's own UI (`ProductVariantResponse`/`OrderList.tsx` were left untouched — no new API exposure of any new field). The real anti-collision guarantee is DB-level uniqueness (below), plus webhook resolution keyed by our own minted, DB-unique values — never by trusting or parsing the prefix out of an untrusted payload.

**Migration V51** (`printify_shop_scoping_and_ownership.sql`):
```sql
alter table product_variant
    add column printify_product_id text unique,
    add column printify_shop_id    text;

alter table fulfillment
    add column printify_shop_id text,
    add constraint fulfillment_printify_order_id_unique unique (printify_order_id);
```
`printify_product_id` lands on `product_variant`, not `product` — confirmed by reading `ProductService.createVariant`, which calls `PrintifyProductClient.createProduct(printifyVariantIds = listOf(one variant), ...)`: each `ProductVariant` row *is* its own distinct Printify "product." Both external-id columns are `unique` (Postgres allows multiple `NULL`s under `unique`, matching the existing `contribution.stripe_checkout_session_id`/`organization_payout_account.stripe_account_id` pattern) — a Printify external id can never resolve to more than one Rally26 row. `printify_shop_id` is snapshotted per-row at creation time from whatever `PrintifyProperties.shopId` was configured then, so historical rows keep pointing at the shop they were actually created under even after a future token/shop rotation.

**`PrintifyOwnershipPrefixService`** (new): the single source of truth for the prefix format, injected into `ProductService` (title) and `OrderService` (order `external_id`). Neither `PrintifyProductClient` nor `PrintifyOrderClient` changed — prefixing happens at the call site, keeping the HTTP clients simple, dumb pass-throughs, matching their existing style.

**`PrintifyWebhookController`** (new), mirroring `StripeWebhookController` exactly: `POST /api/v1/webhooks/printify`, gated to a `404` when `!webhookEnabled || webhookSecret.isBlank()` (no active subscription exists today, so nothing legitimate should ever reach this URL), HMAC-SHA256 signature verification (constant-time compare), `webhook_event` dedupe via `(provider="printify", external_event_id)`, and routes `order:sent-to-production`/`order:shipment:created`/`order:shipment:delivered` onto real `FulfillmentStatus` values via a new `FulfillmentOperationsService.applyProviderStatusUpdate(...)` method. That method resolves the fulfillment via a new `FulfillmentRepository.findByPrintifyOrderId(...)` — our own DB-unique, self-minted key, joined to the order for its real organization id — **never** by trusting anything the webhook payload claims about organization or entity identity. It reuses the existing private `validateTransition` state machine `FulfillmentOperationsService.update` already had, so a webhook can never push a fulfillment through an impossible transition, and is idempotent (a same-status replay is a no-op).

**Two assumptions are explicitly flagged, not fabricated with false confidence**: the signature header name/encoding, and the webhook envelope/`resource` field names, are a best-effort guess — not verified against Printify's live webhook documentation in this session. Both are implemented behind small, easily corrected private functions and must be confirmed before `PRINTIFY_WEBHOOK_ENABLED` is ever set to `true` in production.

**Real-world context this receiver does not currently have live traffic from**: `OrderService.createInitialFulfillment` deliberately never calls Printify's `send_to_production.json` (existing ADR-backed scope — orders stay drafts), so none of the webhook's event types will fire for orders this system creates today. The receiver exists to satisfy the isolation/scoping acceptance criterion and make future activation config-only, not because it has real signal now — `PlatformProviderContractService`'s Printify "Shipment webhooks" capability text was updated to say exactly this, and deliberately stays at `SCAFFOLDED` status rather than `READY`.

## Live-verified

Two new integration tests (`PrintifyShopIsolationIntegrationTest`, real Postgres, mocked Printify HTTP clients) prove the DB-enforced isolation guarantee directly, not just by code inspection:
1. Two organizations' Printify products each get correctly org/store-prefixed titles and distinct, correctly persisted `printify_product_id`/`printify_shop_id` values; attempting to insert a second `product_variant` row with a `printify_product_id` already used by another organization's variant throws a real `DataIntegrityViolationException` from Postgres, not a silent acceptance.
2. A webhook-style status update resolved through one organization's own `printify_order_id` updates only that organization's fulfillment — a second organization's confirmed order, with its own distinct `printify_order_id`, is provably untouched — and an unrelated/never-created `printify_order_id` resolves to nothing.

Full backend suite (`gradlew test ktlintCheck`) passes, including the pre-existing `StoreOrderIntegrationTest`, updated to expect the new prefixed Printify product title (a real, necessary consequence of this slice's change to `ProductService.createVariant`, not a workaround).

## Manual token-rotation runbook (not performed by this slice)

1. Provision a real Rally26-owned Printify account (not the founder's personal one) and create a shop matching the current "Manual/API" shop type.
2. Generate a Personal Access Token from that account's dashboard.
3. Note the new shop's numeric `shop_id`.
4. Update `PRINTIFY_API_TOKEN`/`PRINTIFY_SHOP_ID` in the managed production secret store (already externalized env vars, `application-prod.yml`); restart the backend to pick up the new `PrintifyProperties` bean.
5. Only once ready to receive real webhooks: register the webhook URL with Printify, **verify the real payload/signature-header shape against Printify's live docs first and correct `PrintifyWebhookController` if this ADR's assumptions were wrong**, then set `PRINTIFY_WEBHOOK_SECRET`/`PRINTIFY_WEBHOOK_ENABLED=true`.
6. Post-rotation smoke-test checklist:
   - [ ] A catalog call (`GET /v1/catalog/blueprints.json`) succeeds with the new token.
   - [ ] Creating a product succeeds and returns real cost/mockups; the new `product_variant.printify_shop_id` matches the new shop id, and the product's title in the new Printify dashboard carries the `[orgSlug/storeSlug]` prefix.
   - [ ] A real test order confirms and creates a draft order in the new shop; `fulfillment.printify_shop_id` matches the new shop id and the order's `external_id` in the Printify dashboard carries the expected prefix.
   - [ ] If webhooks were registered: a replayed test event from Printify's dashboard produces a real `PROCESSED` `webhook_event` row (`provider='printify'`) and updates the corresponding `fulfillment.status`.
   - [ ] Historical rows created under the old founder-token shop still show the old `printify_shop_id` — confirming the config swap does not silently reattribute past rows to the new shop.

## Consequences
- Rally26's Printify integration now has a real, DB-enforced answer to "which organization does this external id belong to," closing the acceptance-criterion gap that previously existed by omission (no constraint existed at all, not merely an unenforced one).
- A future per-organization or multi-shop Printify model, if ever needed, has a natural extension point (`printify_shop_id` is already a real per-row column, not inferred from global config) — but this slice deliberately does not build that; DESIGN-DOC.md explicitly calls for keeping one shared account.
- The webhook receiver is real and tested but dormant until (a) `OrderService` is extended to actually submit orders to production in a future phase, and (b) the token-rotation runbook's webhook-registration step is performed and its payload assumptions verified.
- Phase 24's remaining slices (24.5 timezone foundation, 24.6 registration wizard, 24.7 Help Center media) are unaffected and unblocked by this slice.

## Alternatives Considered
- A per-organization Printify shop/account model: rejected — DESIGN-DOC.md explicitly calls for keeping one Rally26-controlled account; a multi-account model would also require per-organization credential provisioning, which is out of scope and unnecessary for the isolation guarantee this slice actually needs.
- Trusting a webhook payload's own embedded metadata (if Printify's real payload ever includes shop/customer-scoped fields) to resolve organization directly: rejected in favor of resolving through our own DB-unique `printify_order_id`, mirroring `StripeWebhookController`'s and `PayoutAccountService`'s established "resolve by our own id, never reverse-lookup an external claim" discipline.
- Encoding organization/team ownership as a raw UUID prefix instead of slugs: rejected — slugs are already the human-readable, validated identifier used everywhere else in this codebase for exactly this kind of external-facing reference (Store/Campaign/Organization all already use the same slug convention), and are easier for a human to recognize in the Printify dashboard during support/debugging.
