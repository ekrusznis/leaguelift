import { useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useCreateOrderCheckout, useOrderStatus, usePublicStore } from "../../features/store/api";
import type { CartLine, PublicProduct } from "../../features/store/types";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";
import { formatMoneyMinorUnits } from "../../lib/money";

/** Order confirmation is authoritative via the Stripe webhook, not this poll — see OrderService.confirmFromWebhook. A brief "processing" flash before it flips to CONFIRMED is expected. */
function OrderReturnPanel({ slug, orderId }: { slug: string; orderId: string }) {
	const { data: order, isLoading, isError } = useOrderStatus(slug, orderId);

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

function ProductCard({ product, cart, onChangeQuantity }: { product: PublicProduct; cart: CartLine[]; onChangeQuantity: (variantId: string, quantity: number) => void }) {
	return (
		<div className="rounded-xl border border-white/10 bg-white/5 p-4">
			<div className="flex gap-4">
				{product.designUrl && (
					<img src={product.designUrl} alt={product.name} className="h-20 w-20 shrink-0 rounded-md object-cover" />
				)}
				<div className="flex-1">
					<p className="font-heading text-lg font-bold text-white">{product.name}</p>
					{product.description && <p className="mt-1 text-sm text-slate-300">{product.description}</p>}
				</div>
			</div>
			<ul className="mt-3 flex flex-col gap-2">
				{product.variants.map((variant) => {
					const quantity = cart.find((line) => line.productVariantId === variant.id)?.quantity ?? 0;
					return (
						<li key={variant.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-navy-900/40 px-3 py-2 text-sm text-white">
							<span className="min-w-0 flex-1 break-words">
								{variant.label} — {formatMoneyMinorUnits(variant.priceMinor, variant.currency)}
							</span>
							<label className="flex shrink-0 items-center gap-2">
								<span className="sr-only">Quantity for {variant.label}</span>
								<input
									type="number"
									min={0}
									step={1}
									value={quantity}
									onChange={(event) => onChangeQuantity(variant.id, Math.max(0, Number(event.target.value) || 0))}
									className="min-h-9 w-16 rounded-md border border-white/20 bg-white/10 px-2 py-1 text-white"
								/>
							</label>
						</li>
					);
				})}
			</ul>
		</div>
	);
}

export function PublicStoreView() {
	const { slug } = useParams<{ slug: string }>();
	const [searchParams] = useSearchParams();
	const { data: store, isLoading, isError } = usePublicStore(slug ?? "");
	const createCheckout = useCreateOrderCheckout(slug ?? "");
	const [cart, setCart] = useState<CartLine[]>([]);
	const [checkoutError, setCheckoutError] = useState(false);
	const orderId = searchParams.get("orderId");
	const checkoutCanceled = searchParams.get("checkoutCanceled") === "1";

	if (isLoading) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<LoadingState label="Loading store…" />
			</div>
		);
	}

	if (isError || !store) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<ErrorState message="This store could not be found or is not currently open." />
			</div>
		);
	}

	function onChangeQuantity(variantId: string, quantity: number) {
		setCart((prev) => {
			const withoutVariant = prev.filter((line) => line.productVariantId !== variantId);
			return quantity > 0 ? [...withoutVariant, { productVariantId: variantId, quantity }] : withoutVariant;
		});
	}

	const totalItems = cart.reduce((sum, line) => sum + line.quantity, 0);

	async function onCheckout() {
		setCheckoutError(false);
		const returnBase = `${window.location.origin}${window.location.pathname}`;
		try {
			const result = await createCheckout.mutateAsync({
				items: cart,
				successUrl: `${returnBase}?orderId={ORDER_ID}`,
				cancelUrl: `${returnBase}?checkoutCanceled=1`,
			});
			window.location.href = result.checkoutUrl;
		} catch {
			setCheckoutError(true);
		}
	}

	return (
		<>
			<Seo title={store.name} description={`Shop ${store.name} on LeagueLift.`} />

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-2xl">
					<h1 className="text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">{store.name}</h1>

					{orderId ? (
						<OrderReturnPanel slug={store.slug} orderId={orderId} />
					) : (
						<>
							{checkoutCanceled && (
								<p role="alert" className="mt-6 rounded-md bg-gold-500/10 p-3 text-sm text-gold-500">
									Checkout was canceled — no order was placed.
								</p>
							)}

							{store.products.length === 0 && (
								<p className="mt-8 text-slate-300">No products are available yet.</p>
							)}

							<div className="mt-8 flex flex-col gap-4">
								{store.products.map((product) => (
									<ProductCard key={product.id} product={product} cart={cart} onChangeQuantity={onChangeQuantity} />
								))}
							</div>

							{totalItems > 0 && (
								<div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-4">
									{checkoutError && (
										<p role="alert" className="mb-3 rounded-md bg-error-red/10 p-2 text-sm text-error-red">
											We couldn&rsquo;t start checkout. Please try again.
										</p>
									)}
									<PrimaryButton type="button" onClick={onCheckout} loading={createCheckout.isPending}>
										Checkout ({totalItems} item{totalItems !== 1 ? "s" : ""})
									</PrimaryButton>
								</div>
							)}
						</>
					)}

					<SecondaryLightButton to="/" className="mt-8">
						Learn more about LeagueLift
					</SecondaryLightButton>
				</PageContainer>
			</section>
		</>
	);
}
