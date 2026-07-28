import type { ReactNode } from "react";

export function DashboardPageHeader({
	heading,
	emoji = "\u{1F44B}",
	description,
	actions,
}: {
	heading: string;
	emoji?: string;
	description?: string;
	actions?: ReactNode;
}) {
	return (
		<div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
			<div>
				<h1 className="flex items-center gap-2 font-heading text-2xl font-extrabold text-navy-900 sm:text-3xl">
					{heading} <span aria-hidden="true">{emoji}</span>
				</h1>
				{description && <p className="mt-1 text-slate-600">{description}</p>}
			</div>
			{actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
		</div>
	);
}
