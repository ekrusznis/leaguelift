import { AudienceCard } from "../../marketing/components/AudienceCard";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";

const WORKFLOW_STEPS = [
	"Create the organization",
	"Add teams and tournaments",
	"Publish public pages",
	"Create fundraising or apparel programs",
	"Add households and assign fees",
	"Attribute eligible sales or contributions",
	"Apply approved credits",
	"Review reports and payouts",
];

const ROLE_CARDS = [
	{ heading: "Organization leaders", copy: "See every team, tournament, campaign, and fee balance across the organization in one place." },
	{ heading: "Team managers", copy: "Publish a team page and point families to fundraising, apparel, and fee information." },
	{ heading: "Parents and guardians", copy: "See what's owed, pay fees, and track any organization-approved credits for your household." },
	{ heading: "Supporters", copy: "Find a team or tournament page, contribute to a fundraiser, or shop apparel without an account." },
	{ heading: "Tournament operators", copy: "Publish dates, divisions, and participating teams, then promote merchandise and sponsors." },
];

export function HowItWorksPage() {
	return (
		<>
			<Seo
				title="How It Works"
				description="Create public pages, launch campaigns, sell apparel, manage fees, and track approved family credits without replacing your scheduling or registration tools."
			/>

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-3xl">
					<SectionHeading
						tone="dark"
						align="left"
						heading="One platform for the revenue surrounding your program."
						copy="Create public pages, launch campaigns, sell apparel, manage fees, and track approved family credits without replacing your scheduling or registration tools."
					/>
				</PageContainer>
			</section>

			<section className="bg-white py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-10">
					<SectionHeading heading="The detailed workflow" align="left" />
					<ol className="grid gap-6 sm:grid-cols-2">
						{WORKFLOW_STEPS.map((step, index) => (
							<li key={step} className="flex items-start gap-4 rounded-2xl border border-slate-200 bg-ice-50 p-5">
								<span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-green-500/15 font-heading text-sm font-bold text-green-600">
									{index + 1}
								</span>
								<p className="font-medium text-navy-900">{step}</p>
							</li>
						))}
					</ol>
				</PageContainer>
			</section>

			<section className="bg-ice-50 py-20 sm:py-28">
				<PageContainer className="grid gap-8 lg:grid-cols-2 lg:items-center">
					<SectionHeading
						align="left"
						heading="Works alongside your existing tools."
						copy="LeagueLift is not a registration, scheduling, or communication system. It adds a revenue layer next to the tools you already use."
					/>
					<div className="grid gap-4 sm:grid-cols-2">
						<div className="rounded-2xl border border-slate-200 bg-white p-6">
							<h3 className="font-heading text-sm font-semibold uppercase tracking-wide text-slate-500">Your existing tools</h3>
							<ul className="mt-4 flex flex-col gap-2 text-sm text-slate-700">
								<li>Registration software</li>
								<li>Scheduling software</li>
								<li>Communication software</li>
							</ul>
						</div>
						<div className="rounded-2xl border border-green-500/30 bg-navy-900 p-6 text-white">
							<h3 className="font-heading text-sm font-semibold uppercase tracking-wide text-green-400">LeagueLift</h3>
							<p className="mt-4 text-sm text-slate-200">The revenue layer: public pages, fundraising, apparel, dues, and credits.</p>
						</div>
					</div>
				</PageContainer>
			</section>

			<section className="bg-white py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-10">
					<SectionHeading heading="Built around every role in your organization." />
					<div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
						{ROLE_CARDS.map((card) => (
							<AudienceCard key={card.heading} {...card} />
						))}
					</div>
				</PageContainer>
			</section>

			<section className="bg-navy-950 py-16">
				<PageContainer className="flex flex-col items-center gap-6 text-center">
					<h2 className="font-heading text-2xl font-extrabold text-white sm:text-3xl">Ready to see it on your program?</h2>
					<PrimaryButton to="/auth/register" icon="arrow">
						Get Started
					</PrimaryButton>
				</PageContainer>
			</section>
		</>
	);
}
