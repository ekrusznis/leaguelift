import { useEffect, type ReactNode } from "react";
import { AnnouncementBar } from "../../marketing/components/AnnouncementBar";
import { AudienceCard } from "../../marketing/components/AudienceCard";
import { AvailabilityStatusBadge } from "../../marketing/components/Badges";
import { FaqAccordion } from "../../marketing/components/FaqAccordion";
import { FeatureCard } from "../../marketing/components/FeatureCard";
import { HomeHeader } from "../../marketing/components/HomeHeader";
import { PageContainer } from "../../marketing/components/PageContainer";
import { ResponsiveVisual } from "../../marketing/components/ResponsiveVisual";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { SiteFooter } from "../../marketing/components/SiteFooter";
import { StepTimeline } from "../../marketing/components/StepTimeline";
import { PrimaryButton, SecondaryDarkButton, SecondaryLightButton, TextButton } from "../../marketing/components/buttons";
import { HOMEPAGE_SECTION_IDS } from "../../marketing/content/nav";
import { HOMEPAGE_FAQ } from "../../marketing/content/faq";
import { PRICING_FAQ } from "../../marketing/content/pricing";
import { SOLUTIONS } from "../../marketing/content/solutions";
import { heroImages } from "../../marketing/heroImages";
import { track } from "../../marketing/analytics";
import { usePendingHomeScroll } from "../../marketing/useScrollToHash";

const SCROLL_MT = "scroll-mt-28";

const BENEFIT_CARDS = [
	{
		heading: "Team & Tournament Pages",
		description: "Public pages built to inform and convert supporters.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<rect x="4" y="4" width="16" height="16" rx="3" stroke="currentColor" strokeWidth="1.6" />
				<path d="M8 9h8M8 13h5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
			</svg>
		),
	},
	{
		heading: "Fundraising & Apparel",
		description: "Campaigns and merchandise tied to real program goals.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<path d="M12 20s-7-4.4-7-9.5A4.5 4.5 0 0 1 12 8a4.5 4.5 0 0 1 7 2.5C19 15.6 12 20 12 20Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		heading: "Dues & Family Credits",
		description: "Clear balances with approved opportunities to reduce fees.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.6" />
				<path d="M9 12h6M12 9v6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
			</svg>
		),
	},
	{
		heading: "Revenue Reporting",
		description: "One view of campaigns, orders, fees, and credits.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<path d="M5 19V9M12 19V5M19 19v-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
			</svg>
		),
	},
];

const STATS = [
	{
		value: "23%",
		label: "More revenue",
		copy: "Organizations see an average revenue increase within their first year.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<path d="M4 16 10 10l4 4 6-7M14 9h6v6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		value: "12+",
		label: "Hours saved weekly",
		copy: "Automate fee reminders, order tracking, and reporting busywork.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<circle cx="12" cy="12" r="8.5" stroke="currentColor" strokeWidth="1.8" />
				<path d="M12 7.5V12l3 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		value: "2x",
		label: "Stronger communication",
		copy: "Announcements and reminders that families actually see.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<circle cx="8" cy="9" r="2.6" stroke="currentColor" strokeWidth="1.8" />
				<circle cx="16.5" cy="9" r="2.6" stroke="currentColor" strokeWidth="1.8" />
				<path d="M2.8 18c.6-2.6 2.7-4.2 5.2-4.2s4.6 1.6 5.2 4.2M12.6 13.9c2.2.1 4.1 1.7 4.6 4.1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
			</svg>
		),
	},
	{
		value: "99.9%",
		label: "Reliable & secure",
		copy: "Built on the same infrastructure discipline as the payments it processes.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<path d="M12 3.5 19 6.5v5.2c0 4.6-3 8-7 9.3-4-1.3-7-4.7-7-9.3V6.5Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
				<path d="m9 12 2.2 2.2L15.5 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
];

