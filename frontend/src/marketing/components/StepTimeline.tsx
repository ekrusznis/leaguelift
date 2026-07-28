import type { ReactNode } from "react";

type Step = { title: string; copy: string; icon: ReactNode };

/** Connected horizontal process on desktop, stacked on mobile (section 12.6). */
export function StepTimeline({ steps }: { steps: Step[] }) {
	return (
		<ol className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4 lg:gap-6">
			{steps.map((step, index) => (
				<li key={step.title} className="relative flex flex-col gap-3">
					<div className="flex items-center gap-3">
						<span className="flex size-12 shrink-0 items-center justify-center rounded-full border border-green-400/50 bg-green-500/10 text-green-400">
							{step.icon}
						</span>
						{index < steps.length - 1 && (
							<span className="hidden h-px flex-1 border-t border-dashed border-white/25 lg:block" aria-hidden="true" />
						)}
					</div>
					<h3 className="font-heading text-lg font-bold text-white">
						<span className="text-green-400">{index + 1}</span> {step.title}
					</h3>
					<p className="text-sm leading-relaxed text-slate-300">{step.copy}</p>
				</li>
			))}
		</ol>
	);
}
