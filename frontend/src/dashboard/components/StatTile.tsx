import type { ReactNode } from "react";

type StatTileProps = {
	icon?: ReactNode;
	value: string;
	label: string;
	trend?: string;
};

/** Small stat block used in Organization Summary / Roster Summary style grids. */
export function StatTile({ icon, value, label, trend }: StatTileProps) {
	return (
		<div className="flex flex-col gap-1">
			{icon && <span className="text-green-600">{icon}</span>}
			<span className="font-heading text-2xl font-extrabold text-navy-900 dark:text-[#f8fafc]">{value}</span>
			<span className="text-sm text-slate-500 dark:text-[#cbd5e1]">{label}</span>
			{trend && <span className="text-xs font-semibold text-green-600">{trend}</span>}
		</div>
	);
}
