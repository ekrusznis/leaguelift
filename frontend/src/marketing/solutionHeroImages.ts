/**
 * Typed paths into /public/demo-assets/solutions — semi-transparent navy/orange
 * illustration artwork used as a full-bleed background layer behind each solution
 * detail page's hero copy (distinct from heroImages.ts's foreground product-photo
 * slot, used by e.g. apparel's hero.image). Same local-static-file pattern as
 * heroImages.ts (docs/RALLY26_MEDIA_ASSET_DESIGN.md section 2.2).
 */

const BASE = "/demo-assets/solutions";

export const solutionHeroImages = {
	/** Team & Tournament Pages — bracket, stadium, venue pin, and QR check-in motif. */
	teamAndTournamentPages: `${BASE}/hero-tournament-bracket-1536.webp`,
	/** Fundraising — campaign-goal progress ring and supporter hearts. */
	fundraising: `${BASE}/hero-fundraising-progress-1536.webp`,
	/** Dues & Fees — invoices, due-date, and secure-payment motif. */
	duesAndFees: `${BASE}/hero-dues-secure-payment-1536.webp`,
	/** Family Credits — eligible purchases flowing into a reward/credit balance. */
	familyCredits: `${BASE}/hero-family-credits-rewards-1536.webp`,
	/** Sponsorships — verification, handshake, and storefront placement motif. */
	sponsorships: `${BASE}/hero-sponsorship-handshake-1536.webp`,
} as const;
