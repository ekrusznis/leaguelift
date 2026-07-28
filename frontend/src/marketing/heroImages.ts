/**
 * Typed paths into /public/demo-assets/hero — real generated photography for the
 * public marketing site (docs/LEAGUELIFT_MEDIA_ASSET_DESIGN.md's demo-asset
 * pattern, section 2.2: local static files for now, swappable for a real
 * CDN-backed media API later without touching call sites). These replace the
 * abstract line-icon/SVG placeholders used when no photography was available.
 */

const BASE = "/demo-assets/hero";

export const heroImages = {
	/** Homepage hero — multi-sport athletes in a stadium huddle. */
	multisportHuddle: `${BASE}/hero-multisport-huddle-1600.webp`,
	/** Auth brand panel — multi-sport athletes walking out onto the field. */
	multisportWalkout: `${BASE}/hero-multisport-walkout-900.webp`,
	/** Talk to Sales page — real community handoff/fundraising-table moment. */
	communityHandoff: `${BASE}/hero-community-handoff-1200.webp`,
	/** Apparel solution page — folded/hung team apparel collection. */
	apparelCollection: `${BASE}/hero-apparel-collection-1200.webp`,
	/** Decorative gold/green accent texture for guided-onboarding moments. */
	goldAccent: `${BASE}/hero-gold-accent-900.webp`,
} as const;
