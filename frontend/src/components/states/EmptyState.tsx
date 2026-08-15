import type { ReactNode } from "react";

export function EmptyState({
	title,
	description,
	action,
	compact = false,
}: {
	title: string;
	description?: string;
	action?: ReactNode;
	compact?: boolean;
}) {
	return (
		<div role="status" className={`rounded-lg border border-dashed border-slate-gray/30 text-center ${compact ? "p-5" : "p-8"}`}>
			<h3 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">{title}</h3>
			{description && <p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">{description}</p>}
			{action && <div className="mt-4">{action}</div>}
		</div>
	);
}
