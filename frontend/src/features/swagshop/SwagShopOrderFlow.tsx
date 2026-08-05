import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useContexts } from "../../authorization/api";
import { Capabilities } from "../../authorization/capabilityConstants";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import { useParticipants } from "../households/api";
import { useCreateSwagShopOrder, useSwagShopApparelTypes, useTeamRoster } from "../store/api";
import type { PersonalizationPlacement } from "../store/types";

const PLACEMENT_OPTIONS: { value: PersonalizationPlacement; label: string }[] = [
	{ value: "LEFT_CHEST", label: "Left chest" },
	{ value: "RIGHT_CHEST", label: "Right chest" },
	{ value: "BACK", label: "Back" },
];

export function SwagShopOrderFlow() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const contexts = useContexts();
	const apparelTypes = useSwagShopApparelTypes(organizationId ?? "");
	const createOrder = useCreateSwagShopOrder(organizationId ?? "");

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
			});
			window.location.href = result.checkoutUrl;
		} catch {
			setSubmitError("We couldn't start checkout. Please try again.");
		}
	}

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={`/app/organizations/${organizationId}`} className="mb-2 inline-block text-sm text-azure-blue hover:underline">
					← Back to organization
				</Link>
				<img src="/demo-assets/swagshop/swagshoplogo.png" alt="Swag Shop" className="h-16 w-auto" />
				<p className="mt-2 text-slate-gray">Order personalized team apparel — the team logo is added automatically.</p>
			</div>

			{teams.length > 1 && (
				<div className="flex flex-col gap-1">
					<label htmlFor="swagshop-team" className="text-sm font-medium text-navy">Ordering for team</label>
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
				<p className="text-slate-gray">No apparel types are set up yet — ask an organization admin or coach to enable the Swag Shop.</p>
			)}

			{apparelTypes.data && apparelTypes.data.length > 0 && (
				<div className="flex flex-col gap-4 rounded-lg border border-slate-gray/20 bg-pure-white p-4">
					<div className="flex flex-col gap-1">
						<label htmlFor="swagshop-product" className="text-sm font-medium text-navy">Apparel type</label>
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
							<label htmlFor="swagshop-variant" className="text-sm font-medium text-navy">Size / color</label>
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

					{combinedParticipants.length > 0 && (
						<div className="flex flex-col gap-1">
							<label htmlFor="swagshop-participant" className="text-sm font-medium text-navy">Athlete</label>
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
						<p className="text-sm text-slate-gray">This apparel type isn&rsquo;t set up for name/number personalization yet — you can still order it as-is.</p>
					)}

					{selectedProduct && canPersonalize && (
						<div className="flex flex-col gap-3 rounded-md bg-ice-white p-3">
							<label className="flex items-center gap-2 text-sm font-medium text-navy">
								<input type="checkbox" checked={wantsPersonalization} onChange={(e) => setWantsPersonalization(e.target.checked)} className="h-4 w-4" />
								Add name and/or number
							</label>
							{wantsPersonalization && (
								<div className="flex flex-wrap gap-3">
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-name" className="text-sm font-medium text-navy">Name</label>
										<input id="swagshop-name" type="text" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
									</div>
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-number" className="text-sm font-medium text-navy">Number</label>
										<input id="swagshop-number" type="text" maxLength={20} value={number} onChange={(e) => setNumber(e.target.value)} className="min-h-11 w-24 rounded-md border border-slate-gray/30 px-3 py-2" />
									</div>
									<div className="flex flex-col gap-1">
										<label htmlFor="swagshop-placement" className="text-sm font-medium text-navy">Placement</label>
										<select id="swagshop-placement" value={placement} onChange={(e) => setPlacement(e.target.value as PersonalizationPlacement)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
											{PLACEMENT_OPTIONS.map((opt) => (
												<option key={opt.value} value={opt.value}>{opt.label}</option>
											))}
										</select>
									</div>
								</div>
							)}
							<p className="text-xs text-slate-gray">Team logo will be printed on the front. {wantsPersonalization ? `Name/number placement: ${PLACEMENT_OPTIONS.find((o) => o.value === placement)?.label}.` : ""}</p>
						</div>
					)}

					{selectedVariant && (
						<p className="text-lg font-semibold text-navy">{formatMoneyMinorUnits(selectedVariant.priceMinor, selectedVariant.currency)}</p>
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
