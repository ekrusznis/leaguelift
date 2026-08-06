-- Swag Shop garment mockup images + physical back printing (DESIGN-DOC.md
-- section 13). Printify's real create-product response (already called for
-- cost discovery at variant-creation time, ADR-016) generates photorealistic
-- garment mockup images per position — front/back/size-chart — with the
-- design already composited in place. Previously discarded entirely; now
-- captured and stored so admins and buyers can see a real picture of the
-- shirt/color/logo instead of text-only dropdowns.

-- Mirrors the existing print_area_width_px/height_px columns (V44) exactly,
-- but for the "back" placeholder instead of "front" — captured from the same
-- already-fetched Printify catalog variant response, no extra API call.
alter table product_variant add column back_print_area_width_px integer;
alter table product_variant add column back_print_area_height_px integer;

-- Printify's images-api.printify.com CDN URLs from the create-product
-- response. Stable, not presigned/expiring (unlike our own Spaces URLs), safe
-- to store as plain text and serve directly to the frontend.
alter table product_variant add column mockup_front_url text;
alter table product_variant add column mockup_back_url text;
