import { Link } from "react-router-dom";
import { AudienceCard } from "../../marketing/components/AudienceCard";
import { AvailabilityStatusBadge } from "../../marketing/components/Badges";
import { FaqAccordion } from "../../marketing/components/FaqAccordion";
import { FeatureCard } from "../../marketing/components/FeatureCard";
import { LandingPreviewHeader } from "../../marketing/components/LandingPreviewHeader";
import { PageContainer } from "../../marketing/components/PageContainer";
import { ResponsiveVisual } from "../../marketing/components/ResponsiveVisual";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { SiteFooter } from "../../marketing/components/SiteFooter";
import { StepTimeline } from "../../marketing/components/StepTimeline";
import { PrimaryButton, SecondaryDarkButton, SecondaryLightButton, TextButton } from "../../marketing/components/buttons";
import { PREVIEW_SECTION_IDS } from "../../marketing/content/landingPreviewNav";
import { HOMEPAGE_FAQ } from "../../marketing/content/faq";
import { PRICING_FAQ } from "../../marketing/content/pricing";
import { SOLUTIONS } from "../../marketing/content/solutions";
import { heroImages } from "../../marketing/heroImages";
import { track } from "../../marketing/analytics";

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

const ORGANIZATION_TYPE_CARDS = [
	{ heading: "Leagues", copy: "Give every division and team a branded public presence while keeping fees and reporting in one place." },
	{ heading: "Clubs", copy: "Run team and tournament pages, fundraising, and dues across multiple squads from one organization account." },
	{ heading: "Teams", copy: "Give a single team a public page, a fundraiser, and a clear view of who owes what — without extra software." },
	{ heading: "Tournaments", copy: "Promote your event, list participating teams and divisions, and give sponsors a professional page to point to." },
	{ heading: "Booster Organizations", copy: "Track sponsorships, fundraising, and merchandise revenue that supports a program without a spreadsheet." },
	{ heading: "Multisport Facilities", copy: "Give every program under your roof its own public page while keeping revenue reporting centralized." },
];

const ROLE_CARDS = [
	{ heading: "Organization leaders", copy: "See every team, tournament, campaign, and fee balance across the organization in one place." },
	{ heading: "Team managers", copy: "Publish a team page and point families to fundraising, apparel, and fee information." },
	{ heading: "Parents and guardians", copy: "See what's owed, pay fees, and track any organization-approved credits for your household." },
	{ heading: "Supporters", copy: "Find a team or tournament page, contribute to a fundraiser, or shop apparel without an account." },
	{ heading: "Tournament operators", copy: "Publish dates, divisions, and participating teams, then promote merchandise and sponsors." },
];

const STARTER_INCLUDES = [
	"Organization account",
	"Adult administrator access",
	"Organization onboarding",
	"Team and tournament pages",
	"Initial campaign setup",
	"Basic reporting",
	"Standard support",
	"Feature feedback access",
];

