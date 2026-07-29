/**
 * Nav model for the single-page landing preview (/landing-preview). Kept separate
 * from content/nav.ts so this experiment can't accidentally change the real site's
 * header — see the sales-site redesign review for the plan to fold this into
 * content/nav.ts once the comparison is settled.
 *
 * IDs are prefixed "preview-" so they never collide with HomePage's own section
 * ids if both pages are ever inspected side by side.
 */
export const PREVIEW_SECTION_IDS = {
	hero: "preview-hero",
	solutions: "preview-solutions",
	howItWorks: "preview-how-it-works",
	audiences: "preview-audiences",
	pricing: "preview-pricing",
	about: "preview-about",
	faq: "preview-faq",
} as const;

export type PreviewSectionId = (typeof PREVIEW_SECTION_IDS)[keyof typeof PREVIEW_SECTION_IDS];

export const PREVIEW_NAV_LINKS: { label: string; hash: PreviewSectionId }[] = [
	{ label: "Overview", hash: PREVIEW_SECTION_IDS.hero },
	{ label: "Solutions", hash: PREVIEW_SECTION_IDS.solutions },
	{ label: "How It Works", hash: PREVIEW_SECTION_IDS.howItWorks },
	{ label: "Pricing", hash: PREVIEW_SECTION_IDS.pricing },
	{ label: "About", hash: PREVIEW_SECTION_IDS.about },
	{ label: "FAQ", hash: PREVIEW_SECTION_IDS.faq },
];
