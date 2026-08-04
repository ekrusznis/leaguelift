/**
 * Primary navigation structure (RALLY26_SALES_SITE_DESIGN.md section 8.1).
 *
 * Two kinds of destination:
 * - `to`: a real route.
 * - `hash`: a section on the homepage. The IA (section 7.1) has no standalone
 *   "/features" or "/overview" route, so these items scroll to the matching
 *   homepage section instead — smoothly if already on "/", or by navigating home
 *   and then scrolling once the page has rendered (see `useScrollToHash`).
 */
export type NavLeaf = { label: string; to: string } | { label: string; hash: string };

export type NavGroup = {
	label: string;
	items: NavLeaf[];
};

/**
 * 2026-08-03 (ADR-057 rebrand + single-page site): HomePage absorbed the former
 * standalone /how-it-works, /solutions, /pricing, and /about pages as scroll
 * sections — see pages/marketing/HomePage.tsx. Every in-page anchor below points
 * at a real section id defined there. This is also, as of the same date, the only
 * nav model in the codebase — the former /landing-preview experiment (its own
 * content/landingPreviewNav.ts) was folded in here once the founder confirmed the
 * single-page layout, and both the preview route and its nav file were deleted.
 */
export const HOMEPAGE_SECTION_IDS = {
	hero: "hero",
	solutions: "solutions",
	platform: "platform",
	howItWorks: "how-it-works",
	audiences: "audiences",
	pricing: "pricing-preview",
	about: "about",
	faq: "faq",
	/** Contact Us section, added just above the footer (ADR-059) — replaces the old standalone /contact mock page and the removed /book-demo page. */
	contactUs: "contact-us",
} as const;

export const PRIMARY_NAV: NavGroup[] = [
	{
		label: "Product",
		items: [
			{ label: "Overview", hash: HOMEPAGE_SECTION_IDS.hero },
			{ label: "How It Works", hash: HOMEPAGE_SECTION_IDS.howItWorks },
			{ label: "One Platform", hash: HOMEPAGE_SECTION_IDS.platform },
		],
	},
	{
		label: "Solutions",
		items: [
			{ label: "Team & Tournament Pages", to: "/solutions/team-and-tournament-pages" },
			{ label: "Fundraising", to: "/solutions/fundraising" },
			{ label: "Apparel Stores", to: "/solutions/apparel" },
			{ label: "Dues & Fees", to: "/solutions/dues-and-fees" },
			{ label: "Family Credits", to: "/solutions/family-credits" },
			{ label: "Sponsorships", to: "/solutions/sponsorships" },
		],
	},
	{
		label: "Resources",
		items: [
			{ label: "Talk to Sales", to: "/talk-to-sales" },
			{ label: "Help Center", to: "/help" },
			{ label: "Security", to: "/security" },
			{ label: "Contact", hash: HOMEPAGE_SECTION_IDS.contactUs },
		],
	},
];

export const SIMPLE_NAV_LINKS: NavLeaf[] = [
	{ label: "Pricing", hash: HOMEPAGE_SECTION_IDS.pricing },
	{ label: "About", hash: HOMEPAGE_SECTION_IDS.about },
];

/** Flat single-row nav for the homepage's own header (HomeHeader) — every item is a same-page anchor, unlike SiteHeader's dropdown IA used on inner pages. */
export const HOME_NAV_LINKS: { label: string; hash: (typeof HOMEPAGE_SECTION_IDS)[keyof typeof HOMEPAGE_SECTION_IDS] }[] = [
	{ label: "Overview", hash: HOMEPAGE_SECTION_IDS.hero },
	{ label: "Solutions", hash: HOMEPAGE_SECTION_IDS.solutions },
	{ label: "Platform", hash: HOMEPAGE_SECTION_IDS.platform },
	{ label: "How It Works", hash: HOMEPAGE_SECTION_IDS.howItWorks },
	{ label: "Pricing", hash: HOMEPAGE_SECTION_IDS.pricing },
	{ label: "FAQ", hash: HOMEPAGE_SECTION_IDS.faq },
	{ label: "Contact", hash: HOMEPAGE_SECTION_IDS.contactUs },
];
