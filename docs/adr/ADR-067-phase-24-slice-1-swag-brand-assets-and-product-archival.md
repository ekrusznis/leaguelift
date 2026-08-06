# ADR-067: Phase 24 Slice 1 — Swag Brand Assets and Product Archival

## Status
Accepted

## Context
Path 1 stored one team-logo media reference directly on each product. Phase 24 requires reusable organization/team brand marks, durable product provenance, and a real archived-product lifecycle without changing historical order snapshots.

## Decision
- Add an organization-scoped `swag_brand_asset` registry with optional team scope, named categories, and `ACTIVE`/`ARCHIVED` status.
- Reuse the existing secure media upload pipeline. A new library entry may reference only a caller-owned, confirmed `READY` PNG, JPEG, or WebP uploaded as `LOGO` or `PRODUCT_DESIGN`; later restore/assignment validates the durable media record without requiring the original uploader.
- A team store can select active organization-wide assets and active/archived assets scoped to that team. Team-scoped assets never cross teams, and team coaches do not gain archived organization-library visibility.
- Product assignment snapshots both `swag_brand_asset_id` provenance and the exact `swag_logo_media_asset_id` used by fulfillment. A composite foreign key prevents cross-organization product/asset links. Source archival does not mutate products or orders.
- Keep the existing “use team logo” action as a compatibility path. New setup should prefer the library.
- Hide archived products from default organization and Platform Admin management queries and all public/cart paths. Organization `includeArchived=true` requires store-management access; Platform Admin retrieval requires an explicit `ARCHIVED` filter.
- Restoring an archived product always returns it to `DRAFT`; direct `ARCHIVED -> ACTIVE` transitions are rejected. Existing no-order-history deletion rules remain unchanged.

## Consequences
- Operators can maintain alternate/light/dark/seasonal marks once and reuse them across products.
- Signed preview URLs remain short-lived and source media stays private.
- Archived products retain order/report joins, stay out of default organization/platform triage, and cannot accidentally re-enter checkout.
- The next Path 2 slice can build placement/size previews on a stable, server-validated asset snapshot.

## Alternatives Considered
- Reusing live team-logo assignments only: rejected because later logo edits would make product setup ambiguous and cannot represent alternate marks.
- Copying media bytes for every product: rejected because immutable ID snapshots already provide the required historical behavior without duplicate storage.
- Allowing direct archive-to-active restore: rejected because an old product must be reviewed before returning to sale.
