import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { STATUS_COLORS, STATUS_LABELS } from "../fees/statusLabels";
import type { FeeAssignmentStatus } from "../fees/types";
import {
	downloadCollectionsSearchCsv,
	useCollectionsSearch,
	type CollectionsSort,
} from "./searchApi";

export function CollectionsPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<FeeAssignmentStatus | "">("");
	const [overdueOnly, setOverdueOnly] = useState(false);
	const [sort, setSort] = useState<CollectionsSort>("DUE_DATE_ASC");
	const [isExporting, setIsExporting] = useState(false);
	const [exportError, setExportError] = useState<string | null>(null);

	const filter = {
		q: query,
		status: status || undefined,
		overdueOnly,
		sort,
		page,
		size,
	};
	const { data, isLoading, isError, refetch } = useCollectionsSearch(organizationId ?? "", filter);

	if (!organizationId) return <ErrorState message="No organization selected." />;

	async function handleExport() {
		setIsExporting(true);
		setExportError(null);
		try {
			await downloadCollectionsSearchCsv(organizationId!, filter);
		} catch {
			setExportError("Could not export collections. Please try again.");
		} finally {
			setIsExporting(false);
		}
	}

	const hasFilters = !!status || overdueOnly;

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link
					to={`/app/organizations/${organizationId}`}
					className="mb-2 inline-block text-sm text-azure-blue hover:underline"
				>
					← Back to organization
				</Link>
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">
					Collections
				</h1>
				<p className="text-slate-gray dark:text-[#cbd5e1]">
					Organization-wide fee balances across all households.
				</p>
			</div>

			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search household, athlete, fee, or template"
				resultCount={data?.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "DUE_DATE_ASC", label: "Due date — soonest" },
					{ value: "DUE_DATE_DESC", label: "Due date — latest" },
					{ value: "BALANCE_DESC", label: "Balance — high to low" },
					{ value: "BALANCE_ASC", label: "Balance — low to high" },
					{ value: "HOUSEHOLD_ASC", label: "Household A–Z" },
					{ value: "DESCRIPTION_ASC", label: "Fee A–Z" },
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
				]}
				onSortChange={(value) => {
					setSort(value as CollectionsSort);
					setPage(0);
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("");
					setOverdueOnly(false);
					setSort("DUE_DATE_ASC");
					setPage(0);
				}}
				filters={
					<>
						<select
							aria-label="Filter collections by status"
							value={status}
							onChange={(event) => {
								setStatus(event.target.value as FeeAssignmentStatus | "");
								setPage(0);
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All statuses</option>
							<option value="OPEN">Open</option>
							<option value="PARTIALLY_PAID">Partially paid</option>
							<option value="PAID">Paid</option>
							<option value="WAIVED">Waived</option>
							<option value="CANCELLED">Cancelled</option>
						</select>
						<label className="flex min-h-11 items-center gap-2 rounded-lg border border-slate-300 px-3 text-sm font-medium text-navy dark:border-[#334155] dark:text-[#f8fafc]">
							<input
								type="checkbox"
								checked={overdueOnly}
								onChange={(event) => {
									setOverdueOnly(event.target.checked);
									setPage(0);
								}}
							/>
							Overdue only
						</label>
					</>
				}
				actions={
					<Button type="button" variant="secondary" onClick={handleExport} disabled={isExporting}>
						{isExporting ? "Exporting…" : "Export current results"}
					</Button>
				}
			/>

			{exportError && <p role="alert" className="text-sm text-error-red">{exportError}</p>}
			{isLoading && <LoadingState label="Loading collections…" />}
			{isError && <ErrorState message="Could not load collections." onRetry={() => refetch()} />}

			{data && data.items.length === 0 && (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "Nothing to collect"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: "Fee assignments will appear here once households have balances to manage."
					}
				/>
			)}

			{data && data.items.length > 0 && (
				<div className="overflow-x-auto rounded-lg border border-slate-gray/20">
					<table className="w-full text-left text-sm">
						<thead className="bg-ice-white text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]">
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
										<Link
											to={`/app/organizations/${organizationId}/households/${row.householdId}`}
											className="text-azure-blue hover:underline"
										>
											{row.householdName}
										</Link>
									</td>
									<td className="p-3 text-slate-gray dark:text-[#cbd5e1]">
										{row.participantName ?? "—"}
									</td>
									<td className="p-3">{row.description}</td>
									<td className="p-3">
										{formatMoneyMinorUnits(row.originalAmountMinor, row.currency)}
									</td>
									<td className="p-3">
										{formatMoneyMinorUnits(row.paidMinor, row.currency)}
									</td>
									<td className="p-3">
										{formatMoneyMinorUnits(row.adjustedMinor, row.currency)}
									</td>
									<td className="p-3 font-semibold">
										{formatMoneyMinorUnits(row.balanceMinor, row.currency)}
									</td>
									<td className="p-3 text-slate-gray dark:text-[#cbd5e1]">
										{row.dueDate ?? "—"}
									</td>
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

			{data && (
				<Pagination
					page={page}
					size={size}
					totalElements={data.totalElements}
					onPageChange={setPage}
					onSizeChange={(value) => {
						setSize(value);
						setPage(0);
					}}
				/>
			)}
		</div>
	);
}
