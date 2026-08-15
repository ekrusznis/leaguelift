import { useState } from "react";
import { Link } from "react-router-dom";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { appPaths } from "../../routes/appPaths";
import { useContributionSearch, type ContributionSearchSort } from "./searchApi";

export function ContributionList({ organizationId, campaignId }: { organizationId: string; campaignId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<"CONFIRMED" | "REFUNDED" | "">("");
	const [paymentSource, setPaymentSource] = useState<"STRIPE" | "OFFLINE" | "">("");
	const [sort, setSort] = useState<ContributionSearchSort>("NEWEST");
	const { data, isLoading, isError, refetch } = useContributionSearch(
		organizationId,
		campaignId,
		{ page, size, q: query, status, paymentSource, sort },
	);

	if (isLoading) return <LoadingState label="Loading contributions…" />;
	if (isError || !data) return <ErrorState message="Could not load contributions." onRetry={() => refetch()} />;

	const hasFilters = !!status || !!paymentSource;

	return (
		<div className="flex flex-col gap-3">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => { setQuery(value); setPage(0); }}
				searchPlaceholder="Search supporter name or email"
				resultCount={data.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
					{ value: "AMOUNT_DESC", label: "Amount — high to low" },
					{ value: "AMOUNT_ASC", label: "Amount — low to high" },
					{ value: "SUPPORTER_ASC", label: "Supporter A–Z" },
				]}
				onSortChange={(value) => { setSort(value as ContributionSearchSort); setPage(0); }}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("");
					setPaymentSource("");
					setSort("NEWEST");
					setPage(0);
				}}
				filters={
					<>
						<select
							aria-label="Filter contribution status"
							value={status}
							onChange={(event) => { setStatus(event.target.value as typeof status); setPage(0); }}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All statuses</option>
							<option value="CONFIRMED">Confirmed</option>
							<option value="REFUNDED">Refunded</option>
						</select>
						<select
							aria-label="Filter payment source"
							value={paymentSource}
							onChange={(event) => { setPaymentSource(event.target.value as typeof paymentSource); setPage(0); }}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All payment sources</option>
							<option value="STRIPE">Online card</option>
							<option value="OFFLINE">Recorded offline</option>
						</select>
					</>
				}
			/>

			{data.items.length === 0 ? (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No contributions yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: "Confirmed online and recorded offline contributions will appear here."
					}
				/>
			) : (
				<ul className="flex flex-col gap-2" aria-label="Contributions">
					{data.items.map((contribution) => (
						<li key={contribution.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
							<div className="min-w-0 flex-1">
								<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
									{contribution.isAnonymous ? "Anonymous supporter" : contribution.supporterName ?? "Anonymous supporter"}
									{contribution.paymentSource === "OFFLINE" && <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">Recorded offline</span>}
									{contribution.status === "REFUNDED" && <span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]">Refunded</span>}
								</p>
								<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{formatMoneyMinorUnits(contribution.amountMinor, contribution.currency)}</p>
								{!contribution.isAnonymous && contribution.supporterEmail && <p className="text-xs text-slate-gray dark:text-[#94a3b8]">{contribution.supporterEmail}</p>}
							</div>
							{contribution.status === "CONFIRMED" && contribution.paymentSource === "STRIPE" && (
								<Link className="min-h-11 shrink-0 rounded-md border border-error-red px-4 py-2 text-sm font-medium text-error-red hover:bg-error-red/5" to={`${appPaths.organization(organizationId, "financial-operations")}?targetType=CONTRIBUTION&targetId=${contribution.id}`}>
									Preview refund
								</Link>
							)}
						</li>
					))}
				</ul>
			)}

			<Pagination
				page={page}
				size={size}
				totalElements={data.totalElements}
				onPageChange={setPage}
				onSizeChange={(value) => { setSize(value); setPage(0); }}
			/>
		</div>
	);
}
