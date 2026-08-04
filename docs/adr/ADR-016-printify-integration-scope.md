# ADR-016: Printify Integration Scope (Phase 4 Slice 1)

## Status
Accepted

## Context

Phase 4 ("Apparel commerce proof," DESIGN-DOC.md §14.1) needed a concrete answer
to a founder request: auto-select the cheapest/closest US print vendor for a
product, and eventually auto-place a logo/name on apparel. Research against
Printify's actual public API (official docs plus a community SDK's documented
response shapes, cross-checked against Printify's own OpenAPI spec at
`developers.printify.com/openapi.json`) found several concrete constraints that
shaped this decision:

1. **Printify's catalog never exposes cost/price.** `GET
   /v1/catalog/blueprints/{id}/print_providers/{id}/variants.json` returns
   `id`/`title`/`options`/`placeholders` only. `cost`/`price` (in cents) only
   appear on a *product's* variants — i.e. only after `POST
   /v1/shops/{shopId}/products.json` is actually called against a specific
   blueprint + print provider + variant. There is no lighter-weight pricing
   endpoint.
2. **Print-provider location is cheap and separate.** `GET
   /v1/catalog/blueprints/{id}/print_providers.json` returns id/title/decoration
   methods only (no location); `GET /v1/catalog/print_providers/{id}.json`
   returns location (country/region/city) per provider. Both are real, catalog-
   only calls.
3. **Order line items don't need a pre-created product.** Printify's
   `submitOrderRequest` schema supports a `lineItemWithBlueprint` shape —
   `print_provider_id`/`blueprint_id`/`variant_id`/`quantity`/`print_areas`
   (a simple `{"front": "<image URL>"}` map) — directly, with no `product_id`
   required. Product creation is therefore *only* needed for cost discovery, not
   for placing orders.
4. **Printify's "personalization" (logo/name auto-placement) is not a general
   API feature.** It only works through Printify's own Product Creator UI on
   their native sales channels (Shopify, Etsy, TikTok Shop). A custom
   API-driven app has to composite the final print-ready image itself before
   calling Printify's Orders/Products APIs, which only ever accept a finished
   image per print area.

## Decision

**Vendor selection is a location (and, where available, decoration-method)
filter, not a price comparison.** `VendorSelectionService.listUsPrintProviders`
fetches a blueprint's print providers, fetches each candidate's location, and
returns only US-located ones. It is explicitly named and documented as a
filter, not a ranking, because there is no cost signal in the catalog to rank
by. The alternative — creating a throwaway product per candidate provider just
to read back a price quote, then discarding the ones not chosen — was
considered and rejected (founder decision, 2026-07-29): it would create
real, if unused, product records in the founder's own Printify shop for every
candidate an admin didn't choose, for a comparison this slice doesn't promise.

**A variant's real cost is learned once, when an admin actually creates it.**
`ProductService.createVariant` calls `PrintifyProductClient.createProduct`
exactly once per admin-chosen provider+variant — never per candidate — and
snapshots the returned `cost`/`price` onto `product_variant`. This is the only
place in the system a cost number is ever recorded; it is never guessed or
estimated.

**Auto-design/personalization is out of scope for this slice.** An org admin
uploads one pre-made design image via the existing, already-built media
pipeline (`MediaEntityType.PRODUCT`, `MediaUsageSlot.PRODUCT_DESIGN` — a small,
additive widening of `media_asset`/`media_assignment`'s check constraints, V14,
rather than a new asset table). The data model (`product.printify_print_position`,
one design per product) is shaped so a future design-generator service could
slot in without reworking `product`/`order`, but no image-compositing
infrastructure exists yet.

**Order fulfillment submission is draft-only.** `PrintifyOrderClient.createDraftOrder`
calls `POST /v1/shops/{shopId}/orders.json` using the `lineItemWithBlueprint`
shape (no pre-created product needed, per finding #3 above) but never calls
`send_to_production.json`. This mirrors the payout module's "onboarding only,
no live execution" precedent (ADR-005): a founder-owned Printify account/shop
is required even for this, and blank credentials fail cleanly
(`ServiceUnavailableException`) exactly like `STRIPE_SECRET_KEY` does today.

**No `cart`/`cart_item` tables.** The cart is frontend-only state until
checkout, submitted as a line-item array (`OrderLineItemRequest[]`) that
becomes multiple Stripe Checkout line items in one Session — no persisted cart
entity needed for a proof-of-checkout slice.

**Confirmation follows the contribution slice's webhook pattern, not the
payout module's sync-refresh pattern**, for the same reason established in
ADR-015: a supporter who pays and closes the tab shouldn't leave Stripe holding
confirmed money Rally26 never records. `OrderService.confirmFromWebhook` is
dispatched from the same `POST /webhooks/stripe` receiver as contributions,
disambiguated by the Stripe Checkout Session's own metadata (`orderId` present
or not).

## Consequences

- `VendorSelectionService`'s name and every place it's surfaced (API responses,
  admin UI copy) must keep making clear this is a location filter, not a price
  ranking — overstating it would be exactly the kind of overclaim DESIGN-DOC.md
  §12.2's truthfulness rules exist to prevent, extended here to internal/admin
  surfaces as well as public marketing ones.
- Real orders can be submitted to Printify (as drafts) using the
  `lineItemWithBlueprint` shape without ever creating a corresponding shop
  Product for that specific order — the product created during
  `createVariant` exists solely for cost discovery and is not otherwise
  referenced at order time.
- Credit rules/allocations ("calculate configured allocations, create pending
  credits" from the Apparel milestone's acceptance bar, DESIGN-DOC.md §14.3)
  remain unbuilt — blocked on the same unresolved §19.3 questions (#6/#16/#17)
  that deferred fundraising credits in ADR-015's slice. Apparel and fundraising
  credits are the same underlying system; neither is built until those
  questions are resolved.
- Refund handling, real send-to-production submission, and cross-provider price
  shopping all remain explicitly out of scope — Phase 5 (payments) and Live
  Fulfillment Launch (DESIGN-DOC.md §14.4) territory respectively.
- If the founder later wants true cheapest-vendor selection, that requires
  either accepting the create-and-discard-drafts cost (revisit this ADR) or a
  future Printify API capability this ADR didn't have available.

## Alternatives Considered

- **Create-and-compare drafts per candidate provider** to get real price
  comparison: rejected per the founder's 2026-07-29 decision — extra API calls,
  cleanup calls to discard unchosen drafts, and throwaway products briefly
  existing in the org's real Printify shop, for a promise ("cheapest") this
  slice doesn't need to make yet.
- **Building the auto-design/personalization compositing engine now**: rejected
  as a much larger, different-risk-profile piece of work than a commerce
  "proof" milestone warrants — see the founder's separate 2026-07-29 decision
  to defer it and stub the interface instead.
- **A separate `fulfillment` Kotlin module**: considered (the repo-structure
  comment in DESIGN-DOC.md §6 lists `fulfillment/` as a candidate future
  module name) but folded into the `order` module instead — one row per order,
  tightly coupled, not worth a separate module boundary at this scope.
