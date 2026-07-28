import { GoldButton } from "./buttons";
import { heroImages } from "../heroImages";

const BENEFITS = [
	"Guided onboarding",
	"A dedicated setup contact",
	"Help configuring teams, tournaments, and pages",
	"Direct product feedback channel",
	"Priority consideration for new modules",
];

/** Dark + gold guided-onboarding feature card (adapted from section 12.5). */
export function GuidedOnboardingCard() {
	return (
		<div className="relative overflow-hidden rounded-[22px] border border-gold-500/30 shadow-[0_22px_60px_rgba(0,0,0,0.32)]">
			<img src={heroImages.goldAccent} alt="" aria-hidden="true" className="absolute inset-0 size-full object-cover" />
			<div
				className="pointer-events-none absolute inset-0"
				style={{ background: "linear-gradient(135deg, rgba(6,19,33,0.88) 30%, rgba(6,19,33,0.6) 100%)" }}
			/>
			<div className="relative p-8 sm:p-10">
				<p className="font-heading text-xs font-semibold uppercase tracking-wide text-gold-500">Guided Onboarding</p>
				<h3 className="mt-2 max-w-lg text-balance font-heading text-2xl font-extrabold text-white sm:text-3xl">
					Prefer a hands-on setup? Talk to our team.
				</h3>
				<ul className="mt-6 grid gap-3 sm:grid-cols-2">
					{BENEFITS.map((benefit) => (
						<li key={benefit} className="flex items-center gap-2 text-sm text-slate-200">
							<svg className="size-4 shrink-0 text-gold-500" viewBox="0 0 16 16" fill="none" aria-hidden="true">
								<path d="m3 8.5 3 3 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
							</svg>
							{benefit}
						</li>
					))}
				</ul>
				<GoldButton to="/talk-to-sales" icon="arrow" className="mt-8">
					Talk to Our Team
				</GoldButton>
			</div>
		</div>
	);
}
