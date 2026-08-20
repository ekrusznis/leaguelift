import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { heroImages } from "../../marketing/heroImages";
import { track } from "../../marketing/analytics";

const INCLUDED = [
	"Full Club-tier access ($149/month value) for three months — $447 total value",
	"Normal transaction fees only — no markup, no hidden pilot pricing",
	"Direct access to the Rally26 team, not a support queue",
	"Guided setup for teams, dues, fundraising, apparel, and eligibility",
	"Priority consideration for new features as they ship",
];

const EXPECTATIONS = [
	"Actively use Rally26 as your organization's primary platform for the pilot",
	"Share real, candid feedback on what works and what doesn't",
	"A short check-in conversation with our team partway through the pilot",
];

/**
 * Dedicated landing page for the 10-club Founding Organization outreach campaign
 * (LR-021) — replaces sending invited prospects straight into the generic
 * registration flow, which gave no context for what "Founding Organization" meant.
 * `/founding-pilot` continues to redirect here for continuity with any links already
 * sent. The CTA routes to the real, backend-wired Talk to Sales form rather than a
 * fabricated confirmation, so every request actually reaches the team.
 */
export function FoundingOrganizationsPage() {
	return (
		<>
			<Seo
				title="Founding Organizations"
				description="Rally26 is inviting 10 youth sports organizations into a six-month Founding Organization pilot with full Club access."
			/>

			<section className="relative overflow-hidden bg-navy-950 py-20 sm:py-28">
				<PageContainer className="relative grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
					<div>
						<p className="inline-flex items-center gap-1.5 rounded-full bg-orange-600 px-3 py-1 text-xs font-bold uppercase tracking-wide text-white">
							Founding Organizations
						</p>
						<h1 className="mt-5 text-balance font-heading text-4xl font-extrabold leading-[1.08] text-white sm:text-5xl">
							We're inviting 10 organizations to help shape Rally26.
						</h1>
						<p className="mt-6 max-w-xl text-lg leading-relaxed text-slate-300">
							A three-month pilot with full Club access, direct access to our team, and a real voice in
							what we build next — for organizations willing to run their season on Rally26 and tell us
							what's working.
						</p>
						<div className="mt-8">
							<PrimaryButton to="/talk-to-sales" icon="arrow" onClick={() => track("founding_org_cta_clicked")}>
								Request a Founding Organization Spot
							</PrimaryButton>
						</div>
					</div>
					<div className="relative overflow-hidden rounded-[24px] border border-white/10">
						<img
							src={heroImages.multisportHuddle}
							alt="Athletes from hockey, soccer, baseball, and basketball standing together in a stadium under orange stadium lights"
							className="aspect-[4/3] w-full object-cover"
						/>
					</div>
				</PageContainer>
			</section>

			<section className="bg-white dark:bg-[#111827] py-16 sm:py-20">
				<PageContainer className="mx-auto flex max-w-3xl flex-col gap-12">
					<div>
						<SectionHeading align="left" heading="What's included" />
						<ul className="mt-6 flex flex-col gap-3">
							{INCLUDED.map((item) => (
								<li key={item} className="flex items-start gap-2.5 text-slate-700 dark:text-[#cbd5e1]">
									<svg className="mt-1 size-4 shrink-0 text-success-700 dark:text-success-400" viewBox="0 0 16 16" fill="none" aria-hidden="true">
										<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
									</svg>
									{item}
								</li>
							))}
						</ul>
					</div>

					<div>
						<SectionHeading align="left" heading="What we're asking in return" />
						<ul className="mt-6 flex flex-col gap-3">
							{EXPECTATIONS.map((item) => (
								<li key={item} className="flex items-start gap-2.5 text-slate-700 dark:text-[#cbd5e1]">
									<svg className="mt-1 size-4 shrink-0 text-orange-500" viewBox="0 0 16 16" fill="none" aria-hidden="true">
										<circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="1.6" />
									</svg>
									{item}
								</li>
							))}
						</ul>
					</div>

					<div className="rounded-[22px] border border-slate-200 dark:border-[#334155] bg-ice-50 dark:bg-[#0f172a] p-6 sm:p-8">
						<h3 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">After the pilot</h3>
						<p className="mt-2 leading-relaxed text-slate-700 dark:text-[#cbd5e1]">
							There's no obligation to continue. If Rally26 is working for your organization, you'll move
							to standard Club pricing — no surprise increase, no auto-renewal games.
						</p>
					</div>

					<div className="flex flex-col items-center gap-4 text-center">
						<p className="text-slate-700 dark:text-[#cbd5e1]">Only 10 spots are available for this pilot.</p>
						<PrimaryButton to="/talk-to-sales" icon="arrow" onClick={() => track("founding_org_cta_clicked_footer")}>
							Request a Founding Organization Spot
						</PrimaryButton>
					</div>
				</PageContainer>
			</section>
		</>
	);
}