const PLATFORM_MODULES = [
	{
		heading: "Fundraising",
		copy: "Run campaigns and sponsorships that maximize support.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<path d="M4 20h16M6 20V10l6-6 6 6v10" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		heading: "Fees & Payments",
		copy: "Collect dues online with flexible balances and plans.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<rect x="3" y="6" width="18" height="13" rx="2.2" stroke="currentColor" strokeWidth="1.6" />
				<path d="M3 10.5h18" stroke="currentColor" strokeWidth="1.6" />
			</svg>
		),
	},
	{
		heading: "Team Stores",
		copy: "Custom apparel stores that are easy to manage.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<path d="M8 4h8l1.5 4h-11z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
				<path d="M6.5 8h11L19 20H5z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		heading: "Sponsorships",
		copy: "Manage sponsors and showcase their impact publicly.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<path d="M12 3 4 6v6c0 5 3.5 7.5 8 9 4.5-1.5 8-4 8-9V6Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		heading: "Events & RSVP",
		copy: "Schedule events and manage RSVPs with ease.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<rect x="4" y="5" width="16" height="15" rx="2.2" stroke="currentColor" strokeWidth="1.6" />
				<path d="M4 10h16M8 3v4M16 3v4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
			</svg>
		),
	},
	{
		heading: "Reports & Insights",
		copy: "Real dashboards that track performance as it happens.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-6" aria-hidden="true">
				<path d="M5 19V9M12 19V5M19 19v-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
			</svg>
		),
	},
];

const HOW_IT_WORKS_STEPS = [
	{
		title: "Build",
		copy: "Create your organization, teams, tournaments, public pages, and revenue programs.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<path d="M4 20V10l8-6 8 6v10" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
				<path d="M9 20v-6h6v6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		title: "Share",
		copy: "Promote fundraisers, apparel, fees, and sponsor opportunities through links, QR codes, email, and your existing communication tools.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<circle cx="6" cy="12" r="2.3" stroke="currentColor" strokeWidth="1.7" />
				<circle cx="17.5" cy="6" r="2.3" stroke="currentColor" strokeWidth="1.7" />
				<circle cx="17.5" cy="18" r="2.3" stroke="currentColor" strokeWidth="1.7" />
				<path d="m8.1 10.9 7.4-3.6M8.1 13.1l7.4 3.6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
			</svg>
		),
	},
	{
		title: "Track",
		copy: "See confirmed activity, balances, orders, credits, and campaign performance in one place.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<path d="M4 20V10M10 20V4M16 20v-7M22 20H2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
	{
		title: "Reinvest",
		copy: "Use organization earnings and approved family credits to support stronger programs.",
		icon: (
			<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
				<path d="M12 20s-7-4.4-7-9.5A4.5 4.5 0 0 1 12 8a4.5 4.5 0 0 1 7 2.5C19 15.6 12 20 12 20Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
				<path d="M12 13V9m-1.6 1.6L12 9l1.6 1.6" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
		),
	},
];

function orgIcon(path: ReactNode) {
	return (
		<svg viewBox="0 0 24 24" fill="none" className="size-5" aria-hidden="true">
			{path}
		</svg>
	);
}

