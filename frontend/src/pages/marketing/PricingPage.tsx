import { FaqAccordion } from "../../marketing/components/FaqAccordion";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";
import { PRICING_FAQ } from "../../marketing/content/pricing";

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

export function PricingPage() {
	return (
		<>
			<Seo
				title="Pricing"
				description="LeagueLift combines organization subscriptions with clearly disclosed transaction fees and optional implementation services."
			/>

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-3xl">
					<SectionHeading
						tone="dark"
						align="left"
						heading="Start small. Grow with your program."
						copy="LeagueLift combines organization subscriptions with clearly disclosed transaction fees and optional implementation services."
					/>
				</PageContainer>
			</section>

			<section className="bg-white py-20 sm:py-28">
				<PageContainer className="grid gap-8 lg:grid-cols-2">
					<div className="rounded-[22px] border border-green-500/30 bg-navy-900 p-8 text-white">
						<p className="font-heading text-xs font-semibold uppercase tracking-wide text-green-400">Starter</p>
						<p className="mt-3 font-heading text-3xl font-extrabold">Starting at $149 per month</p>
						<p className="mt-2 text-sm text-slate-300">
							Applicable transaction, payment-processing, fulfillment, and optional service fees are
							disclosed before launch.
						</p>
						<ul className="mt-6 flex flex-col gap-2 text-sm text-slate-200">
							{STARTER_INCLUDES.map((item) => (
								<li key={item} className="flex items-center gap-2">
									<svg className="size-4 shrink-0 text-green-400" viewBox="0 0 16 16" fill="none" aria-hidden="true">
										<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
									</svg>
									{item}
								</li>
							))}
						</ul>
						<PrimaryButton to="/auth/register" icon="arrow" className="mt-8 w-full justify-center">
							Get Started
						</PrimaryButton>
					</div>

					<div className="rounded-[22px] border border-slate-200 bg-ice-50 p-8">
						<p className="font-heading text-xs font-semibold uppercase tracking-wide text-slate-500">
							Enterprise & Large Organizations
						</p>
						<p className="mt-3 font-heading text-3xl font-extrabold text-navy-900">Contact Us</p>
						<p className="mt-2 text-sm text-slate-700">Suitable for:</p>
						<ul className="mt-6 flex flex-col gap-2 text-sm text-slate-700">
							{ENTERPRISE_FITS.map((item) => (
								<li key={item} className="flex items-center gap-2">
									<svg className="size-4 shrink-0 text-green-600" viewBox="0 0 16 16" fill="none" aria-hidden="true">
										<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
									</svg>
									{item}
								</li>
							))}
						</ul>
						<SecondaryLightButton to="/contact" className="mt-8 w-full justify-center">
							Contact Us
						</SecondaryLightButton>
					</div>
				</PageContainer>
			</section>

			<section className="bg-ice-50 py-20 sm:py-28">
				<PageContainer className="flex flex-col gap-8">
					<SectionHeading heading="Pricing questions" />
					<div className="mx-auto w-full max-w-3xl">
						<FaqAccordion items={PRICING_FAQ} />
					</div>
				</PageContainer>
			</section>
		</>
	);
}
