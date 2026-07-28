import { PageContainer } from "../../marketing/components/PageContainer";
import { SectionHeading } from "../../marketing/components/SectionHeading";
import { Seo } from "../../marketing/components/Seo";

const VALUES = [
	"Stronger communities",
	"Clear financial reporting",
	"Less volunteer administration",
	"Responsible data use",
	"Adult-controlled youth information",
	"Honest product claims",
];

export function AboutPage() {
	return (
		<>
			<Seo title="About" description="Better revenue tools for the people who keep youth sports running." />

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-3xl">
					<SectionHeading
						tone="dark"
						align="left"
						heading="Better revenue tools for the people who keep youth sports running."
					/>
				</PageContainer>
			</section>

			<section className="bg-white py-16 sm:py-20">
				<PageContainer className="mx-auto flex max-w-3xl flex-col gap-10">
					<div>
						<h2 className="font-heading text-xl font-bold text-navy-900">The founding idea</h2>
						<p className="mt-3 leading-relaxed text-slate-700">
							LeagueLift was created around a simple idea: youth sports organizations should have better
							ways to generate and manage revenue than repeatedly raising family fees or relying on
							already-busy volunteers.
						</p>
					</div>
					<div>
						<h2 className="font-heading text-xl font-bold text-navy-900">Mission</h2>
						<p className="mt-3 leading-relaxed text-slate-700">
							Help youth sports organizations build sustainable programs while making costs clearer and
							more manageable for families.
						</p>
					</div>
					<div>
						<h2 className="font-heading text-xl font-bold text-navy-900">Values</h2>
						<ul className="mt-4 grid gap-3 sm:grid-cols-2">
							{VALUES.map((value) => (
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
		</>
	);
}
