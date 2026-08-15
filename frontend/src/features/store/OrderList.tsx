import { useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { FulfillmentOperationsPanel } from "./FulfillmentOperationsPanel";
import { useOrderSearch, type OrderSearchSort } from "./searchApi";
import type { FulfillmentStatus } from "./types";

const FULFILLMENT_STATUSES: FulfillmentStatus[] = [
	"NOT_SUBMITTED",
	"DRAFT_CREATED",
	"FAILED",
	"READY",
	"IN_PRODUCTION",
	"SHIPPED",
	"DELIVERED",
	"NEEDS_ATTENTION",
	"CANCELED",
];

export function OrderList({ organizationId, storeId }: { organizationId: string; storeId: string }) {
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<"CONFIRMED" | "REFUNDED" | "">("");
	const [paymentSource, setPaymentSource] = useState<"STRIPE" | "OFFLINE" | "">("");
	const [fulfillmentStatus, setFulfillmentStatus] = useState<FulfillmentStatus | "">("");
	const [sort, setSort] = useState<OrderSearchSort>("NEWEST");
	const [expandedOrderId, setExpandedOrderId] = useState<string | null>(null);

	const { data, isLoading, isError, refetch } = useOrderSearch(organizationId, storeId, {
		page,
		size,
		q: query,
		status,
		paymentSource,
		fulfillmentStatus,
		sort,
	});

	const hasFilters = !!status || !!paymentSource || !!fulfillmentStatus;

	if (isLoading) return <LoadingState label="Loading orders…" />;
	if (isError || !data) return <ErrorState message="Could not load orders." onRetry={() => refetch()} />;

	return (
		<div className="flex flex-col gap-4">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search supporter, email, address, or order ID"
				resultCount={data.totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "NEWEST", label: "Newest" },
					{ value: "OLDEST", label: "Oldest" },
					{ value: "SUPPORTER_ASC", label: "Supporter A–Z" },
					{ value: "STATUS_ASC", label: "Payment status" },
					{ value: "FULFILLMENT_ASC", label: "Fulfillment status" },
				]}
				onSortChange={(value) => {
					setSort(value as OrderSearchSort);
					setPage(0);
				}}
				hasActiveFilters={hasFilters}
				onClear={() => {
					setQuery("");
					setStatus("");
					setPaymentSource("");
					setFulfillmentStatus("");
					setSort("NEWEST");
					setPage(0);
				}}
				filters={
					<>
						<select
							aria-label="Filter order payment status"
							value={status}
							onChange={(event) => {
								setStatus(event.target.value as typeof status);
								setPage(0);
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All payment statuses</option>
							<option value="CONFIRMED">Confirmed</option>
							<option value="REFUNDED">Refunded</option>
						</select>
						<select
							aria-label="Filter order payment source"
							value={paymentSource}
							onChange={(event) => {
								setPaymentSource(event.target.value as typeof paymentSource);
								setPage(0);
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All payment sources</option>
							<option value="STRIPE">Online card</option>
							<option value="OFFLINE">Recorded offline</option>
						</select>
						<select
							aria-label="Filter fulfillment status"
							value={fulfillmentStatus}
							onChange={(event) => {
								setFulfillmentStatus(event.target.value as FulfillmentStatus | "");
								setPage(0);
							}}
							className="min-h-11 rounded-lg border border-slate-300 bg-white px-3 text-navy dark:border-[#334155] dark:bg-[#0f172a] dark:text-[#f8fafc]"
						>
							<option value="">All fulfillment statuses</option>
							{FULFILLMENT_STATUSES.map((value) => (
								<option key={value} value={value}>
									{formatStatus(value)}
								</option>
							))}
						</select>
					</>
				}
			/>

			{data.items.length === 0 ? (
				<EmptyState
					title={query.trim() || hasFilters ? "No results found" : "No confirmed orders yet"}
					description={
						query.trim() || hasFilters
							? "Try changing your search or filters."
							: "Confirmed orders will appear here once a supporter checks out."
					}
				/>
			) : (
				<ul className="flex flex-col gap-2" aria-label="Confirmed orders">
					{data.items.map((order) => {
						const isExpanded = expandedOrderId === order.id;
						return (
							<li key={order.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3 dark:bg-[#111827]">
								<div className="flex flex-wrap items-center justify-between gap-3">
									<div className="min-w-0 flex-1">
										<p className="break-words font-medium text-navy dark:text-[#f8fafc]">
											{order.supporterName ?? "Anonymous supporter"}
											{order.paymentSource === "OFFLINE" && (
												<span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
													Recorded offline
												</span>
											)}
											{order.status === "REFUNDED" && (
												<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray dark:bg-[#0f172a] dark:text-[#cbd5e1]">
													Refunded
												</span>
											)}
										</p>
										<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
											{order.supporterEmail ?? "No email recorded"}
										</p>
										<p className="mt-1 text-xs text-slate-gray dark:text-[#94a3b8]">
											Order {order.id.slice(0, 8)} ·{" "}
											{order.confirmedAt
												? new Date(order.confirmedAt).toLocaleString()
												: new Date(order.createdAt).toLocaleString()}
										</p>
									</div>
									<div className="flex shrink-0 flex-wrap items-center gap-3">
										<FulfillmentBadge status={order.fulfillmentStatus} />
										<Button
											type="button"
											variant="secondary"
											aria-expanded={isExpanded}
											onClick={() => setExpandedOrderId(isExpanded ? null : order.id)}
										>
											{isExpanded ? "Close operations" : "Manage fulfillment"}
										</Button>
										{order.status === "CONFIRMED" && order.paymentSource === "STRIPE" && (
											<Link
												className="min-h-11 rounded-md border border-error-red px-4 py-2 text-sm font-medium text-error-red hover:bg-error-red/5"
												to={`${appPaths.organization(organizationId, "financial-operations")}?targetType=ORDER&targetId=${order.id}`}
											>
												Preview refund
											</Link>
										)}
									</div>
								</div>
								{isExpanded && (
									<FulfillmentOperationsPanel
										organizationId={organizationId}
										storeId={storeId}
										orderId={order.id}
									/>
								)}
							</li>
						);
					})}
				</ul>
			)}

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
		</div>
	);
}

function FulfillmentBadge({ status }: { status: FulfillmentStatus | null }) {
	if (!status) return <span className="text-xs text-slate-gray dark:text-[#cbd5e1]">Fulfillment pending</span>;
	const critical = status === "FAILED" || status === "NEEDS_ATTENTION";
	const positive = status === "SHIPPED" || status === "DELIVERED";
	return (
		<span
			className={`shrink-0 rounded-full px-2 py-1 text-xs font-medium ${
				critical
					? "bg-error-red/10 text-error-red"
					: positive
						? "bg-victory-green/10 text-green-700"
						: "bg-slate-gray/10 text-slate-gray dark:text-[#cbd5e1]"
			}`}
		>
			{formatStatus(status)}
		</span>
	);
}

function formatStatus(status: FulfillmentStatus) {
	return status.toLowerCase().replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase());
}
