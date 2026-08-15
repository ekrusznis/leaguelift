const DEFAULT_PAGE_SIZES = [25, 50, 100];

function pageWindow(currentPage: number, totalPages: number): number[] {
	if (totalPages <= 5) return Array.from({ length: totalPages }, (_, index) => index);
	const start = Math.max(0, Math.min(currentPage - 2, totalPages - 5));
	return Array.from({ length: 5 }, (_, index) => start + index);
}

export function Pagination({
	page,
	size,
	totalElements,
	onPageChange,
	onSizeChange,
	pageSizeOptions = DEFAULT_PAGE_SIZES,
}: {
	page: number;
	size: number;
	totalElements: number;
	onPageChange: (page: number) => void;
	onSizeChange?: (size: number) => void;
	pageSizeOptions?: number[];
}) {
	if (totalElements <= 0) return null;
	const totalPages = Math.max(1, Math.ceil(totalElements / size));
	const safePage = Math.min(Math.max(page, 0), totalPages - 1);
	const start = safePage * size + 1;
	const end = Math.min((safePage + 1) * size, totalElements);

	return (
		<nav className="mt-4 flex flex-col gap-3 border-t border-slate-200 pt-4 dark:border-[#334155] sm:flex-row sm:items-center sm:justify-between" aria-label="List pagination">
			<div className="flex flex-wrap items-center gap-3 text-sm text-slate-600 dark:text-[#cbd5e1]">
				<span>
					Showing {start.toLocaleString()}–{end.toLocaleString()} of {totalElements.toLocaleString()}
				</span>
				{onSizeChange && (
					<label className="flex items-center gap-2">
						<span>Rows</span>
						<select
							aria-label="Rows per page"
							value={size}
							onChange={(event) => onSizeChange(Number(event.target.value))}
							className="min-h-10 rounded-md border border-slate-300 bg-white px-2 text-navy-900 dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							{pageSizeOptions.map((option) => (
								<option key={option} value={option}>{option}</option>
							))}
						</select>
					</label>
				)}
			</div>

			<div className="flex flex-wrap items-center gap-1">
				<button type="button" disabled={safePage === 0} onClick={() => onPageChange(safePage - 1)} className="min-h-10 rounded-md border border-slate-300 px-3 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-40 dark:border-[#334155]">
					Previous
				</button>
				{pageWindow(safePage, totalPages).map((pageIndex) => (
					<button
						type="button"
						key={pageIndex}
						onClick={() => onPageChange(pageIndex)}
						aria-current={pageIndex === safePage ? "page" : undefined}
						aria-label={`Page ${pageIndex + 1}`}
						className={`min-h-10 min-w-10 rounded-md border px-2 text-sm font-semibold ${pageIndex === safePage ? "border-victory-green bg-victory-green text-white" : "border-slate-300 text-navy-900 dark:border-[#334155] dark:text-[#f8fafc]"}`}
					>
						{pageIndex + 1}
					</button>
				))}
				<button type="button" disabled={safePage >= totalPages - 1} onClick={() => onPageChange(safePage + 1)} className="min-h-10 rounded-md border border-slate-300 px-3 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-40 dark:border-[#334155]">
					Next
				</button>
			</div>
		</nav>
	);
}
