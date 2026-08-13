import type { BoxPoolBox } from "./types";

const STATUS_STYLES: Record<BoxPoolBox["status"], string> = {
	OPEN: "bg-pure-white dark:bg-[#111827] border-slate-gray/30 hover:border-info-blue hover:bg-ice-white hover:dark:bg-[#0f172a] cursor-pointer",
	RESERVED: "bg-championship-gold/10 border-championship-gold/40 text-navy dark:text-[#f8fafc] cursor-not-allowed",
	CLAIMED: "bg-victory-green/10 border-victory-green/40 text-navy dark:text-[#f8fafc] cursor-not-allowed",
};

/**
 * Box-pool grid/slot-picker (Phase 42) — the one genuinely new UI pattern this
 * feature needed; no grid/calendar/seat-picker precedent existed anywhere in this
 * frontend before. Read-only (org management view, and any RESERVED/CLAIMED box on
 * the public view) unless `onSelectBox` is given, in which case only OPEN boxes are
 * clickable.
 */
export function BoxPoolGrid({
	rows,
	cols,
	boxes,
	rowAxisLabel,
	colAxisLabel,
	onSelectBox,
}: {
	rows: number;
	cols: number;
	boxes: BoxPoolBox[];
	rowAxisLabel?: string | null;
	colAxisLabel?: string | null;
	onSelectBox?: (box: BoxPoolBox) => void;
}) {
	const byPosition = new Map(boxes.map((box) => [`${box.rowIndex}-${box.colIndex}`, box]));

	return (
		<div className="flex flex-col gap-2">
			{(rowAxisLabel || colAxisLabel) && (
				<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">
					{rowAxisLabel && <span>Rows: {rowAxisLabel}</span>}
					{rowAxisLabel && colAxisLabel && " · "}
					{colAxisLabel && <span>Columns: {colAxisLabel}</span>}
				</p>
			)}
			<div
				role="grid"
				aria-label="Box pool grid"
				className="grid gap-1 overflow-x-auto"
				style={{ gridTemplateColumns: `repeat(${cols}, minmax(2.5rem, 1fr))` }}
			>
				{Array.from({ length: rows }).flatMap((_, rowIndex) =>
					Array.from({ length: cols }).map((_, colIndex) => {
						const box = byPosition.get(`${rowIndex}-${colIndex}`);
						const status = box?.status ?? "OPEN";
						const clickable = !!onSelectBox && !!box && status === "OPEN";
						const label = `Row ${rowIndex + 1}, column ${colIndex + 1}${status === "OPEN" ? ", open" : status === "RESERVED" ? ", reserved" : `, claimed${box?.claimantName ? ` by ${box.claimantName}` : ""}`}`;
						return (
							<button
								key={`${rowIndex}-${colIndex}`}
								type="button"
								role="gridcell"
								disabled={!clickable}
								onClick={() => box && clickable && onSelectBox?.(box)}
								aria-label={label}
								title={label}
								className={`flex aspect-square min-h-10 items-center justify-center rounded-md border text-[10px] font-medium ${STATUS_STYLES[status]} disabled:opacity-90`}
							>
								{status === "CLAIMED" && box?.claimantName ? (
									<span className="truncate px-0.5">{box.claimantName.split(" ")[0]}</span>
								) : status === "RESERVED" ? (
									<span aria-hidden>•</span>
								) : null}
							</button>
						);
					}),
				)}
			</div>
			<div className="flex flex-wrap gap-3 text-xs text-slate-gray dark:text-[#cbd5e1]">
				<span className="flex items-center gap-1">
					<span className="inline-block size-3 rounded-sm border border-slate-gray/30 bg-pure-white dark:bg-[#111827]" aria-hidden /> Open
				</span>
				<span className="flex items-center gap-1">
					<span className="inline-block size-3 rounded-sm border border-championship-gold/40 bg-championship-gold/10" aria-hidden /> Reserved (payment in progress)
				</span>
				<span className="flex items-center gap-1">
					<span className="inline-block size-3 rounded-sm border border-victory-green/40 bg-victory-green/10" aria-hidden /> Claimed
				</span>
			</div>
		</div>
	);
}
