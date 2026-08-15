import type { ReactNode } from "react";

export interface ListSortOption {
	value: string;
	label: string;
}

export function ListToolbar({
	searchValue,
	onSearchChange,
	searchPlaceholder = "Search",
	filters,
	sortValue,
	sortOptions = [],
	onSortChange,
	resultCount,
	hasActiveFilters = false,
	onClear,
	actions,
}: {
	searchValue: string;
	onSearchChange: (value: string) => void;
	searchPlaceholder?: string;
	filters?: ReactNode;
	sortValue?: string;
	sortOptions?: ListSortOption[];
	onSortChange?: (value: string) => void;
	resultCount?: number;
	hasActiveFilters?: boolean;
	onClear?: () => void;
	actions?: ReactNode;
}) {
	return (
		<div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm dark:border-[#334155] dark:bg-[#111827] sm:p-4">
			<div className="flex flex-col gap-3 xl:flex-row xl:items-end">
				<label className="min-w-0 flex-1 text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">
					<span className="sr-only">Search</span>
					<input
						type="search"
						value={searchValue}
						onChange={(event) => onSearchChange(event.target.value)}
						placeholder={searchPlaceholder}
						className="min-h-11 w-full rounded-lg border border-slate-300 bg-white px-3 font-normal text-navy-900 outline-none transition placeholder:text-slate-400 focus:border-info-blue focus:ring-2 focus:ring-info-blue/20 dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
					/>
				</label>

				{filters && <div className="flex flex-wrap items-end gap-2">{filters}</div>}

				{sortOptions.length > 0 && onSortChange && (
					<label className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">
						<span className="sr-only">Sort by</span>
						<select
							aria-label="Sort by"
							value={sortValue ?? sortOptions[0]?.value ?? ""}
							onChange={(event) => onSortChange(event.target.value)}
							className="min-h-11 min-w-40 rounded-lg border border-slate-300 bg-white px-3 font-normal text-navy-900 outline-none focus:border-info-blue focus:ring-2 focus:ring-info-blue/20 dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							{sortOptions.map((option) => (
								<option key={option.value} value={option.value}>
									{option.label}
								</option>
							))}
						</select>
					</label>
				)}

				{(hasActiveFilters || searchValue.trim()) && onClear && (
					<button
						type="button"
						onClick={onClear}
						className="min-h-11 rounded-lg border border-slate-300 px-3 text-sm font-semibold text-slate-700 hover:bg-ice-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info-blue dark:border-[#334155] dark:text-[#e2e8f0] dark:hover:bg-[#0f172a]"
					>
						Clear
					</button>
				)}

				{actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
			</div>

			{resultCount !== undefined && (
				<p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]" aria-live="polite">
					{resultCount.toLocaleString()} {resultCount === 1 ? "result" : "results"}
				</p>
			)}
		</div>
	);
}
