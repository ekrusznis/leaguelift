import { heroImages } from "../heroImages";

export type AvailabilityBadge = "Available in Initial Release" | "Pilot Workflow" | "Planned" | "Future";

export type SolutionContent = {
	slug: string;
	navLabel: string;
	heading: string;
	shortDescription: string;
	availability: AvailabilityBadge;
	hero: { headline: string; copy: string; image?: string };
	problem: string;
	approach: string;
	capabilities: string[];
	workflow: string[];
	relatedUsers: string[];
	requiredStatement?: string;
	faq: { question: string; answer: string }[];
};

/**
 * Availability badges reflect what is actually implemented (see docs/launch-checklist.md),
 * not the long-term feature roadmap in DESIGN-DOC.md section 8 — Team/Tournament Pages
 * and Dues & Fees shipped in Phase 1/2; Fundraising, Apparel, Family Credits, and
 * Sponsorships are still ahead (Phases 3-6). Update a badge only when its slice merges.
 */
export const SOLUTIONS: SolutionContent[] = [
	{
		slug: "team-pages",
		navLabel: "Team Pages",
		heading: "Team Pages",
		shortDescription:
			"Give each team a branded home for updates, fundraising, apparel, sponsors, and supporter links.",
		availability: "Available in Initial Release",
		hero: {
			headline: "A public home for every team.",
			copy: "Publish a branded page for each team with a public slug and QR code your families can share.",
		},
		problem:
			"Teams rely on scattered group chats and printed flyers to reach parents and supporters, with no single public page to point people to.",
		approach:
			"LeagueLift gives every team a draft, preview, and publish workflow so administrators control exactly what's public and when.",
		capabilities: [
			"Team branding",
			"Public description",
			"Sport, division, and season",
			"Fundraising links",
			"Apparel collection",
			"Sponsor recognition",
			"QR code",
			"Adult-controlled public contact options",
			"Draft, preview, publish, and archive",
		],
		workflow: [
			"Create the team under your organization",
			"Add branding, description, and season details",
			"Preview the draft page",
			"Publish and share the link or QR code",
		],
		relatedUsers: ["Team managers", "League presidents", "Parents and guardians", "Supporters"],
		faq: [
			{
				question: "Can a team page go live before the season starts?",
				answer: "Yes. Pages stay in draft until an administrator explicitly publishes them.",
			},
			{
				question: "Do team pages replace our roster or scheduling tool?",
				answer: "No. Team pages are a public front door — rostering and scheduling stay in the tools you already use.",
			},
		],
	},
	{
		slug: "tournament-pages",
		navLabel: "Tournament Pages",
		heading: "Tournament Pages",
		shortDescription:
			"Promote an event, highlight participating teams, sell tournament apparel, and recognize sponsors.",
		availability: "Available in Initial Release",
		hero: {
			headline: "One page for the whole tournament.",
			copy: "Publish dates, venue, divisions, and participating teams, and give sponsors and supporters one link to follow.",
		},
		problem:
			"Tournament organizers usually cobble together a PDF, a spreadsheet, and a social post to describe a single event.",
		approach:
			"A tournament gets the same draft/publish page model as a team, plus fields for dates, venue, and divisions.",
		capabilities: [
			"Dates and venue",
			"Participating teams",
			"Divisions",
			"Tournament merchandise",
			"Fundraising",
			"Sponsors",
			"Vendor information",
			"QR-code promotion",
			"Post-event products",
			"Results or schedule links",
		],
		workflow: [
			"Create the tournament under your organization",
			"Set dates, venue, and divisions",
			"Add participating teams",
			"Publish and promote with a QR code",
		],
		relatedUsers: ["Tournament directors", "Club directors", "Team managers", "Sponsors"],
		requiredStatement: "Live scheduling and ticketing are planned for a future release, not part of the initial pilot.",
		faq: [
			{
				question: "Can we list divisions and participating teams?",
				answer: "Yes — divisions and participating teams are part of the published tournament page.",
			},
			{
				question: "Does LeagueLift handle live brackets or ticketing?",
				answer: "Not yet. Live scheduling and ticketing are planned for a future release.",
			},
		],
	},
	{
		slug: "fundraising",
		navLabel: "Fundraising",
		heading: "Fundraising",
		shortDescription: "Campaigns and merchandise tied to real program goals.",
		availability: "Planned",
		hero: {
			headline: "Campaigns built for youth sports programs.",
			copy: "Launch organization- or team-specific campaigns with goals, sharing links, QR codes, and attribution.",
		},
		problem:
			"Ad-hoc fundraising through spreadsheets and personal payment apps makes it hard to see what's working or credit the right team.",
		approach:
			"Fundraising campaigns will live alongside team and tournament pages, with confirmed-contribution reporting and a connection to organization credit rules.",
		capabilities: [
			"Organization and team campaigns",
			"Goals",
			"Date ranges",
			"Share links",
			"QR codes",
			"Team attribution",
			"Family attribution when enabled",
			"Confirmed contribution reporting",
			"Credit-rule connection",
			"Refund reversals",
		],
		workflow: [
			"Create a campaign with a goal and date range",
			"Share the link or QR code",
			"Track confirmed contributions",
			"Apply eligible credits per organization policy",
		],
		relatedUsers: ["Fundraising coordinators", "Booster officers", "Supporters", "Parents and guardians"],
		requiredStatement:
			"LeagueLift does not represent a contribution as tax-deductible unless the organization and transaction qualify.",
		faq: [
			{
				question: "Is fundraising available today?",
				answer: "Not yet — it's planned for a future release. This page describes the intended design.",
			},
			{
				question: "Will contributions be tax-deductible?",
				answer: "Only when the organization and the specific transaction qualify. LeagueLift never assumes deductibility.",
			},
		],
	},
	{
		slug: "apparel",
		navLabel: "Apparel Stores",
		heading: "Apparel Stores",
		shortDescription: "Sell organization, team, season, and tournament merchandise without requiring large inventory purchases.",
		availability: "Planned",
		hero: {
			headline: "Merchandise without the inventory risk.",
			copy: "Sell organization, team, season, and tournament products with attribution back to the right program.",
			image: heroImages.apparelCollection,
		},
		problem:
			"Booster clubs and teams often avoid selling merchandise because it means fronting money for inventory nobody's sure will sell.",
		approach:
			"Apparel stores are designed to connect orders to fulfillment partners so organizations aren't holding physical inventory up front.",
		capabilities: [
			"Organization collections",
			"Team collections",
			"Tournament collections",
			"Variants",
			"Personalization",
			"Fundraising markup",
			"Attribution",
			"Order tracking",
			"Fulfillment status",
		],
		workflow: [
			"Build a collection for your organization, team, or tournament",
			"Set variants, personalization, and markup",
			"Share the store link",
			"Track orders and fulfillment status",
		],
		relatedUsers: ["Merchandise coordinators", "Team managers", "Parents and guardians", "Supporters"],
		requiredStatement: "Not every product type is available until fulfillment-provider support is confirmed.",
		faq: [
			{
				question: "Do we need to buy inventory upfront?",
				answer: "No — the store model is designed around per-order fulfillment rather than bulk inventory purchases.",
			},
			{
				question: "Is the apparel store live today?",
				answer: "Not yet. It's planned for a future release alongside a confirmed fulfillment partner.",
			},
		],
	},
	{
		slug: "dues-and-fees",
		navLabel: "Dues & Fees",
		heading: "Dues & Fees",
		shortDescription: "Assign, collect, and track registration fees, team dues, tournament costs, uniforms, travel, and more.",
		availability: "Available in Initial Release",
		hero: {
			headline: "Clear fees, without the spreadsheet.",
			copy: "Assign fee templates to households and participants, track due dates, and see outstanding balances in one place.",
		},
		problem:
			"Dues and fees are usually tracked in spreadsheets or email threads, making it hard for families or administrators to know what's actually owed.",
		approach:
			"Organizations build fee templates and assign them to households and participants, with manual discounts and credits applied per family.",
		capabilities: [
			"Fee templates",
			"Household and participant assignments",
			"Due dates",
			"Partial payments",
			"Installment plans",
			"Discounts",
			"Scholarships",
			"Waivers",
			"Credits",
			"Receipts",
			"Outstanding-balance reporting",
		],
		workflow: [
			"Create a fee template",
			"Assign it to households or participants",
			"Set due dates and any discounts",
			"Review outstanding balances and collections",
		],
		relatedUsers: ["Treasurers", "Organization administrators", "Parents and guardians"],
		faq: [
			{
				question: "Can families make partial payments?",
				answer: "Partial payments and installment plans are part of the fee model.",
			},
			{
				question: "Can we offer scholarships or waivers?",
				answer: "Yes — manual discounts, scholarships, and waivers can be applied per household.",
			},
		],
	},
	{
		slug: "family-credits",
		navLabel: "Family Credits",
		heading: "Family Credits",
		shortDescription: "Apply organization-approved sales and fundraising credits to eligible family fees.",
		availability: "Planned",
		hero: {
			headline: "Turn eligible sales into fee credits.",
			copy: "Apply organization-approved credit from eligible sales or contributions directly to a family's outstanding fees.",
		},
		problem:
			"Families who sell or fundraise on behalf of a team rarely see that effort reflected in what they owe.",
		approach:
			"Organizations define credit rules; eligible sales and contributions accrue as pending credit that becomes available and can be applied to fees.",
		capabilities: [
			"Eligible sales attribution",
			"Eligible contribution attribution",
			"Pending credit",
			"Available credit",
			"Applied credit",
			"Reversed credit",
			"Organization policies",
			"Limits and expiration",
			"Fee eligibility",
		],
		workflow: [
			"Organization defines a credit rule",
			"Eligible activity accrues pending credit",
			"Credit becomes available per policy",
			"Family applies available credit to an eligible fee",
		],
		relatedUsers: ["Parents and guardians", "Treasurers", "Fundraising coordinators"],
		requiredStatement:
			"LeagueLift family credits are organization-approved fee credits. They are not cash accounts and are not withdrawable or transferable.",
		faq: [
			{
				question: "Are family credits available today?",
				answer: "Not yet — credit rules are planned for a future release after fundraising and apparel ship.",
			},
			{
				question: "Can a family cash out unused credit?",
				answer: "No. Family credits are fee credits only — they are never withdrawable or transferable.",
			},
		],
	},
	{
		slug: "sponsorships",
		navLabel: "Sponsorships",
		heading: "Sponsorships",
		shortDescription: "Create professional packages for local businesses and track fulfillment and renewals.",
		availability: "Future",
		hero: {
			headline: "Sponsor packages built for local businesses.",
			copy: "Create sponsorship packages, take sponsor checkout, and track placement and renewals in one place.",
		},
		problem:
			"Local sponsor relationships are often tracked in someone's inbox, with no clear record of what was promised or fulfilled.",
		approach: "Sponsorships are planned after the initial pilot, building on the organization and team page model already in place.",
		capabilities: [
			"Sponsorship packages",
			"Sponsor checkout",
			"Logo upload",
			"Approval",
			"Placement tracking",
			"Renewal reminders",
			"Sponsor reporting",
		],
		workflow: [
			"Define sponsorship packages",
			"Sponsor completes checkout",
			"Organization approves and places the sponsor",
			"Track renewals and reporting",
		],
		relatedUsers: ["Booster officers", "League presidents", "Sponsors"],
		faq: [
			{
				question: "Is sponsorship management available now?",
				answer: "No — it's planned after the initial pilot. This page describes the intended design.",
			},
		],
	},
];

export function getSolution(slug: string): SolutionContent | undefined {
	return SOLUTIONS.find((solution) => solution.slug === slug);
}
