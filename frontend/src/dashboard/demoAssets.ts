/**
 * Typed paths into /public/demo-assets (see docs/RALLY26_MEDIA_ASSET_DESIGN.md
 * sections 18-21). Only the decorative sidebar-promo background remains here —
 * it's generic marketing art, not fictional customer data, so it's fine in
 * production. The fictional avatar/logo/product-image exports that used to live
 * here (athleteAvatars, adultAvatars, teamLogos, organizationLogos,
 * productImages, panelBackgrounds) were removed: `adultAvatars` was the one
 * actually wired into real dashboards (`userAvatarSrc`), showing every real
 * Owner/Parent/Coach account the same stock photo of a stranger as "themselves"
 * — replaced with each user's own uploaded photo or a default initials avatar.
 * The rest had zero real usages anywhere in the app.
 */

const BASE = "/demo-assets";

export const sidebarPromoBackground = `${BASE}/promotions/sidebar-promo-480.webp`;
