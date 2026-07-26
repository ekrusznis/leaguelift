import type { ReactNode } from "react";

export function EmptyState({
	title,
	description,
	action,
}: {
	title: string;
	description?: string;
	action?: ReactNode;
}) {
	return (
		<div className="rounded-lg border border-dashed border-slate-gray/30 p-8 text-center">
			<h3 className="font-heading text-lg font-semibold text-navy">{title}</h3>
			{description && <p className="mt-1 text-sm text-slate-gray">{description}</p>}
			{action && <div className="mt-4">{action}</div>}
		</div>
	);
}
