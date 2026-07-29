import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { ApiError } from "../../lib/apiError";
import { useOrderFulfillment, useOrders, useRefundOrder } from "./api";

export function OrderList({ organizationId, storeId }: { organizationId: string; storeId: string }) {
	const { data, isLoading, isError, refetch } = useOrders(organizationId, storeId);
	const refundOrder = useRefundOrder(organizationId, storeId);
	const [refundError, setRefundError] = useState<string | null>(null);

	if (isLoading) return <LoadingState label="Loading orders…" />;
	if (isError) return <ErrorState message="Could not load orders." onRetry={() => refetch()} />;
	if (!data || data.items.length === 0) {
		return <EmptyState title="No confirmed orders yet" description="Confirmed orders will appear here once a supporter checks out." />;
	}

	async function handleRefund(orderId: string) {
		setRefundError(null);
		try {
			await refundOrder.mutateAsync(orderId);
		} catch (e) {
			setRefundError(e instanceof ApiError ? e.message : "Could not refund this order. Please try again.");
		}
	}

	return (
		<div className="flex flex-col gap-2">
			{refundError && (
				<p role="alert" className="text-sm text-error-red">
					{refundError}
				</p>
			)}
			<ul className="flex flex-col gap-2" aria-label="Confirmed orders">
				{data.items.map((order) => (
					<li key={order.id} className="rounded-lg border border-slate-gray/20 bg-pure-white p-3">
						<div className="flex items-center justify-between">
							<div>
								<p className="font-medium text-navy">
									{order.supporterName ?? "Anonymous supporter"}
									{order.status === "REFUNDED" && (
										<span className="ml-2 rounded-full bg-ice-white px-2 py-0.5 text-xs font-medium text-slate-gray">Refunded</span>
									)}
								</p>
								<p className="text-sm text-slate-gray">{order.supporterEmail}</p>
							</div>
							<div className="flex items-center gap-3">
								<OrderFulfillmentBadge organizationId={organizationId} orderId={order.id} />
								{order.status === "CONFIRMED" && (
									<Button
										type="button"
										variant="danger"
										onClick={() => handleRefund(order.id)}
										disabled={refundOrder.isPending}
									>
										{refundOrder.isPending ? "Refunding…" : "Refund"}
									</Button>
								)}
							</div>
						</div>
					</li>
				))}
			</ul>
		</div>
	);
}

function OrderFulfillmentBadge({ organizationId, orderId }: { organizationId: string; orderId: string }) {
	const { data } = useOrderFulfillment(organizationId, orderId);
	if (!data) return <span className="text-xs text-slate-gray">Fulfillment pending</span>;
	const label = data.status === "DRAFT_CREATED" ? "Submitted to Printify" : data.status === "FAILED" ? "Fulfillment failed" : "Not submitted";
	const color = data.status === "DRAFT_CREATED" ? "text-green-600" : data.status === "FAILED" ? "text-error-red" : "text-slate-gray";
	return <span className={`text-xs font-medium ${color}`}>{label}</span>;
}
