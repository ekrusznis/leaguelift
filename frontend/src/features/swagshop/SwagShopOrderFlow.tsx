import { useMemo, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useContexts } from "../../authorization/api";
import { Capabilities } from "../../authorization/capabilityConstants";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useParticipants } from "../households/api";
import { useCreateSwagShopOrder, useMySwagShopOrders, useSwagShopApparelTypes, useTeamRoster } from "../store/api";
import type { PersonalizationPlacement, SwagLogoSize, SwagShopOrderHistoryItem } from "../store/types";
import { SwagPersonalizationPreview } from "./SwagPersonalizationPreview";

const PLACEMENT_OPTIONS: { value: PersonalizationPlacement; label: string }[] = [
	{ value: "LEFT_CHEST", label: "Left chest" },
	{ value: "RIGHT_CHEST", label: "Right chest" },
	{ value: "CENTER_FRONT", label: "Center front" },
	{ value: "BACK", label: "Back" },
];

const LOGO_SIZE_OPTIONS: { value: SwagLogoSize; label: string }[] = [
	{ value: "SMALL", label: "Small" },
	{ value: "STANDARD", label: "Standard" },
	{ value: "LARGE", label: "Large" },
];

export function SwagShopOrderFlow() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const [searchParams] = useSearchParams();
	const checkoutStatus = searchParams.get("status");
	const returnedOrderId = searchParams.get("orderId");
	const contexts = useContexts();
	const apparelTypes = useSwagShopApparelTypes(organizationId ?? "");
	const pastOrders = useMySwagShopOrders(organizationId ?? "");
	const createOrder = useCreateSwagShopOrder(organizationId ?? "");
	const orderFormRef = useRef<HTMLDivElement>(null);

	const orgContexts = useMemo(() => (contexts.data ?? []).filter((c) => c.organizationId === organizationId), [contexts.data, organizationId]);
	const household = orgContexts.find((c) => c.contextType === "HOUSEHOLD");
	const teams = orgContexts.filter((c) => c.contextType === "TEAM" && c.capabilities.includes(Capabilities.TEAM_ORDER_CREATE));

	const [selectedTeamId, setSelectedTeamId] = useState<string | null>(teams[0]?.resourceId ?? null);
	const householdParticipants = useParticipants(organizationId ?? "", household?.resourceId ?? "");
	const teamRoster = useTeamRoster(organizationId ?? "", selectedTeamId);

	const [productId, setProductId] = useState("");
	const [variantId, setVariantId] = useState("");
	const [participantId, setParticipantId] = useState("");
	const [wantsPersonalization, setWantsPersonalization] = useState(false);
	const [name, setName] = useState("");
	const [number, setNumber] = useState("");
	const [placement, setPlacement] = useState<PersonalizationPlacement>("BACK");
	const [logoSize, setLogoSize] = useState<SwagLogoSize>("STANDARD");
	const [submitError, setSubmitError] = useState<string | null>(null);

	if (!organizationId) return <ErrorState message="No organization selected." />;
	if (contexts.isLoading || apparelTypes.isLoading) return <LoadingState label="Loading Swag Shop…" />;
	if (apparelTypes.isError) return <ErrorState message="Could not load the Swag Shop." onRetry={() => apparelTypes.refetch()} />;

	if (!household && teams.length === 0) {
		return <ErrorState message="You don't have access to order from this organization's Swag Shop." />;
	}

	const selectedProduct = apparelTypes.data?.find((item) => item.productId === productId);
	const selectedVariant = selectedProduct?.variants.find((v) => v.id === variantId);
	const canPersonalize = selectedProduct?.hasSwagLogo ?? false;

	const combinedParticipants = [
		...(household ? (householdParticipants.data ?? []).map((p) => ({ id: p.id, label: `${p.firstName} ${p.lastName} (my household)` })) : []),
		...(selectedTeamId ? (teamRoster.data ?? []).map((p) => ({ id: p.id, label: `${p.firstName} ${p.lastName} (team roster)` })) : []),
	];

	async function onSubmit() {
		setSubmitError(null);
		if (!variantId || !participantId) {
			setSubmitError("Choose an apparel type, size, and athlete before ordering.");
			return;
		}
		try {
			const result = await createOrder.mutateAsync({
				productVariantId: variantId,
				participantId,
				personalizationName: wantsPersonalization && name.trim() ? name.trim() : null,
				personalizationNumber: wantsPersonalization && number.trim() ? number.trim() : null,
				personalizationPlacement: wantsPersonalization ? placement : null,
				personalizationLogoSize: wantsPersonalization ? logoSize : null,
			});
			window.location.href = result.checkoutUrl;
		} catch {
			setSubmitError("We couldn't start checkout. Please try again.");
		}
	}

	function reorder(item: SwagShopOrderHistoryItem) {
		setProductId(item.productId);
		setVariantId(item.variantId);
		setParticipantId(item.participantId);
		const hadPersonalization = Boolean(item.personalizationName || item.personalizationNumber);
		setWantsPersonalization(hadPersonalization);
		setName(item.personalizationName ?? "");
		setNumber(item.personalizationNumber ?? "");
		if (item.personalizationPlacement) setPlacement(item.personalizationPlacement);
		if (item.personalizationLogoSize) setLogoSize(item.personalizationLogoSize);
		setSubmitError(null);
		orderFormRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={`/app/organizations/${organizationId}`} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← Back to organization
				</Link>
				<img src="/demo-assets/swagshop/swagshoplogo.png" alt="Swag Shop" className="h-16 w-auto" />
				<p className="mt-2 text-slate-gray dark:text-[#cbd5e1]">Order personalized team apparel — the team logo is added automatically.</p>
			</div>

			{checkoutStatus === "success" && (
				<div role="status" className="rounded-lg border border-green-600/30 bg-green-50 dark:bg-green-950 p-4 text-green-800">
					<p className="font-medium">Order placed!</p>
					<p className="mt-1 text-sm">
						{returnedOrderId ? `Order ${returnedOrderId.slice(0, 8)} is confirmed. ` : ""}
						We&rsquo;ll send apparel to production once the team logo and personalization are ready. Watch for a confirmation email.
					</p>
				</div>
			)}
			{checkoutStatus === "canceled" && (
				<div role="status" className="rounded-lg border border-slate-gray/30 bg-ice-white dark:bg-[#0f172a] p-4 text-slate-gray dark:text-[#cbd5e1]">
					Checkout was canceled — no order was placed.
				</div>
			)}

			<section aria-label="Your past orders" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Your past orders</h2>
				{pastOrders.isLoading && <LoadingState label="Loading your past orders…" />}
				{pastOrders.isError && <ErrorState message="Could not load your past orders." onRetry={() => pastOrders.refetch()} />}
				{pastOrders.data && pastOrders.data.length === 0 && (
					<EmptyState title="No past orders yet" description="Orders you place below will show up here for easy reordering." />
				)}
				{pastOrders.data && pastOrders.data.length > 0 && (
					<ul className="flex flex-col gap-2" aria-label="Past orders list">
						{pastOrders.data.map((order) => (
							<li key={`${order.orderId}-${order.variantId}-${order.participantId}`} className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
								{order.mockupFrontUrl && <img src={order.mockupFrontUrl} alt="" className="h-14 w-14 shrink-0 rounded-md object-cover" />}
								<div className="min-w-0 flex-1">
									<p className="break-words font-medium text-navy dark:text-[#f8fafc]">{order.productName} — {order.variantLabel}</p>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
										For {order.participantName}
										{order.personalizationName ? ` · ${order.personalizationName}` : ""}
										{order.personalizationNumber ? ` #${order.personalizationNumber}` : ""}
										{" · "}{formatMoneyMinorUnits(order.unitPriceMinor, order.currency)}
										{" · "}{new Date(order.confirmedAt).toLocaleDateString()}
									</p>
								</div>
								<Button type="button" variant="secondary" disabled={!order.isReorderable} onClick={() => reorder(order)}>
									{order.isReorderable ? "Reorder" : "No longer available"}
								</Button>
							</li>
						))}
					</ul>
				)}
			</section>

			{teams.length > 1 && (
				<div className="flex flex-col gap-1">
					<label htmlFor="swagshop-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Ordering for team</label>
					<select
						id="swagshop-team"
						value={selectedTeamId ?? ""}
						onChange={(e) => setSelectedTeamId(e.target.value || null)}
						className="min-h-11 w-full max-w-sm rounded-md border border-slate-gray/30 px-3 py-2"
					>
						{teams.map((t) => (
							<option key={t.resourceId ?? ""} value={t.resourceId ?? ""}>{t.label}</option>
						))}
					</select>
				</div>
			)}

			{apparelTypes.data && apparelTypes.data.length === 0 && (
				<p className="text-slate-gray dark:text-[#cbd5e1]">No apparel types are set up yet — ask an organization admin or coach to enable the Swag Shop.</p>
			)}

			{apparelTypes.data && apparelTypes.data.length > 0 && (
				<div ref={orderFormRef} className="flex flex-col gap-4 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-4">
					<div className="flex flex-col gap-1">
						<label htmlFor="swagshop-product" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Apparel type</label>
						<select
							id="swagshop-product"
							value={productId}
							onChange={(e) => { setProductId(e.target.value); setVariantId(""); }}
							className="min-h-11 w-full max-w-sm rounded-md border border-slate-gray/30 px-3 py-2"
						>
							<option value="">Select…</option>
							{apparelTypes.data.map((item) => (
								<option key={item.productId} value={item.productId}>{item.storeName} — {item.productName}</option>
							))}
						</select>
					</div>

					{selectedProduct && (
						<div className="flex flex-col gap-1">
							<label htmlFor="swagshop-variant" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Size / color</label>
							<select
								id="swagshop-variant"
								value={variantId}
								onChange={(e) => setVariantId(e.target.value)}
								className="min-h-11 w-full max-w-sm rounded-md border border-slate-gray/30 px-3 py-2"
							>
								<option value="">Select…</option>
								{selectedProduct.variants.map((v) => (
									<option key={v.id} value={v.id}>{v.label} — {formatMoneyMinorUnits(v.priceMinor, v.currency)}</option>
								))}
							</select>
						</div>
					)}

					{selectedVariant && (selectedVariant.mockupFrontUrl || selectedVariant.mockupBackUrl) && (
						<SwagPersonalizationPreview
							mockupFrontUrl={selectedVariant.mockupFrontUrl}
							mockupBackUrl={selectedVariant.mockupBackUrl}
							logoPreviewUrl={selectedProduct?.logoPreviewUrl ?? null}
							productName={selectedProduct?.productName}
							placement={wantsPersonalization ? placement : null}
							logoSize={logoSize}
							name={wantsPersonalization ? name : ""}
							number={wantsPersonalization ? number : ""}
						/>
					)}

					{combinedParticipants.length > 0 && (
						<div className="flex flex-col gap-1">
							<label htmlFor="swagshop-participant" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Athlete</label>
							<select
								id="swagshop-participant"
								value={participantId}
								onChange={(e) => setParticipantId(e.target.value)}
								className="min-h-11 w-full max-w-sm rounded-md border border-slate-gray/30 px-3 py-2"
							>
								<option value="">Select…</option>
								{combinedParticipants.map((p) => (
									<option key={p.id} value={p.id}>{p.label}</option>
								))}
							</select>
						</div>
					)}

					{selectedProduct && !canPersonalize && (
						<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">This apparel type isn&rsquo;t set up for name/number personalization yet — you can still order it as-is.</p>
					)}

					{selectedProduct && canPersonalize && (
						<div className="flex flex-col gap-3 rounded-md bg-ice-white dark:bg-[#0f172a] p-3">
							<label className="flex items-center gap-2 text-sm font-medium text-navy dark:text-[#f8fafc]">
								<input type="checkbox" checked={wantsPersonalization} onChange={(e) => setWantsPersonalization(e.target.checked)} className="h-4 w-4" />
								Add name and/or number
							</label>
							{wantsPersonalization && (
								<div className="flex flex-wrap gap-3">
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-name" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Name</label>
										<input id="swagshop-name" type="text" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
									</div>
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-number" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Number</label>
										<input id="swagshop-number" type="text" maxLength={20} value={number} onChange={(e) => setNumber(e.target.value)} className="min-h-11 w-24 rounded-md border border-slate-gray/30 px-3 py-2" />
									</div>
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-placement" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Placement</label>
										<select id="swagshop-placement" value={placement} onChange={(e) => setPlacement(e.target.value as PersonalizationPlacement)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
											{PLACEMENT_OPTIONS.map((opt) => (
												<option key={opt.value} value={opt.value}>{opt.label}</option>
											))}
										</select>
									</div>
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-logo-size" className="text-sm font-medium text-navy dark:text-[#f8fafc]">Logo size</label>
										<select id="swagshop-logo-size" value={logoSize} onChange={(e) => setLogoSize(e.target.value as SwagLogoSize)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
											{LOGO_SIZE_OPTIONS.map((opt) => (
												<option key={opt.value} value={opt.value}>{opt.label}</option>
											))}
										</select>
									</div>
								</div>
							)}
							<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">Team logo will be printed on the front. {wantsPersonalization ? `Name/number placement: ${PLACEMENT_OPTIONS.find((o) => o.value === placement)?.label}.` : ""}</p>
						</div>
					)}

					{selectedVariant && (
						<p className="text-lg font-semibold text-navy dark:text-[#f8fafc]">{formatMoneyMinorUnits(selectedVariant.priceMinor, selectedVariant.currency)}</p>
					)}

					{submitError && <p role="alert" className="text-sm text-error-red">{submitError}</p>}

					<Button type="button" onClick={onSubmit} disabled={createOrder.isPending || !variantId || !participantId}>
						{createOrder.isPending ? "Starting checkout…" : "Order"}
					</Button>
				</div>
			)}
		</div>
	);
}
