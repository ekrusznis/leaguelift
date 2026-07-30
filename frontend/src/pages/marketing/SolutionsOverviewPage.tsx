import { AvailabilityStatusBadge } from "../../marketing/components/Badges";
import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";
import { TextButton } from "../../marketing/components/buttons";
import { SOLUTIONS } from "../../marketing/content/solutions";

export function SolutionsOverviewPage() {
	return (
		<>
			<Seo
				title="Solutions"
				description="From one team to a multi-division tournament, LeagueLift gives adult administrators clear tools for public pages, fundraising, apparel, fees, credits, and reporting."
			/>

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-3xl">
					<SectionHeading
						tone="dark"
						align="left"
						heading="Revenue tools built for every level of youth sports."
						copy="From one team to a multi-division tournament, LeagueLift gives adult administrators clear tools for public pages, fundraising, apparel, fees, credits, and reporting."
					/>
				</PageContainer>
			</section>

			<section className="bg-white py-20 sm:py-28">
				<PageContainer className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
					{SOLUTIONS.map((solution) => (
						<div key={solution.slug} className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-ice-50 p-6">
							<div>
								<div className="flex flex-wrap items-start justify-between gap-3">
									<h2 className="font-heading text-lg font-bold text-navy-900">{solution.heading}</h2>
									<AvailabilityStatusBadge status={solution.availability} />
								</div>
								<p className="mt-3 text-sm leading-relaxed text-slate-700">{solution.shortDescription}</p>
							</div>
							<TextButton to={`/solutions/${solution.slug}`} icon="arrow" className="mt-5 self-start text-green-600">
								Learn more
							</TextButton>
						</div>
					))}
				</PageContainer>
			</section>
		</>
	);
}
