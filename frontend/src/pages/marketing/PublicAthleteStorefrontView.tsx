import { useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatMoneyMinorUnits } from "../../lib/money";
import {
	useAthleteStorefrontOrderStatus,
	useCreateAthleteStorefrontOrderCheckout,
	usePublicAthleteStorefront,
	type AthleteStorefrontProductPublic,
} from "../../features/store/athleteStorefrontApi";
import { SwagPersonalizationPreview } from "../../features/swagshop/SwagPersonalizationPreview";
import type { PersonalizationPlacement, SwagLogoSize } from "../../features/store/types";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";

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

/** Order confirmation is authoritative via the Stripe webhook, not this poll — see OrderService.confirmFromWebhook. */
function OrderReturnPanel({ slug, orderId }: { slug: string; orderId: string }) {
	const { data: order, isLoading, isError } = useAthleteStorefrontOrderStatus(slug, orderId);

	if (isLoading) {
		return (
			<div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-6 text-center">
				<LoadingState label="Confirming your order…" />
			</div>
		);
	}

	if (isError || !order) {
		return (
			<div className="mt-6 rounded-xl border border-error-red/30 bg-error-red/10 p-6 text-center text-error-red">
				We couldn&rsquo;t look up your order. If you completed checkout, it may still be processing — check back shortly.
			</div>
		);
	}

	if (order.status === "CONFIRMED") {
		return (
			<div className="mt-6 rounded-xl border border-green-500/30 bg-green-500/10 p-6 text-center">
				<p className="font-heading text-xl font-bold text-white">Thank you for your order!</p>
				<p className="mt-2 text-slate-300">We&rsquo;ll be in touch with fulfillment and shipping updates.</p>
			</div>
		);
	}

	if (order.status === "CANCELED") {
		return (
			<div className="mt-6 rounded-xl border border-gold-500/30 bg-gold-500/10 p-6 text-center text-gold-500">
				This order was canceled before it completed.
			</div>
		);
	}

	return (
		<div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-6 text-center">
			<LoadingState label="Confirming your order…" />
			<p className="mt-2 text-sm text-slate-400">This can take a few seconds after checkout.</p>
		</div>
	);
}

