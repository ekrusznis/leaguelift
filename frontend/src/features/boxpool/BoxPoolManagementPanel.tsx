import { useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { BoxPoolGrid } from "./BoxPoolGrid";
import { useBoxPool, useCreateBoxPool } from "./api";

/** Shown on an organization's campaign row once its template is BOX_POOL — the setup form if no pool exists yet, otherwise the read-only grid (claiming itself only happens on the public page). */
export function BoxPoolManagementPanel({ organizationId, campaignId }: { organizationId: string; campaignId: string }) {
	const { data: pool, isLoading, isError } = useBoxPool(organizationId, campaignId);
	const createPool = useCreateBoxPool(organizationId, campaignId);
	const [sport, setSport] = useState("Football");
	const [rows, setRows] = useState(10);
	const [cols, setCols] = useState(10);
	const [price, setPrice] = useState(500);
	const [rowAxisLabel, setRowAxisLabel] = useState("");
	const [colAxisLabel, setColAxisLabel] = useState("");
	const [prizeDescription, setPrizeDescription] = useState("");

	if (isLoading) return <LoadingState label="Loading box pool…" />;

	if (isError || !pool) {
		return (
			<form
				onSubmit={async (event) => {
					event.preventDefault();
					await createPool.mutateAsync({
						sport,
						rows,
						cols,
						pricePerBoxMinor: price,
						rowAxisLabel: rowAxisLabel || null,
						colAxisLabel: colAxisLabel || null,
						prizeDescription: prizeDescription || null,
					});
				}}
				className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4"
				aria-label="Set up box pool"
			>
				<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
					Set up the grid once — random number assignment and announcing the winner each period is up to you, off-platform.
				</p>
				<div className="flex flex-wrap gap-3">
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Sport</span>
						<input value={sport} onChange={(e) => setSport(e.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Rows</span>
						<input type="number" min={1} max={26} value={rows} onChange={(e) => setRows(Number(e.target.value))} className="min-h-11 w-24 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Columns</span>
						<input type="number" min={1} max={26} value={cols} onChange={(e) => setCols(Number(e.target.value))} className="min-h-11 w-24 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Price per box (cents)</span>
						<input type="number" min={1} value={price} onChange={(e) => setPrice(Number(e.target.value))} className="min-h-11 w-32 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Row axis label</span>
						<input value={rowAxisLabel} onChange={(e) => setRowAxisLabel(e.target.value)} placeholder="e.g. Home team" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Column axis label</span>
						<input value={colAxisLabel} onChange={(e) => setColAxisLabel(e.target.value)} placeholder="e.g. Away team" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
					<label className="flex min-w-[16rem] flex-1 flex-col gap-1">
						<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Prize (what the winner gets — up to you)</span>
						<input value={prizeDescription} onChange={(e) => setPrizeDescription(e.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</label>
				</div>
				{createPool.isError && <p role="alert" className="text-sm text-error-red">Could not create the box pool. Please try again.</p>}
				<div className="flex justify-end">
					<Button type="submit" disabled={createPool.isPending}>
						{createPool.isPending ? "Creating…" : "Create box pool"}
					</Button>
				</div>
			</form>
		);
	}

	const claimed = pool.boxes.filter((box) => box.status === "CLAIMED").length;
	const total = pool.rows * pool.cols;

	return (
		<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4">
			<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
				{claimed} of {total} boxes claimed · {formatMoneyMinorUnits(pool.pricePerBoxMinor, "USD")} per box
				{pool.prizeDescription && <> · Prize: {pool.prizeDescription}</>}
			</p>
			<BoxPoolGrid rows={pool.rows} cols={pool.cols} boxes={pool.boxes} rowAxisLabel={pool.rowAxisLabel} colAxisLabel={pool.colAxisLabel} />
		</div>
	);
}
