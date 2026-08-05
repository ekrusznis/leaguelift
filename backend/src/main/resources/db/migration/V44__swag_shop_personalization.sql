-- Swag Shop slice 1 (personalized apparel, Path 1/Quick only — DESIGN-DOC.md
-- section 13 "Personalized Apparel Ordering / Swag Shop", decided 2026-08-05).
-- A coach (for their team roster) or guardian (for their own household) orders
-- an existing Swag Shop apparel type with the team logo automatically placed,
-- optionally adding the athlete's name/number in a curated placement preset.
-- The composited print file is generated once, after payment confirms, and
-- submitted directly to Printify's order API (no per-order Printify "product"
-- is created — see PrintifyOrderClient, which already accepts a raw image URL
-- per print position at order-submission time).

-- Frozen at Swag-Shop-setup time, not re-resolved at order time, matching the
-- existing snapshot-don't-live-reference convention (e.g. order_item's own
-- unit_price_minor/unit_cost_minor). A team logo change after setup does not
-- retroactively change an already-configured apparel type.
alter table product add column swag_logo_media_asset_id uuid references media_asset (id);

-- The real Printify print-area pixel dimensions for this variant's position,
-- captured once from Printify's catalog response at variant-creation time
-- (the same call that already learns cost_minor/price_minor) rather than
-- re-fetched live at order time, so composing a print file never depends on
-- an extra Printify call inside the payment-confirmation transaction.
alter table product_variant add column print_area_width_px integer;
alter table product_variant add column print_area_height_px integer;

-- Personalization is a fixed 3-field shape for Path 1 (Quick) — real typed
-- columns, not jsonb, matching this schema's general preference (shipping_address
-- is the one deliberate jsonb exception, for a genuinely free-form address shape).
-- All nullable: an order item with no personalization behaves exactly as today's
-- existing static-design flow (see OrderService.createInitialFulfillment).
alter table order_item add column participant_id uuid references participant (id);
alter table order_item add column personalization_name text;
alter table order_item add column personalization_number text;
alter table order_item add column personalization_placement text;
alter table order_item add constraint order_item_personalization_placement_check check (
    personalization_placement is null
    or personalization_placement in ('LEFT_CHEST', 'RIGHT_CHEST', 'BACK')
);
