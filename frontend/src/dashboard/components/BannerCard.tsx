import type { ReactNode } from "react";

/** Full-width highlight banner at the bottom of a dashboard (e.g. "Keep pushing, Maya."). */
export function BannerCard({ icon, heading, copy, action }: { icon: ReactNode; heading: string; copy: string; action?: ReactNode }) {
	return (
		<div className="flex flex-col items-start gap-4 rounded-2xl border border-green-500/25 bg-green-500/5 p-5 sm:flex-row sm:items-center sm:justify-between">
			<div className="flex items-center gap-3">
				<span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-green-500/15 text-green-600">{icon}</span>
				<div>
					<p className="font-heading font-bold text-navy-900 dark:text-[#f8fafc]">{heading}</p>
					<p className="text-sm text-slate-600 dark:text-[#cbd5e1]">{copy}</p>
				</div>
			</div>
			{action}
		</div>
	);
}