const ORGANIZATION_TYPE_CARDS = [
	{
		heading: "Leagues",
		copy: "Give every division and team a branded public presence while keeping fees and reporting in one place.",
		icon: orgIcon(<path d="M4 20V9l8-5 8 5v11M9 20v-6h6v6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />),
	},
	{
		heading: "Clubs",
		copy: "Run team and tournament pages, fundraising, and dues across multiple squads from one organization account.",
		icon: orgIcon(<path d="M12 3.5 19 6.5v5.2c0 4.6-3 8-7 9.3-4-1.3-7-4.7-7-9.3V6.5Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />),
	},
	{
		heading: "Teams",
		copy: "Give a single team a public page, a fundraiser, and a clear view of who owes what — without extra software.",
		icon: orgIcon(<><circle cx="12" cy="8" r="3.2" stroke="currentColor" strokeWidth="1.7" /><path d="M5 20c1-3.6 3.8-5.5 7-5.5s6 1.9 7 5.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" /></>),
	},
	{
		heading: "Tournaments",
		copy: "Promote your event, list participating teams and divisions, and give sponsors a professional page to point to.",
		icon: orgIcon(<path d="M8 4h8v3a4 4 0 0 1-4 4 4 4 0 0 1-4-4Zm4 7v4m-3 0h6m-6 4h6M4 5h4v2a3 3 0 0 1-3 3H4Zm16 0h-4v2a3 3 0 0 0 3 3h1Z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />),
	},
	{
		heading: "Booster Organizations",
		copy: "Track sponsorships, fundraising, and merchandise revenue that supports a program without a spreadsheet.",
		icon: orgIcon(<path d="M4 20h16M6 20V10l6-6 6 6v10" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />),
	},
	{
		heading: "Multisport Facilities",
		copy: "Give every program under your roof its own public page while keeping revenue reporting centralized.",
		icon: orgIcon(<path d="M4 4h7v7H4Zm9 0h7v7h-7ZM4 13h7v7H4Zm9 0h7v7h-7Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />),
	},
];

const ROLE_CARDS = [
	{
		heading: "Organization leaders",
		copy: "See every team, tournament, campaign, and fee balance across the organization in one place.",
		icon: orgIcon(<path d="M5 19V9M12 19V5M19 19v-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />),
	},
	{
		heading: "Team managers",
		copy: "Publish a team page and point families to fundraising, apparel, and fee information.",
		icon: orgIcon(<><rect x="4" y="4" width="16" height="16" rx="3" stroke="currentColor" strokeWidth="1.6" /><path d="M8 9h8M8 13h5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></>),
	},
	{
		heading: "Parents and guardians",
		copy: "See what's owed, pay fees, and track any organization-approved credits for your household.",
		icon: orgIcon(<><circle cx="9" cy="8" r="2.4" stroke="currentColor" strokeWidth="1.6" /><circle cx="16" cy="9" r="2" stroke="currentColor" strokeWidth="1.6" /><path d="M3.5 19c.6-3 3-4.8 5.5-4.8s4.9 1.8 5.5 4.8M14.8 14.6c2 .3 3.6 1.8 4.1 4.1" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></>),
	},
	{
		heading: "Supporters",
		copy: "Find a team or tournament page, contribute to a fundraiser, or shop apparel without an account.",
		icon: orgIcon(<path d="M12 20s-7-4.4-7-9.5A4.5 4.5 0 0 1 12 8a4.5 4.5 0 0 1 7 2.5C19 15.6 12 20 12 20Z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />),
	},
	{
		heading: "Tournament operators",
		copy: "Publish dates, divisions, and participating teams, then promote merchandise and sponsors.",
		icon: orgIcon(<><rect x="4" y="5" width="16" height="15" rx="2.2" stroke="currentColor" strokeWidth="1.6" /><path d="M4 10h16M8 3v4M16 3v4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></>),
	},
];

type PricingTier = {
	name: string;
	tone: "dark" | "light";
	badge?: string;
	price: string;
	cadence?: string;
	billingNote?: string;
	description: string;
	features: string[];
	ctaLabel: string;
	ctaTo: string;
};

/**
 * Three-tier pricing (added alongside the ADR-057 rebrand, replacing the earlier
 * Starter/Enterprise 2-tier layout). Starter and Growth carry real anchor prices;
 * Pro stays "Contact Us" rather than a fabricated enterprise number, consistent
 * with how the rest of the site avoids invented figures. Features still marked
 * "(planned)" match the same availability badges shown in the Solutions section
 * above — nothing here claims a planned feature ships today.
 */
const PRICING_TIERS: PricingTier[] = [
	{
		name: "Starter",
		tone: "dark",
		price: "$79",
		cadence: "/mo",
		billingNote: "Billed annually",
		description: "Perfect for a single team or small club just getting started.",
		features: [
			"Up to 3 teams",
			"Team & tournament pages",
			"Dues & fee collection",
			"Basic reporting",
			"Standard support",
			"Feature feedback access",
		],
		ctaLabel: "Start Free",
		ctaTo: "/auth/register",
	},
	{
		name: "Growth",
		tone: "dark",
		badge: "Most Popular",
		price: "$149",
		cadence: "/mo",
		billingNote: "Billed annually",
		description: "Built for growing clubs and leagues running multiple teams.",
		features: [
			"Up to 20 teams",
			"Everything in Starter",
			"Fundraising campaigns (planned)",
			"Team apparel stores (planned)",
			"Family credits (planned)",
			"Priority support",
			"Advanced reporting",
		],
		ctaLabel: "Start Free",
		ctaTo: "/auth/register",
	},
	{
		name: "Pro",
		tone: "light",
		price: "Contact Us",
		description: "For large leagues, tournament groups, and multisport operators.",
		features: [
			"Unlimited teams",
			"Everything in Growth",
			"Sponsorship management (planned)",
			"Custom onboarding",
			"Dedicated support",
			"Custom reporting",
		],
		ctaLabel: "Contact Us",
		ctaTo: "/contact",
	},
];

const ABOUT_VALUES = [
	"Stronger communities",
	"Clear financial reporting",
	"Less volunteer administration",
	"Responsible data use",
	"Adult-controlled youth information",
	"Honest product claims",
];

function CheckItem({ children }: { children: string }) {
	return (
		<li className="flex items-center gap-2">
			<svg className="size-4 shrink-0 text-green-600" viewBox="0 0 16 16" fill="none" aria-hidden="true">
				<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
			</svg>
			{children}
		</li>
	);
}

/**
 * Single-page homepage (section 7.1 of the sales-site redesign, ADR-057
 * rebrand). Replaces the old dropdown-menu, multi-route marketing IA — this
 * page now absorbs what used to be separate /how-it-works, /solutions,
 * /pricing, and /about routes as scroll sections navigated by HomeHeader's
 * anchor nav (see content/nav.ts's HOME_NAV_LINKS). Every other marketing
 * route (solution detail pages, help/legal/contact, public campaign/store/
 * sponsorship pages) still exists separately and keeps the dropdown-IA
 * SiteHeader via MarketingLayout.
 *
 * 2026-08-03: this was first built and reviewed at /landing-preview
 * alongside the old multi-page HomePage so the two could be compared; once
 * the founder confirmed the single-page layout, it was promoted here and the
 * superseded pages were deleted (see ADR-057).
 */
export function HomePage() {
	const scrollToPendingSection = usePendingHomeScroll();

	useEffect(() => {
		scrollToPendingSection();
	}, [scrollToPendingSection]);

	return (
		<div className="flex min-h-screen flex-col bg-ice-50">
			<Seo
				title="Rally26 | Revenue Tools for Youth Sports Organizations"
				description="Rally26 helps youth sports leagues, clubs, teams, and tournaments create public pages, run fundraisers, sell apparel, manage dues, and apply approved family fee credits."
			/>

			<AnnouncementBar />
			<HomeHeader />

			<main className="flex-1">
				<section
					id={HOMEPAGE_SECTION_IDS.hero}
					className={`relative overflow-hidden bg-[radial-gradient(circle_at_65%_35%,rgba(242,96,12,0.20),transparent_34%),linear-gradient(135deg,#061321_0%,#0B1F33_58%,#102B46_100%)] pb-20 pt-16 sm:pb-28 sm:pt-20 ${SCROLL_MT}`}
				>
					<PageContainer className="relative grid gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
						<div>
							<p className="inline-flex items-center gap-1.5 rounded-full bg-orange-500 px-3 py-1 text-xs font-bold uppercase tracking-wide text-white">
								<svg className="size-3.5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
									<path d="M10 1.5 12.4 7l6 .6-4.5 4 1.3 5.9L10 14.6l-5.2 2.9L6.1 11.6l-4.5-4 6-.6Z" />
								</svg>
								Built for youth sports organizations
							</p>
							<h1 className="mt-5 text-balance font-heading text-4xl font-extrabold leading-[1.08] text-white sm:text-5xl lg:text-[64px]">
								More revenue.
								<br />
								Lower fees.
								<br />
								<span className="text-orange-400">Stronger programs.</span>
							</h1>
							<p className="mt-6 max-w-xl text-lg leading-relaxed text-slate-300">
								Rally26 helps youth sports organizations create public team and tournament pages, run
								fundraisers, sell apparel, manage dues, and apply approved sales-based credits to family
								fees.
							</p>
							<div className="mt-8 flex flex-wrap items-center gap-4">
								<PrimaryButton to="/auth/register" icon="arrow" onClick={() => track("hero_cta_clicked")}>
									Get Started
								</PrimaryButton>
								<SecondaryDarkButton
									onClick={() => {
										track("hero_how_it_works_clicked");
										document.getElementById(HOMEPAGE_SECTION_IDS.howItWorks)?.scrollIntoView({ behavior: "smooth", block: "start" });
									}}
								>
									See How It Works
								</SecondaryDarkButton>
							</div>
							<TextButton to="/auth/sign-in" className="mt-6 inline-block text-slate-300 hover:text-white">
								Log In
							</TextButton>
						</div>

						<div className="relative">
							<div className="pointer-events-none absolute -inset-10 rounded-full bg-orange-500/25 blur-3xl" aria-hidden="true" />
							<div className="relative overflow-hidden" style={{ clipPath: "polygon(9% 0%, 100% 0%, 100% 100%, 0% 100%)" }}>
								<img
									src={heroImages.multisportHuddle}
									alt="Athletes from hockey, soccer, baseball, and basketball standing together in a stadium under orange stadium lights"
									className="aspect-[4/3] w-full object-cover sm:aspect-video lg:aspect-[4/3]"
								/>
								<div
									className="pointer-events-none absolute inset-0"
									style={{
										background:
											"linear-gradient(100deg, rgba(6,19,33,0.85) 0%, transparent 20%), linear-gradient(0deg, rgba(6,19,33,0.6) 0%, transparent 40%)",
									}}
								/>
							</div>
						</div>
					</PageContainer>

					<PageContainer className="relative mt-16 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
						{BENEFIT_CARDS.map((card) => (
							<FeatureCard key={card.heading} icon={card.icon} heading={card.heading} description={card.description} />
						))}
					</PageContainer>
				</section>

				<section className="border-b border-slate-200 bg-white py-12">
					<PageContainer className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
						{STATS.map((stat) => (
							<div key={stat.label} className="flex flex-col gap-3 rounded-2xl border border-slate-200 p-5">
								<span className="flex size-10 items-center justify-center rounded-full bg-navy-900 text-orange-400">{stat.icon}</span>
								<div>
									<p className="font-heading text-2xl font-extrabold text-orange-500 sm:text-3xl">{stat.value}</p>
									<p className="font-heading text-sm font-bold text-navy-900">{stat.label}</p>
								</div>
								<p className="text-sm leading-relaxed text-slate-500">{stat.copy}</p>
							</div>
						))}
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.solutions} className={`bg-ice-50 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-12">
						<SectionHeading
							eyebrow="Solutions"
							heading="Everything your program's revenue side needs."
							copy="From one team to a multi-division tournament, Rally26 gives adult administrators clear tools for public pages, fundraising, apparel, fees, credits, and reporting."
						/>
						<div className="grid gap-5 sm:grid-cols-2">
							{SOLUTIONS.map((solution) => (
								<div
									key={solution.slug}
									className="flex flex-col justify-between rounded-[22px] border border-white/[0.12] bg-navy-900 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.22)]"
								>
									<div>
										<div className="flex flex-wrap items-start justify-between gap-3">
											<h3 className="font-heading text-xl font-bold text-white">{solution.heading}</h3>
											<AvailabilityStatusBadge status={solution.availability} />
										</div>
										<p className="mt-3 text-sm leading-relaxed text-slate-300">{solution.shortDescription}</p>
									</div>
									<TextButton to={`/solutions/${solution.slug}`} icon="arrow" className="mt-6 self-start text-orange-400 hover:text-orange-300">
										Explore {solution.navLabel}
									</TextButton>
								</div>
							))}
						</div>
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.platform} className={`bg-white py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col items-center gap-12 text-center">
						<SectionHeading heading="One platform. Everything." copy="Every revenue tool your organization needs, connected — not six separate logins." />
						<div className="grid w-full gap-5 sm:grid-cols-2 lg:grid-cols-3">
							{PLATFORM_MODULES.map((module) => (
								<div key={module.heading} className="flex flex-col items-start gap-3 rounded-[18px] border border-slate-200 bg-ice-50 p-6 text-left">
									<span className="flex size-11 items-center justify-center rounded-xl bg-orange-500/10 text-orange-500">{module.icon}</span>
									<h3 className="font-heading text-base font-bold text-navy-900">{module.heading}</h3>
									<p className="text-sm leading-relaxed text-slate-600">{module.copy}</p>
								</div>
							))}
						</div>
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.howItWorks} className={`bg-navy-950 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-16">
						<div className="flex flex-col gap-12">
							<SectionHeading tone="dark" heading="How Rally26 Works" />
							<StepTimeline steps={HOW_IT_WORKS_STEPS} />
						</div>

						<ResponsiveVisual
							src="/demo-assets/landing/landing-page-vis-2.png"
							alt="Diagram showing the Rally26 mark at the center, connected to Campaigns, Fees & Payments, Team Stores, Store Orders, Communications, and Reports & Insights — one connected platform."
							width={1536}
							height={1024}
						/>

						<ResponsiveVisual
							src="/demo-assets/landing/landing-page-vis-1.png"
							alt="The Rally26 Workflow: a club or tournament sets up Rally26, creates team pages and stores, launches fees, fundraising, and apparel, families and supporters pay or purchase, orders and notifications flow through integrated tools like Stripe and Printify, and revenue, credits, and reporting flow back to the organization."
							width={1672}
							height={941}
						/>
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.audiences} className={`bg-white py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-16">
						<div className="flex flex-col gap-8">
							<SectionHeading heading="Built for the organizations that make youth sports possible." />
							<div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
								{ORGANIZATION_TYPE_CARDS.map((card) => (
									<AudienceCard key={card.heading} {...card} />
								))}
							</div>
						</div>

						<div className="flex flex-col gap-8">
							<SectionHeading align="left" heading="Built around every role in your organization." />
							<div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
								{ROLE_CARDS.map((card) => (
									<AudienceCard key={card.heading} {...card} />
								))}
							</div>
						</div>
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.pricing} className={`bg-ice-50 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-12">
						<SectionHeading heading="Simple pricing. Real transparency." copy="Rally26 combines organization subscriptions with clearly disclosed transaction fees and optional implementation services. Features marked “planned” are on the roadmap, not live yet." />

						<div className="grid gap-8 pt-3 lg:grid-cols-3 lg:items-start">
							{PRICING_TIERS.map((tier) => (
								<div
									key={tier.name}
									className={`relative flex h-full flex-col rounded-[22px] p-8 ${
										tier.tone === "dark"
											? tier.badge
												? "border-2 border-orange-500 bg-navy-900 text-white shadow-[0_28px_70px_rgba(242,96,12,0.28)] lg:-translate-y-3"
												: "border border-orange-500/30 bg-navy-900 text-white"
											: "border border-slate-200 bg-white text-navy-900"
									}`}
								>
									{tier.badge && (
										<span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-orange-500 px-3 py-1 text-xs font-bold uppercase tracking-wide text-white">
											{tier.badge}
										</span>
									)}
									<p className={`font-heading text-xs font-semibold uppercase tracking-wide ${tier.tone === "dark" ? "text-orange-400" : "text-slate-500"}`}>
										{tier.name}
									</p>
									<p className="mt-3 flex items-baseline gap-1 font-heading text-3xl font-extrabold">
										{tier.price}
										{tier.cadence && <span className="text-base font-medium">{tier.cadence}</span>}
									</p>
									{tier.billingNote && <p className={`mt-1 text-xs ${tier.tone === "dark" ? "text-slate-400" : "text-slate-500"}`}>{tier.billingNote}</p>}
									<p className={`mt-3 text-sm ${tier.tone === "dark" ? "text-slate-300" : "text-slate-700"}`}>{tier.description}</p>
									<ul className={`mt-6 flex flex-col gap-2 text-sm ${tier.tone === "dark" ? "text-slate-200" : "text-slate-700"}`}>
										{tier.features.map((item) => (
											<CheckItem key={item}>{item}</CheckItem>
										))}
									</ul>
									{tier.tone === "dark" ? (
										<PrimaryButton to={tier.ctaTo} icon="arrow" className="mt-8 w-full justify-center">
											{tier.ctaLabel}
										</PrimaryButton>
									) : (
										<SecondaryLightButton to={tier.ctaTo} className="mt-8 w-full justify-center">
											{tier.ctaLabel}
										</SecondaryLightButton>
									)}
								</div>
							))}
						</div>

						<p className="mx-auto max-w-2xl text-center text-sm text-slate-500">
							Applicable transaction, payment-processing, fulfillment, and optional service fees are disclosed before launch and are never bundled into the base subscription price.
						</p>

						<div className="mx-auto w-full max-w-3xl">
							<FaqAccordion items={PRICING_FAQ} />
						</div>
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.about} className={`bg-white py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="mx-auto flex max-w-3xl flex-col gap-10">
						<SectionHeading align="left" heading="Better revenue tools for the people who keep youth sports running." />
						<div>
							<h3 className="font-heading text-xl font-bold text-navy-900">The founding idea</h3>
							<p className="mt-3 leading-relaxed text-slate-700">
								Rally26 was created around a simple idea: youth sports organizations should have better ways to
								generate and manage revenue than repeatedly raising family fees or relying on already-busy
								volunteers.
							</p>
						</div>
						<div>
							<h3 className="font-heading text-xl font-bold text-navy-900">Mission</h3>
							<p className="mt-3 leading-relaxed text-slate-700">
								Help youth sports organizations build sustainable programs while making costs clearer and more
								manageable for families.
							</p>
						</div>
						<div>
							<h3 className="font-heading text-xl font-bold text-navy-900">Values</h3>
							<ul className="mt-4 grid gap-3 sm:grid-cols-2">
								{ABOUT_VALUES.map((value) => (
									<li key={value} className="flex items-center gap-2 text-slate-700">
										<svg className="size-4 shrink-0 text-green-600" viewBox="0 0 16 16" fill="none" aria-hidden="true">
											<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
										</svg>
										{value}
									</li>
								))}
							</ul>
						</div>
					</PageContainer>
				</section>

				<section id={HOMEPAGE_SECTION_IDS.faq} className={`bg-ice-50 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-10">
						<SectionHeading heading="Frequently asked questions" />
						<div className="mx-auto w-full max-w-3xl">
							<FaqAccordion items={HOMEPAGE_FAQ} />
						</div>
					</PageContainer>
				</section>

				<section
					className="relative overflow-hidden bg-navy-950 py-20 sm:py-28"
					style={{
						backgroundImage: `linear-gradient(90deg, rgba(6,19,33,0.88) 0%, rgba(6,19,33,0.55) 45%, rgba(242,96,12,0.30) 100%), url(${heroImages.stadiumCta})`,
						backgroundSize: "cover",
						backgroundPosition: "center",
					}}
				>
					<PageContainer className="relative flex flex-col items-center gap-6 text-center">
						<h2 className="max-w-2xl text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">
							Raise more. Manage less. Build stronger programs.
						</h2>
						<p className="max-w-xl text-lg text-slate-200">
							Get started with Rally26 and build a stronger revenue program for your organization.
						</p>
						<div className="flex flex-wrap items-center justify-center gap-4">
							<PrimaryButton to="/auth/register" icon="arrow" onClick={() => track("final_cta_clicked")}>
								Get Started
							</PrimaryButton>
							<SecondaryDarkButton to="/book-demo">Book a Demo</SecondaryDarkButton>
						</div>
					</PageContainer>
				</section>
			</main>

			<SiteFooter />
		</div>
	);
}
