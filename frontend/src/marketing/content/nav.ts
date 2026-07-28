/**
 * Primary navigation structure (LEAGUELIFT_SALES_SITE_DESIGN.md section 8.1).
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

export const HOMEPAGE_SECTION_IDS = {
	hero: "hero",
	benefits: "benefits",
	features: "features",
	howItWorks: "how-it-works",
	pricing: "pricing-preview",
	faq: "faq",
} as const;

export const PRIMARY_NAV: NavGroup[] = [
	{
		label: "Product",
		items: [
			{ label: "Overview", hash: HOMEPAGE_SECTION_IDS.hero },
			{ label: "How It Works", to: "/how-it-works" },
			{ label: "Features", hash: HOMEPAGE_SECTION_IDS.features },
		],
	},
	{
		label: "Solutions",
		items: [
			{ label: "Team Pages", to: "/solutions/team-pages" },
			{ label: "Tournament Pages", to: "/solutions/tournament-pages" },
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
			{ label: "Contact", to: "/contact" },
		],
	},
];

export const SIMPLE_NAV_LINKS: { label: string; to: string }[] = [
	{ label: "Pricing", to: "/pricing" },
	{ label: "About", to: "/about" },
];