const ENTERPRISE_FITS = [
	"Large leagues",
	"Tournament groups",
	"Multisport operators",
	"More than 1,000 participants",
	"Multiple legal entities",
	"Custom reporting needs",
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
 * Single-page redesign preview, mounted at /landing-preview alongside the real
 * marketing site so the two can be compared before committing to a rewrite.
 * Consolidates HomePage + HowItWorksPage + SolutionsOverviewPage + PricingPage +
 * AboutPage into one scrollable narrative navigated by anchor links (see
 * LandingPreviewHeader) instead of the current dropdown-menu IA. Nothing in the
 * live site (routes, nav, HomePage) is touched by this file.
 */
export function LandingPreviewPage() {
	return (
		<div className="flex min-h-screen flex-col bg-ice-50">
			<Seo
				title="LeagueLift | Single-Page Preview"
				description="Preview build: LeagueLift's marketing site consolidated into one scrollable page with anchor navigation."
				noIndex
			/>

			<div className="bg-gold-500 py-2 text-center text-sm font-semibold text-navy-950">
				Preview build — a single-page layout for comparison.{" "}
				<Link to="/" className="underline underline-offset-2 hover:no-underline">
					View the current site
				</Link>
			</div>

			<LandingPreviewHeader />

			<main className="flex-1">
				<section
					id={PREVIEW_SECTION_IDS.hero}
					className={`relative overflow-hidden bg-[radial-gradient(circle_at_65%_35%,rgba(32,178,107,0.18),transparent_34%),linear-gradient(135deg,#061321_0%,#0B1F33_58%,#102B46_100%)] pb-20 pt-16 sm:pb-28 sm:pt-20 ${SCROLL_MT}`}
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
								<PrimaryButton to="/auth/register" icon="arrow" onClick={() => track("hero_cta_clicked")}>
									Get Started
								</PrimaryButton>
								<SecondaryDarkButton
									onClick={() => {
										track("hero_how_it_works_clicked");
										document.getElementById(PREVIEW_SECTION_IDS.howItWorks)?.scrollIntoView({ behavior: "smooth", block: "start" });
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
							<div className="pointer-events-none absolute -inset-10 rounded-full bg-green-500/25 blur-3xl" aria-hidden="true" />
							<div className="relative overflow-hidden" style={{ clipPath: "polygon(9% 0%, 100% 0%, 100% 100%, 0% 100%)" }}>
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

				<section id={PREVIEW_SECTION_IDS.solutions} className={`bg-ice-50 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-12">
						<SectionHeading
							eyebrow="Solutions"
							heading="Everything your program's revenue side needs."
							copy="From one team to a multi-division tournament, LeagueLift gives adult administrators clear tools for public pages, fundraising, apparel, fees, credits, and reporting."
						/>
						<div className="grid gap-5 sm:grid-cols-2">
							{SOLUTIONS.map((solution) => (
								<div
									key={solution.slug}
									className="flex flex-col justify-between rounded-[22px] border border-white/[0.12] bg-navy-900 p-7 shadow-[0_22px_60px_rgba(0,0,0,0.22)]"
								>
									<div>
										<div className="flex items-start justify-between gap-3">
											<h3 className="font-heading text-xl font-bold text-white">{solution.heading}</h3>
											<AvailabilityStatusBadge status={solution.availability} />
										</div>
										<p className="mt-3 text-sm leading-relaxed text-slate-300">{solution.shortDescription}</p>
									</div>
									<TextButton to={`/solutions/${solution.slug}`} icon="arrow" className="mt-6 self-start text-green-400 hover:text-green-300">
										Explore {solution.navLabel}
									</TextButton>
								</div>
							))}
						</div>
					</PageContainer>
				</section>

				<section id={PREVIEW_SECTION_IDS.howItWorks} className={`bg-navy-950 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-16">
						<div className="flex flex-col gap-12">
							<SectionHeading tone="dark" heading="How LeagueLift Works" />
							<StepTimeline steps={HOW_IT_WORKS_STEPS} />
						</div>

						<ResponsiveVisual
							src="/demo-assets/landing/landing-page-vis-2.png"
							alt="Diagram showing LeagueLift at the center, connected to MaxPreps, GameChanger, SportsEngine, Google Calendar, Twilio, Stripe, and Printify — works alongside your existing tools."
							width={1672}
							height={941}
						/>

						<ResponsiveVisual
							src="/demo-assets/landing/landing-page-vis-1.png"
							alt="The detailed LeagueLift workflow: a club or tournament sets up LeagueLift, creates team pages and stores, launches dues/fundraising/apparel, parents and supporters pay or purchase, orders and notifications flow through Stripe and Printify, and revenue/credits/reporting flow back to the organization."
							width={1672}
							height={941}
						/>
					</PageContainer>
				</section>

				<section id={PREVIEW_SECTION_IDS.audiences} className={`bg-white py-20 sm:py-28 ${SCROLL_MT}`}>
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

				<section id={PREVIEW_SECTION_IDS.pricing} className={`bg-ice-50 py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="flex flex-col gap-12">
						<SectionHeading heading="Simple, transparent pricing" copy="LeagueLift combines organization subscriptions with clearly disclosed transaction fees and optional implementation services." />

						<div className="grid gap-8 lg:grid-cols-2">
							<div className="rounded-[22px] border border-green-500/30 bg-navy-900 p-8 text-white">
								<p className="font-heading text-xs font-semibold uppercase tracking-wide text-green-400">Starter</p>
								<p className="mt-3 font-heading text-3xl font-extrabold">Starting at $149 per month</p>
								<p className="mt-2 text-sm text-slate-300">
									Applicable transaction, payment-processing, fulfillment, and optional service fees are disclosed before launch.
								</p>
								<ul className="mt-6 grid gap-2 text-sm text-slate-200 sm:grid-cols-2">
									{STARTER_INCLUDES.map((item) => (
										<CheckItem key={item}>{item}</CheckItem>
									))}
								</ul>
								<PrimaryButton to="/auth/register" icon="arrow" className="mt-8 w-full justify-center">
									Get Started
								</PrimaryButton>
							</div>

							<div className="rounded-[22px] border border-slate-200 bg-white p-8">
								<p className="font-heading text-xs font-semibold uppercase tracking-wide text-slate-500">Enterprise & Large Organizations</p>
								<p className="mt-3 font-heading text-3xl font-extrabold text-navy-900">Contact Us</p>
								<p className="mt-2 text-sm text-slate-700">Suitable for:</p>
								<ul className="mt-6 grid gap-2 text-sm text-slate-700 sm:grid-cols-2">
									{ENTERPRISE_FITS.map((item) => (
										<CheckItem key={item}>{item}</CheckItem>
									))}
								</ul>
								<SecondaryLightButton to="/contact" className="mt-8 w-full justify-center">
									Contact Us
								</SecondaryLightButton>
							</div>
						</div>

						<div className="mx-auto w-full max-w-3xl">
							<FaqAccordion items={PRICING_FAQ} />
						</div>
					</PageContainer>
				</section>

				<section id={PREVIEW_SECTION_IDS.about} className={`bg-white py-20 sm:py-28 ${SCROLL_MT}`}>
					<PageContainer className="mx-auto flex max-w-3xl flex-col gap-10">
						<SectionHeading align="left" heading="Better revenue tools for the people who keep youth sports running." />
						<div>
							<h3 className="font-heading text-xl font-bold text-navy-900">The founding idea</h3>
							<p className="mt-3 leading-relaxed text-slate-700">
								LeagueLift was created around a simple idea: youth sports organizations should have better ways to
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

				<section id={PREVIEW_SECTION_IDS.faq} className={`bg-ice-50 py-20 sm:py-28 ${SCROLL_MT}`}>
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
			</main>

			<SiteFooter />
		</div>
	);
}
