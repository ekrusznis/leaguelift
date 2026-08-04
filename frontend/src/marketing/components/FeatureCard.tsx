import type { ReactNode } from "react";

type FeatureCardProps = {
	icon: ReactNode;
	heading: string;
	description: string;
};

/** Dark elevated card for below-hero benefit cards (section 12.2). */
export function FeatureCard({ icon, heading, description }: FeatureCardProps) {
	return (
		<div className="rounded-[22px] border border-white/[0.16] bg-navy-800 p-6 shadow-[0_22px_60px_rgba(0,0,0,0.32),inset_0_1px_0_rgba(255,255,255,0.04)] transition-all duration-200 hover:border-white/25">
			<div className="flex size-11 items-center justify-center rounded-xl bg-orange-500/15 text-orange-400">{icon}</div>
			<h3 className="mt-4 font-heading text-lg font-bold text-white">{heading}</h3>
			<p className="mt-2 text-sm leading-relaxed text-slate-300">{description}</p>
		</div>
	);
}
