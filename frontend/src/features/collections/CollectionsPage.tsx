import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { STATUS_COLORS, STATUS_LABELS } from "../fees/statusLabels";
import type { FeeAssignmentStatus } from "../fees/types";
import { downloadCollectionsCsv, useCollections } from "./api";

function formatAmount(amountMinor: number, currency: string) {
	return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amountMinor / 100);
}

export function CollectionsPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const [status, setStatus] = useState<FeeAssignmentStatus | "">("");
	const [overdueOnly, setOverdueOnly] = useState(false);
	const [isExporting, setIsExporting] = useState(false);
	const [exportError, setExportError] = useState<string | null>(null);

	const filter = { status: status || undefined, overdueOnly, size: 100 };
	const { data, isLoading, isError, refetch } = useCollections(organizationId ?? "", filter);

	if (!organizationId) {
		return <ErrorState message="No organization selected." />;
	}

	async function handleExport() {
		setIsExporting(true);
		setExportError(null);
		try {
			await downloadCollectionsCsv(organizationId!, filter);
		} catch {
			setExportError("Could not export collections. Please try again.");
		} finally {
			setIsExporting(false);
		}
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={`/app/organizations/${organizationId}`} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← Back to organization
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy">Collections</h1>
				<p className="text-slate-gray">Org-wide fee balances across all households.</p>
			</div>

			<div className="flex flex-wrap items-end gap-3">
				<div className="flex flex-col gap-1">
					<label htmlFor="collections-status" className="text-sm font-medium text-navy">Status</label>
					<select
						id="collections-status"
						value={status}
						onChange={(e) => setStatus(e.target.value as FeeAssignmentStatus | "")}
						className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
					>
						<option value="">All statuses</option>
						<option value="OPEN">Open</option>
						<option value="PARTIALLY_PAID">Partially paid</option>
						<option value="PAID">Paid</option>
						<option value="WAIVED">Waived</option>
						<option value="CANCELLED">Cancelled</option>
					</select>
				</div>
				<div className="flex items-center gap-2 pb-1">
					<input id="collections-overdue" type="checkbox" checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)} className="h-4 w-4" />
					<label htmlFor="collections-overdue" className="text-sm font-medium text-navy">Overdue only</label>
				</div>
				<Button type="button" variant="secondary" onClick={handleExport} disabled={isExporting}>
					{isExporting ? "Exporting…" : "Export CSV"}
				</Button>
			</div>
			{exportError && <p role="alert" className="text-sm text-error-red">{exportError}</p>}

			{isLoading && <LoadingState label="Loading collections…" />}
			{isError && <ErrorState message="Could not load collections." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && (
				<EmptyState title="Nothing to collect" description="No fee assignments match these filters." />
			)}
			{data && data.items.length > 0 && (
				<div className="overflow-x-auto rounded-lg border border-slate-gray/20">
					<table className="w-full text-left text-sm">
						<thead className="bg-ice-white text-slate-gray">
							<tr>
								<th className="p-3 font-medium">Household</th>
								<th className="p-3 font-medium">Participant</th>
								<th className="p-3 font-medium">Description</th>
								<th className="p-3 font-medium">Original</th>
								<th className="p-3 font-medium">Paid</th>
								<th className="p-3 font-medium">Adjusted</th>
								<th className="p-3 font-medium">Balance</th>
								<th className="p-3 font-medium">Due</th>
								<th className="p-3 font-medium">Status</th>
							</tr>
						</thead>
						<tbody>
							{data.items.map((row) => (
								<tr key={row.id} className="border-t border-slate-gray/10">
									<td className="p-3">
										<Link to={`/app/organizations/${organizationId}/households/${row.householdId}`} className="text-azure-blue hover:underline">
											{row.householdName}
										</Link>
									</td>
									<td className="p-3 text-slate-gray">{row.participantName ?? "—"}</td>
									<td className="p-3">{row.description}</td>
									<td className="p-3">{formatAmount(row.originalAmountMinor, row.currency)}</td>
									<td className="p-3">{formatAmount(row.paidMinor, row.currency)}</td>
									<td className="p-3">{formatAmount(row.adjustedMinor, row.currency)}</td>
									<td className="p-3 font-semibold">{formatAmount(row.balanceMinor, row.currency)}</td>
									<td className="p-3 text-slate-gray">{row.dueDate ?? "—"}</td>
									<td className="p-3">
										<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[row.status]}`}>
											{STATUS_LABELS[row.status]}
										</span>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