export function PublicAthleteStorefrontView() {
	const { slug } = useParams<{ slug: string }>();
	const [searchParams] = useSearchParams();
	const checkoutStatus = searchParams.get("status");
	const returnedOrderId = searchParams.get("orderId");
	const { data: storefront, isLoading, isError } = usePublicAthleteStorefront(slug ?? "");
	const createCheckout = useCreateAthleteStorefrontOrderCheckout(slug ?? "");

	const [productId, setProductId] = useState("");
	const [variantId, setVariantId] = useState("");
	const [wantsPersonalization, setWantsPersonalization] = useState(false);
	const [name, setName] = useState("");
	const [number, setNumber] = useState("");
	const [placement, setPlacement] = useState<PersonalizationPlacement>("BACK");
	const [logoSize, setLogoSize] = useState<SwagLogoSize>("STANDARD");
	const [supporterName, setSupporterName] = useState("");
	const [supporterEmail, setSupporterEmail] = useState("");
	const [submitError, setSubmitError] = useState<string | null>(null);

	if (isLoading) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<LoadingState label="Loading storefront…" />
			</div>
		);
	}

	if (isError || !storefront) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<ErrorState message="This storefront could not be found or is not currently open." />
			</div>
		);
	}

	const selectedProduct: AthleteStorefrontProductPublic | undefined = storefront.products.find((item) => item.id === productId);
	const selectedVariant = selectedProduct?.variants.find((variant) => variant.id === variantId);
	const canPersonalize = selectedProduct?.hasSwagLogo ?? false;

	async function onSubmit() {
		setSubmitError(null);
		if (!variantId) {
			setSubmitError("Choose an item before ordering.");
			return;
		}
		try {
			const result = await createCheckout.mutateAsync({
				productVariantId: variantId,
				personalizationName: wantsPersonalization && name.trim() ? name.trim() : null,
				personalizationNumber: wantsPersonalization && number.trim() ? number.trim() : null,
				personalizationPlacement: wantsPersonalization ? placement : null,
				personalizationLogoSize: wantsPersonalization ? logoSize : null,
				supporterName: supporterName.trim() || null,
				supporterEmail: supporterEmail.trim() || null,
			});
			window.location.href = result.checkoutUrl;
		} catch {
			setSubmitError("We couldn't start checkout. Please try again.");
		}
	}

	const heading = storefront.teamName ? `${storefront.athletePublicLabel} — ${storefront.teamName}` : storefront.athletePublicLabel;

	return (
		<>
			<Seo title={heading} description={`Shop for ${storefront.athletePublicLabel} on Rally26.`} />

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-2xl">
					<h1 className="text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">{heading}</h1>
					<p className="mt-2 text-slate-300">{storefront.organizationName}</p>

					{returnedOrderId ? (
						<OrderReturnPanel slug={storefront.slug} orderId={returnedOrderId} />
					) : (
						<>
							{checkoutStatus === "canceled" && (
								<p role="alert" className="mt-6 rounded-md bg-gold-500/10 p-3 text-sm text-gold-500">
									Checkout was canceled — no order was placed.
								</p>
							)}

							{storefront.products.length === 0 && (
								<p className="mt-8 text-slate-300">No products are available yet.</p>
							)}

							{storefront.products.length > 0 && (
								<div className="mt-8 flex flex-col gap-4 rounded-xl border border-white/10 bg-white/5 p-4">
									<div className="flex flex-col gap-1">
										<label htmlFor="storefront-product" className="text-sm font-medium text-white">Item</label>
										<select
											id="storefront-product"
											value={productId}
											onChange={(event) => { setProductId(event.target.value); setVariantId(""); }}
											className="min-h-11 w-full max-w-sm rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white"
										>
											<option value="">Select…</option>
											{storefront.products.map((product) => (
												<option key={product.id} value={product.id}>{product.name}</option>
											))}
										</select>
										{selectedProduct?.description && <p className="text-sm text-slate-300">{selectedProduct.description}</p>}
									</div>

									{selectedProduct && (
										<div className="flex flex-col gap-1">
											<label htmlFor="storefront-variant" className="text-sm font-medium text-white">Size / color</label>
											<select
												id="storefront-variant"
												value={variantId}
												onChange={(event) => setVariantId(event.target.value)}
												className="min-h-11 w-full max-w-sm rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white"
											>
												<option value="">Select…</option>
												{selectedProduct.variants.map((variant) => (
													<option key={variant.id} value={variant.id}>{variant.label} — {formatMoneyMinorUnits(variant.priceMinor, variant.currency)}</option>
												))}
											</select>
										</div>
									)}

									{selectedVariant && (selectedVariant.mockupFrontUrl || selectedVariant.mockupBackUrl) && (
										<SwagPersonalizationPreview
											mockupFrontUrl={selectedVariant.mockupFrontUrl}
											mockupBackUrl={selectedVariant.mockupBackUrl}
											logoPreviewUrl={selectedProduct?.logoPreviewUrl ?? null}
											productName={selectedProduct?.name}
											placement={wantsPersonalization ? placement : null}
											logoSize={logoSize}
											name={wantsPersonalization ? name : ""}
											number={wantsPersonalization ? number : ""}
										/>
									)}

									{selectedProduct && !canPersonalize && (
										<p className="text-sm text-slate-300">This item isn&rsquo;t set up for name/number personalization yet — you can still order it as-is.</p>
									)}

									{selectedProduct && canPersonalize && (
										<div className="flex flex-col gap-3 rounded-md bg-navy-900/40 p-3">
											<label className="flex items-center gap-2 text-sm font-medium text-white">
												<input type="checkbox" checked={wantsPersonalization} onChange={(event) => setWantsPersonalization(event.target.checked)} className="h-4 w-4" />
												Add name and/or number
											</label>
											{wantsPersonalization && (
												<div className="flex flex-wrap gap-3">
													<div className="flex flex-col gap-1">
														<label htmlFor="storefront-name" className="text-sm font-medium text-white">Name</label>
														<input id="storefront-name" type="text" maxLength={60} value={name} onChange={(event) => setName(event.target.value)} className="min-h-11 rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white" />
													</div>
													<div className="flex flex-col gap-1">
														<label htmlFor="storefront-number" className="text-sm font-medium text-white">Number</label>
														<input id="storefront-number" type="text" maxLength={20} value={number} onChange={(event) => setNumber(event.target.value)} className="min-h-11 w-24 rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white" />
													</div>
													<div className="flex flex-col gap-1">
														<label htmlFor="storefront-placement" className="text-sm font-medium text-white">Placement</label>
														<select id="storefront-placement" value={placement} onChange={(event) => setPlacement(event.target.value as PersonalizationPlacement)} className="min-h-11 rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white">
															{PLACEMENT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
														</select>
													</div>
													<div className="flex flex-col gap-1">
														<label htmlFor="storefront-logo-size" className="text-sm font-medium text-white">Logo size</label>
														<select id="storefront-logo-size" value={logoSize} onChange={(event) => setLogoSize(event.target.value as SwagLogoSize)} className="min-h-11 rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white">
															{LOGO_SIZE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
														</select>
													</div>
												</div>
											)}
										</div>
									)}

									{selectedVariant && (
										<p className="text-lg font-semibold text-white">{formatMoneyMinorUnits(selectedVariant.priceMinor, selectedVariant.currency)}</p>
									)}

									{selectedVariant && (
										<div className="flex flex-wrap gap-3">
											<div className="flex flex-col gap-1">
												<label htmlFor="storefront-supporter-name" className="text-sm font-medium text-white">Your name (optional)</label>
												<input id="storefront-supporter-name" type="text" maxLength={120} value={supporterName} onChange={(event) => setSupporterName(event.target.value)} className="min-h-11 rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white" />
											</div>
											<div className="flex flex-col gap-1">
												<label htmlFor="storefront-supporter-email" className="text-sm font-medium text-white">Email for your receipt (optional)</label>
												<input id="storefront-supporter-email" type="email" maxLength={254} value={supporterEmail} onChange={(event) => setSupporterEmail(event.target.value)} className="min-h-11 rounded-md border border-white/20 bg-white/10 px-3 py-2 text-white" />
											</div>
										</div>
									)}

									{submitError && (
										<p role="alert" className="rounded-md bg-error-red/10 p-2 text-sm text-error-red">{submitError}</p>
									)}

									<PrimaryButton type="button" onClick={onSubmit} loading={createCheckout.isPending} disabled={!variantId}>
										Order
									</PrimaryButton>
								</div>
							)}
						</>
					)}

					<SecondaryLightButton to="/" className="mt-8">
						Learn more about Rally26
					</SecondaryLightButton>
				</PageContainer>
			</section>
		</>
	);
}
