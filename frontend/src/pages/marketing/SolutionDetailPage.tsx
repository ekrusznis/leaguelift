import { Navigate, useParams } from "react-router-dom";
import { AudienceCard } from "../../marketing/components/AudienceCard";
import { FaqAccordion } from "../../marketing/components/FaqAccordion";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton } from "../../marketing/components/buttons";
import { getSolution } from "../../marketing/content/solutions";

/** Shared solution-detail layout (section 15.1) driven by content/solutions.ts. */
export function SolutionDetailPage() {
	const { slug } = useParams<{ slug: string }>();
	const solution = slug ? getSolution(slug) : undefined;

	if (!solution) {
		return <Navigate to="/404" replace />;
	}

	return (
		<>
			<Seo title={solution.heading} description={solution.shortDescription} />

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className={solution.hero.image ? "grid items-center gap-10 lg:grid-cols-[1.1fr_0.9fr]" : "max-w-3xl"}>
					<div>
						<h1 className="text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">
							{solution.hero.headline}
						</h1>
						<p className="mt-4 text-lg leading-relaxed text-slate-300">{solution.hero.copy}</p>
						{solution.requiredStatement && (
							<p className="mt-6 rounded-xl border border-gold-500/30 bg-gold-500/10 p-4 text-sm text-gold-500">
								{solution.requiredStatement}
							</p>
						)}
					</div>
					{solution.hero.image && (
						<div className="overflow-hidden rounded-[24px] border border-white/10">
							<img src={solution.hero.image} alt="" className="aspect-[4/3] w-full object-cover" />
						</div>
					)}
				</PageContainer>
			</section>

			<section className="bg-white py-16 sm:py-20">
				<PageContainer className="grid gap-10 lg:grid-cols-2">
					<div>
						<h2 className="font-heading text-xl font-bold text-navy-900">The problem</h2>
						<p className="mt-3 text-slate-700 leading-relaxed">{solution.problem}</p>
					</div>
					<div>
						<h2 className="font-heading text-xl font-bold text-navy-900">The Rally26 approach</h2>
						<p className="mt-3 text-slate-700 leading-relaxed">{solution.approach}</p>
					</div>
				</PageContainer>
			</section>

			<section className="bg-ice-50 py-16 sm:py-20">
				<PageContainer className="grid gap-10 lg:grid-cols-2">
					<div>
						<SectionHeading align="left" heading="Key capabilities" />
						<ul className="mt-6 grid gap-3">
							{solution.capabilities.map((capability) => (
								<li key={capability} className="flex items-center gap-2 text-sm text-slate-700">
									<svg className="size-4 shrink-0 text-green-600" viewBox="0 0 16 16" fill="none" aria-hidden="true">
										<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
									</svg>
									{capability}
								</li>
							))}
						</ul>
					</div>
					<div>
						<SectionHeading align="left" heading="Example workflow" />
						<ol className="mt-6 flex flex-col gap-3">
							{solution.workflow.map((step, index) => (
								<li key={step} className="flex items-start gap-3 text-sm text-slate-700">
									<span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-green-500/15 text-xs font-bold text-green-600">
										{index + 1}
									</span>
									{step}
								</li>
							))}
						</ol>
					</div>
				</PageContainer>
			</section>

			<section className="bg-white py-16 sm:py-20">
				<PageContainer className="flex flex-col gap-8">
					<SectionHeading heading="Who this is for" />
					<div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
						{solution.relatedUsers.map((user) => (
							<AudienceCard key={user} heading={user} />
						))}
					</div>
				</PageContainer>
			</section>

			<section className="bg-ice-50 py-16 sm:py-20">
				<PageContainer className="flex flex-col gap-8">
					<SectionHeading heading="Frequently asked questions" />
					<div className="mx-auto w-full max-w-3xl">
						<FaqAccordion items={solution.faq} />
					</div>
				</PageContainer>
			</section>

			<section className="bg-navy-950 py-16">
				<PageContainer className="flex flex-col items-center gap-6 text-center">
					<h2 className="font-heading text-2xl font-extrabold text-white sm:text-3xl">
						See {solution.navLabel.toLowerCase()} on your own organization.
					</h2>
					<PrimaryButton to="/auth/register" icon="arrow">
						Get Started
					</PrimaryButton>
				</PageContainer>
			</section>
		</>
	);
}
