import { useEffect } from "react";
import { AnnouncementBar } from "../../marketing/components/AnnouncementBar";
import { AudienceCard } from "../../marketing/components/AudienceCard";
import { BentoCard } from "../../marketing/components/BentoCard";
import { FaqAccordion } from "../../marketing/components/FaqAccordion";
import { FeatureCard } from "../../marketing/components/FeatureCard";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { StepTimeline } from "../../marketing/components/StepTimeline";
import { PrimaryButton, SecondaryDarkButton, TextButton } from "../../marketing/components/buttons";
import { HOMEPAGE_SECTION_IDS } from "../../marketing/content/nav";
import { HOMEPAGE_FAQ } from "../../marketing/content/faq";
import { heroImages } from "../../marketing/heroImages";
import { track } from "../../marketing/analytics";
import { usePendingHomeScroll } from "../../marketing/useScrollToHash";

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

const BENTO_CARDS = [
	{
		heading: "Team & Tournament Pages",
		copy: "Give each team or tournament a branded home for updates, fundraising, apparel, sponsors, and supporter links.",
		actionLabel: "Explore Team & Tournament Pages",
		to: "/solutions/team-pages",
	},
	{
		heading: "Fundraising",
		copy: "Launch organization- or team-specific campaigns with goals, sharing links, QR codes, and attribution.",
		actionLabel: "Explore Fundraising",
		to: "/solutions/fundraising",
		badge: "Planned",
	},
	{
		heading: "Apparel Stores",
		copy: "Sell organization, team, season, and tournament merchandise without requiring large inventory purchases.",
		actionLabel: "Explore Apparel",
		to: "/solutions/apparel",
		badge: "Planned",
	},
	{
		heading: "Dues & Fees",
		copy: "Assign, collect, and track registration fees, team dues, tournament costs, uniforms, travel, and more.",
		actionLabel: "Explore Dues & Fees",
		to: "/solutions/dues-and-fees",
	},
	{
		heading: "Family Credits",
		copy: "Apply organization-approved sales and fundraising credits to eligible family fees.",
		actionLabel: "Explore Family Credits",
		to: "/solutions/family-credits",
		badge: "Planned for the full platform",
	},
	{
		heading: "Sponsorships",
		copy: "Create professional packages for local businesses and track fulfillment and renewals.",
		actionLabel: "Explore Sponsorships",
		to: "/solutions/sponsorships",
		badge: "Planned after the initial pilot",
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

const AUDIENCE_CARDS = [
	{ heading: "Leagues", copy: "Give every division and team a branded public presence while keeping fees and reporting in one place." },
	{ heading: "Clubs", copy: "Run team and tournament pages, fundraising, and dues across multiple squads from one organization account." },
	{ heading: "Teams", copy: "Give a single team a public page, a fundraiser, and a clear view of who owes what — without extra software." },
	{ heading: "Tournaments", copy: "Promote your event, list participating teams and divisions, and give sponsors a professional page to point to." },
	{ heading: "Booster Organizations", copy: "Track sponsorships, fundraising, and merchandise revenue that supports a program without a spreadsheet." },
	{ heading: "Multisport Facilities", copy: "Give every program under your roof its own public page while keeping revenue reporting centralized." },
];

const PRICING_INCLUDES = [
	"Guided onboarding",
	"Organization profile",
	"Team and tournament page setup",
	"Initial fundraising workflow",
	"Initial apparel workflow as available",
	"Basic reporting",
	"Direct feedback channel",
];

export function HomePage() {
	const scrollToPendingSection = usePendingHomeScroll();

	useEffect(() => {
		scrollToPendingSection();
	}, [scrollToPendingSection]);

	return (
		<>
			<Seo
				title="LeagueLift | Revenue Tools for Youth Sports Organizations"
				description="LeagueLift helps youth sports leagues, clubs, teams, and tournaments create public pages, run fundraisers, sell apparel, manage dues, and apply approved family fee credits."
			/>
			<AnnouncementBar />

			<section
				id={HOMEPAGE_SECTION_IDS.hero}
				className="relative overflow-hidden bg-[radial-gradient(circle_at_65%_35%,rgba(32,178,107,0.18),transparent_34%),linear-gradient(135deg,#061321_0%,#0B1F33_58%,#102B46_100%)] pb-20 pt-16 sm:pb-28 sm:pt-20"
			>
				<PageContainer className="relative grid gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
					<div>
						<p className="inline-flex items-center gap-1.5 rounded-full bg-green-500 px-3 py-1 text-xs font-bold uppercase tracking-wide text-navy-950">
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
							<span className="text-green-400">Stronger programs.</span>
						</h1>
						<p className="mt-6 max-w-xl text-lg leading-relaxed text-slate-300">
							LeagueLift helps youth sports organizations create public team and tournament pages, run
							fundraisers, sell apparel, manage dues, and apply approved sales-based credits to family
							fees.
						</p>
						<div className="mt-8 flex flex-wrap items-center gap-4">
							<PrimaryButton
								to="/auth/register"
								icon="arrow"
								onClick={() => track("hero_cta_clicked")}
							>
								Get Started
							</PrimaryButton>
							<SecondaryDarkButton to="/how-it-works" onClick={() => track("hero_how_it_works_clicked")}>
								See How It Works
							</SecondaryDarkButton>
						</div>
						<TextButton to="/auth/sign-in" className="mt-6 inline-block text-slate-300 hover:text-white">
							Log In
						</TextButton>
					</div>

					<div className="relative">
						<div
							className="pointer-events-none absolute -inset-10 rounded-full bg-green-500/25 blur-3xl"
							aria-hidden="true"
						/>
						<div
							className="relative overflow-hidden"
							style={{ clipPath: "polygon(9% 0%, 100% 0%, 100% 100%, 0% 100%)" }}
						>
							<img
								src={heroImages.multisportHuddle}
								alt="Athletes from soccer, hockey, baseball, and basketball standing together in a stadium"
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

			<section className="bg-white py-20 sm:py-28">
				<PageContainer className="grid gap-12 lg:grid-cols-2 lg:items-center">
					<SectionHeading
						align="left"
						heading="Your sports software runs the season. LeagueLift helps fund it."
						copy="Keep the tools you use for registration, scheduling, and team communication. LeagueLift adds public pages, commerce, fundraising, dues, credits, and revenue reporting."
					/>
					<div className="grid gap-6 sm:grid-cols-2">
						<div className="rounded-2xl border border-slate-200 bg-ice-50 p-6">
							<h3 className="font-heading text-sm font-semibold uppercase tracking-wide text-slate-500">
								Existing Sports Tools
							</h3>
							<ul className="mt-4 flex flex-col gap-2 text-sm text-slate-700">
								{["Registration", "Scheduling", "Rosters", "Communication"].map((item) => (
									<li key={item}>{item}</li>
								))}
							</ul>
						</div>
						<div className="rounded-2xl border border-green-500/30 bg-navy-900 p-6 text-white">
							<h3 className="font-heading text-sm font-semibold uppercase tracking-wide text-green-400">LeagueLift</h3>
							<ul className="mt-4 flex flex-col gap-2 text-sm text-slate-200">
								{[
									"Public team and tournament pages",
									"Fundraising",
									"Apparel stores",
									"Dues and fee tracking",
									"Family credits",
									"Revenue reporting",
								].map((item) => (
									<li key={item}>{item}</li>
								))}
							</ul>
						</div>
					</div>
				</PageContainer>
			</section>

			<section id={HOMEPAGE_SECTION_IDS.features} className="bg-ice-50 py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-12">
					<SectionHeading eyebrow="Features" heading="Everything your program's revenue side needs." />
					<div className="grid gap-5 sm:grid-cols-2">
						{BENTO_CARDS.map((card) => (
							<BentoCard key={card.heading} {...card} />
						))}
					</div>
				</PageContainer>
			</section>

			<section id={HOMEPAGE_SECTION_IDS.howItWorks} className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-12">
					<SectionHeading tone="dark" heading="How LeagueLift Works" />
					<StepTimeline steps={HOW_IT_WORKS_STEPS} />
				</PageContainer>
			</section>

			<section className="bg-white py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-12">
					<SectionHeading heading="Built for the organizations that make youth sports possible." />
					<div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
						{AUDIENCE_CARDS.map((card) => (
							<AudienceCard key={card.heading} {...card} />
						))}
					</div>
				</PageContainer>
			</section>

			<section id={HOMEPAGE_SECTION_IDS.pricing} className="bg-ice-50 py-20 sm:py-28">
				<PageContainer className="flex flex-col items-center gap-8 text-center">
					<SectionHeading heading="Simple, transparent pricing" />
					<p className="font-heading text-3xl font-extrabold text-navy-900">
						Starting at $149 per month, plus applicable transaction fees.
					</p>
					<ul className="grid gap-2 text-sm text-slate-700 sm:grid-cols-2">
						{PRICING_INCLUDES.map((item) => (
							<li key={item} className="flex items-center gap-2">
								<svg className="size-4 shrink-0 text-green-600" viewBox="0 0 16 16" fill="none" aria-hidden="true">
									<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
								</svg>
								{item}
							</li>
						))}
					</ul>
					<PrimaryButton to="/pricing" icon="arrow">
						Review Pricing Details
					</PrimaryButton>
					<p className="max-w-lg text-sm text-slate-500">
						Availability depends on your organization&rsquo;s needs, launch timing, and supported
						workflows.
					</p>
				</PageContainer>
			</section>

			<section id={HOMEPAGE_SECTION_IDS.faq} className="bg-white py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-10">
					<SectionHeading heading="Frequently asked questions" />
					<div className="mx-auto w-full max-w-3xl">
						<FaqAccordion items={HOMEPAGE_FAQ} />
					</div>
				</PageContainer>
			</section>

			<section className="bg-[radial-gradient(circle_at_75%_50%,rgba(32,178,107,0.20),transparent_30%),linear-gradient(90deg,#061321_0%,#0B1F33_62%,#0C3A2A_100%)] py-20 sm:py-28">
				<PageContainer className="flex flex-col items-center gap-6 text-center">
					<h2 className="max-w-2xl text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">
						Raise more. Manage less. Build stronger programs.
					</h2>
					<p className="max-w-xl text-lg text-slate-300">
						Get started with LeagueLift and build a stronger revenue program for your organization.
					</p>
					<div className="flex flex-wrap items-center justify-center gap-4">
						<PrimaryButton to="/auth/register" icon="arrow" onClick={() => track("final_cta_clicked")}>
							Get Started
						</PrimaryButton>
						<SecondaryDarkButton to="/book-demo">Book a Demo</SecondaryDarkButton>
					</div>
				</PageContainer>
			</section>
		</>
	);
}
