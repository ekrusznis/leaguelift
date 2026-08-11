import type { ReactNode } from "react";

export function AudienceCard({ heading, copy, icon }: { heading: string; copy?: string; icon?: ReactNode }) {
	return (
		<div className="rounded-2xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-6 shadow-[0_12px_34px_rgba(11,31,51,0.10)]">
			{icon && (
				<span className="mb-3 flex size-10 items-center justify-center rounded-xl bg-orange-500/10 text-orange-500">{icon}</span>
			)}
			<h3 className="font-heading text-lg font-bold text-navy-900 dark:text-[#f8fafc]">{heading}</h3>
			{copy && <p className="mt-2 text-sm leading-relaxed text-slate-700 dark:text-[#cbd5e1]">{copy}</p>}
		</div>
	);
}
