import { heroImages } from "../heroImages";
import { solutionHeroImages } from "../solutionHeroImages";

export type SolutionContent = {
	slug: string;
	navLabel: string;
	heading: string;
	shortDescription: string;
	hero: { headline: string; copy: string; image?: string };
	/** Full-bleed background layer behind the hero section (see solutionHeroImages.ts) — distinct from hero.image's foreground card. */
	heroBackground?: string;
	problem: string;
	approach: string;
	capabilities: string[];
	workflow: string[];
	relatedUsers: string[];
	requiredStatement?: string;
	faq: { question: string; answer: string }[];
};

export const SOLUTIONS: SolutionContent[] = [
	{
		slug: "team-and-tournament-pages",
		navLabel: "Team & Tournament Pages",
		heading: "Team & Tournament Pages",
		shortDescription:
			"Give every team and tournament a branded public home for updates, fundraising, apparel, sponsors, and supporter links.",
		hero: {
			headline: "A public home for every team and tournament.",
			copy: "Publish a branded page for each team or tournament — with dates, divisions, and participating teams — plus a public slug and QR code your families and supporters can share.",
		},
		heroBackground: solutionHeroImages.teamAndTournamentPages,
		problem:
			"Teams rely on scattered group chats and printed flyers to reach parents and supporters, and tournament organizers usually cobble together a PDF, a spreadsheet, and a social post to describe a single event — with no single public page to point people to.",
		approach:
			"Rally26 gives every team and tournament the same draft, preview, and publish workflow, with tournaments adding fields for dates, venue, and divisions, so administrators control exactly what's public and when.",
		capabilities: [
			"Team and tournament branding",
			"Public description",
			"Sport, division, and season",
			"Dates and venue for tournaments",
			"Participating teams and divisions for tournaments",
			"Fundraising links",
			"Apparel and tournament merchandise",
			"Sponsor recognition and vendor information",
			"QR code promotion",
			"Adult-controlled public contact options",
			"Draft, preview, publish, and archive",
		],
		workflow: [
			"Create the team or tournament under your organization",
			"Add branding, description, and — for tournaments — dates, venue, and divisions",
			"Preview the draft page",
			"Publish and share the link or QR code",
		],
		relatedUsers: [
			"Team managers",
			"Tournament directors",
			"League presidents",
			"Club directors",
			"Parents and guardians",
			"Supporters",
			"Sponsors",
		],
		faq: [
			{
				question: "Can a team or tournament page go live before the season starts?",
				answer: "Yes. Pages stay in draft until an administrator explicitly publishes them.",
			},
			{
				question: "Do these pages replace our roster or scheduling tool?",
				answer: "No. Team and tournament pages are a public front door — rostering and scheduling stay in the tools you already use.",
			},
			{
				question: "Can we list divisions and participating teams for a tournament?",
				answer: "Yes — divisions and participating teams are part of the published tournament page.",
			},
		],
	},
	{
		slug: "fundraising",
		navLabel: "Fundraising",
		heading: "Fundraising",
		shortDescription: "Campaigns and merchandise tied to real program goals.",
		hero: {
			headline: "Campaigns built for youth sports programs.",
			copy: "Launch organization- or team-specific campaigns with goals, sharing links, QR codes, and attribution.",
		},
		heroBackground: solutionHeroImages.fundraising,
		problem:
			"Ad-hoc fundraising through spreadsheets and personal payment apps makes it hard to see what's working or credit the right team.",
		approach:
			"Fundraising campaigns live alongside team and tournament pages, with confirmed-contribution reporting and a connection to organization credit rules.",
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
			"Rally26 does not represent a contribution as tax-deductible unless the organization and transaction qualify.",
		faq: [
			{
				question: "How do supporters find a campaign?",
				answer: "Share the campaign link or QR code from the team or tournament page — supporters can contribute without creating an account.",
			},
			{
				question: "Will contributions be tax-deductible?",
				answer: "Only when the organization and the specific transaction qualify. Rally26 never assumes deductibility.",
			},
		],
	},
	{
		slug: "apparel",
		navLabel: "Apparel Stores",
		heading: "Apparel Stores",
		shortDescription: "Sell organization, team, season, and tournament merchandise without requiring large inventory purchases.",
		hero: {
			headline: "Merchandise without the inventory risk.",
			copy: "Sell organization, team, season, and tournament products with attribution back to the right program.",
			image: heroImages.apparelCollection,
		},
		problem:
			"Booster clubs and teams often avoid selling merchandise because it means fronting money for inventory nobody's sure will sell.",
		approach:
			"Apparel stores connect orders to fulfillment partners so organizations aren't holding physical inventory up front.",
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
		faq: [
			{
				question: "Do we need to buy inventory upfront?",
				answer: "No — the store model is built around per-order fulfillment rather than bulk inventory purchases.",
			},
			{
				question: "Can we offer different product types?",
				answer: "Product options depend on which fulfillment providers are connected to your store.",
			},
		],
	},
	{
		slug: "dues-and-fees",
		navLabel: "Dues & Fees",
		heading: "Dues & Fees",
		shortDescription: "Assign, collect, and track registration fees, team dues, tournament costs, uniforms, travel, and more.",
		hero: {
			headline: "Clear fees, without the spreadsheet.",
			copy: "Assign fee templates to households and participants, track due dates, and see outstanding balances in one place.",
		},
		heroBackground: solutionHeroImages.duesAndFees,
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
		hero: {
			headline: "Turn eligible sales into fee credits.",
			copy: "Apply organization-approved credit from eligible sales or contributions directly to a family's outstanding fees.",
		},
		heroBackground: solutionHeroImages.familyCredits,
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
			"Rally26 family credits are organization-approved fee credits. They are not cash accounts and are not withdrawable or transferable.",
		faq: [
			{
				question: "How does a family start earning credit?",
				answer: "Once your organization defines a credit rule, eligible sales and contributions automatically accrue as pending credit.",
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
		hero: {
			headline: "Sponsor packages built for local businesses.",
			copy: "Create sponsorship packages, take sponsor checkout, and track placement and renewals in one place.",
		},
		heroBackground: solutionHeroImages.sponsorships,
		problem:
			"Local sponsor relationships are often tracked in someone's inbox, with no clear record of what was promised or fulfilled.",
		approach: "Sponsorships build on the organization and team page model already in place.",
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
				question: "How does a sponsor get placed on a page?",
				answer: "Once a sponsor completes checkout, an organization administrator approves and places them before they appear publicly.",
			},
		],
	},
];

export function getSolution(slug: string): SolutionContent | undefined {
	return SOLUTIONS.find((solution) => solution.slug === slug);
}
