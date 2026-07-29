import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useOrderFulfillment, useOrders } from "./api";

export function OrderList({ organizationId, storeId }: { organizationId: string; storeId: string }) {
	const { data, isLoading, isError, refetch } = useOrders(organizationId, storeId);

	if (isLoading) return <LoadingState label="Loading orders…" />;
	if (isError) return <ErrorState message="Could not load orders." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="No confirmed orders yet" description="Confirmed orders will appear here once a supporter checks out." />;
	}

	return (
		<ul className="flex flex-col gap-2" aria-label="Confirmed orders">
			{data.items.map((order) => (
				<li key={order.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
					<div className="flex items-center justify-between">
						<div>
							<p className="font-medium text-navy">{order.supporterName ?? "Anonymous supporter"}</p>
							<p className="text-sm text-slate-gray">{order.supporterEmail}</p>
						</div>
						<OrderFulfillmentBadge organizationId={organizationId} orderId={order.id} />
					</div>
				</li>
			))}
		</ul>
	);
}

function OrderFulfillmentBadge({ organizationId, orderId }: { organizationId: string; orderId: string }) {
	const { data } = useOrderFulfillment(organizationId, orderId);
	if (!data) return <span className="text-xs text-slate-gray">Fulfillment pending</span>;
	const label = data.status === "DRAFT_CREATED" ? "Submitted to Printify" : data.status === "FAILED" ? "Fulfillment failed" : "Not submitted";
	const color = data.status === "DRAFT_CREATED" ? "text-green-600" : data.status === "FAILED" ? "text-error-red" : "text-slate-gray";
	return <span className={`text-xs font-medium ${color}`}>{label}</span>;
}
