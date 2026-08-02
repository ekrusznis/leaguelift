import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { useOrderFulfillment, useOrders, useRefundOrder } from "./api";
import { FulfillmentOperationsPanel } from "./FulfillmentOperationsPanel";
import type { FulfillmentStatus } from "./types";

export function OrderList({ organizationId, storeId }: { organizationId: string; storeId: string }) {
	const { data, isLoading, isError, refetch } = useOrders(organizationId, storeId);
	const refundOrder = useRefundOrder(organizationId, storeId);
	const [refundError, setRefundError] = useState<string | null>(null);
	const [expandedOrderId, setExpandedOrderId] = useState<string | null>(null);

	if (isLoading) return <LoadingState label="Loading orders…" />;
	if (isError) return <ErrorState message="Could not load orders." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="No confirmed orders yet" description="Confirmed orders will appear here once a supporter checks out." />;
	}

	async function handleRefund(orderId: string) {
		setRefundError(null);
		try {
			await refundOrder.mutateAsync(orderId);
		} catch (cause) {
			setRefundError(cause instanceof ApiError ? cause.message : "Could not refund this order. Please try again.");
		}
	}

	return (
		<div className="flex flex-col gap-2">
			{refundError && <p role="alert" className="text-sm text-error-red">{refundError}</p>}
			<ul className="flex flex-col gap-2" aria-label="Confirmed orders">
				{data.items.map((order) => {
					const isExpanded = expandedOrderId === order.id;
					return (
						<li key={order.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
							<div className="flex flex-wrap items-center justify-between gap-3">
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy">
										{order.supporterName ?? "Anonymous supporter"}
										{order.paymentSource === "OFFLINE" && <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">Recorded offline</span>}
									{order.status === "REFUNDED" && <span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">Refunded</span>}
									</p>
									<p className="text-sm text-slate-gray">{order.supporterEmail ?? "No email recorded"}</p>
								</div>
								<div className="flex shrink-0 flex-wrap items-center gap-3">
									<OrderFulfillmentBadge organizationId={organizationId} orderId={order.id} />
									<Button type="button" variant="secondary" aria-expanded={isExpanded} onClick={() => setExpandedOrderId(isExpanded ? null : order.id)}>
										{isExpanded ? "Close operations" : "Manage fulfillment"}
									</Button>
									{order.status === "CONFIRMED" && order.paymentSource === "STRIPE" && (
										<Button type="button" variant="danger" onClick={() => handleRefund(order.id)} disabled={refundOrder.isPending}>
											{refundOrder.isPending ? "Refunding…" : "Refund"}
										</Button>
									)}
								</div>
							</div>
							{isExpanded && <FulfillmentOperationsPanel organizationId={organizationId} storeId={storeId} orderId={order.id} />}
						</li>
					);
				})}
			</ul>
		</div>
	);
}

function OrderFulfillmentBadge({ organizationId, orderId }: { organizationId: string; orderId: string }) {
	const { data } = useOrderFulfillment(organizationId, orderId);
	if (!data) return <span className="text-xs text-slate-gray">Fulfillment pending</span>;
	const critical = data.status === "FAILED" || data.status === "NEEDS_ATTENTION";
	const positive = data.status === "SHIPPED" || data.status === "DELIVERED";
	return (
		<span className={`shrink-0 rounded-full px-2 py-1 text-xs font-medium ${critical ? "bg-error-red/10 text-error-red" : positive ? "bg-victory-green/10 text-green-700" : "bg-slate-gray/10 text-slate-gray"}`}>
			{formatStatus(data.status)}
		</span>
	);
}

function formatStatus(status: FulfillmentStatus) {
	return status.toLowerCase().replaceAll("_", " ").replace(/^./, (character) => character.toUpperCase());
}
