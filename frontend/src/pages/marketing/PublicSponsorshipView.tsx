import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { useParams, useSearchParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import {
	useCreateSponsorshipCheckout,
	usePublicSponsorDirectory,
	usePublicSponsorshipPackages,
	useSponsorshipStatus,
} from "../../features/sponsorship/api";
import { createSponsorshipCheckoutSchema } from "../../features/sponsorship/schema";
import type { PublicSponsorshipPackage } from "../../features/sponsorship/types";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";
import { formatMoneyMinorUnits } from "../../lib/money";

/** Confirmation is authoritative via the Stripe webhook, not this poll — see SponsorshipService.confirmFromWebhook. A brief "processing" flash before it flips to CONFIRMED is expected and fine. */
function SponsorshipReturnPanel({ packageId, sponsorshipId }: { packageId: string; sponsorshipId: string }) {
	const { data: sponsorship, isLoading, isError } = useSponsorshipStatus(packageId, sponsorshipId);

	if (isLoading) {
		return (
			<div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-6 text-center">
				<LoadingState label="Confirming your sponsorship…" />
			</div>
		);
	}

	if (isError || !sponsorship) {
		return (
			<div className="mt-6 rounded-xl border border-error-red/30 bg-error-red/10 p-6 text-center text-error-red">
				We couldn&rsquo;t look up your sponsorship. If you completed checkout, it may still be processing — check back shortly.
			</div>
		);
	}

	if (sponsorship.status === "CONFIRMED") {
		return (
			<div className="mt-6 rounded-xl border border-green-500/30 bg-green-500/10 p-6 text-center">
				<p className="font-heading text-xl font-bold text-white">Thank you for your sponsorship!</p>
				<p className="mt-2 text-slate-300">
					Your sponsorship of {formatMoneyMinorUnits(sponsorship.amountMinor, sponsorship.currency)} has been confirmed.
				</p>
			</div>
		);
	}

	return (
		<div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-6 text-center">
			<LoadingState label="Confirming your sponsorship…" />
			<p className="mt-2 text-sm text-slate-400">This can take a few seconds after checkout.</p>
		</div>
	);
}

function SponsorshipPurchaseForm({ sponsorshipPackage }: { sponsorshipPackage: PublicSponsorshipPackage }) {
	const createCheckout = useCreateSponsorshipCheckout(sponsorshipPackage.id);
	const {
		register,
		handleSubmit,
		formState: { errors, isSubmitting },
	} = useForm<
		z.input<typeof createSponsorshipCheckoutSchema>,
		unknown,
		z.output<typeof createSponsorshipCheckoutSchema>
	>({
		resolver: zodResolver(createSponsorshipCheckoutSchema),
		defaultValues: { sponsorName: "", sponsorContactEmail: "" },
	});

	const onSubmit = handleSubmit(async (values) => {
		const returnBase = `${window.location.origin}${window.location.pathname}`;
		const result = await createCheckout.mutateAsync({
			...values,
			successUrl: `${returnBase}?packageId=${sponsorshipPackage.id}&sponsorshipId={SPONSORSHIP_ID}`,
			cancelUrl: `${returnBase}?checkoutCanceled=1`,
		});
		window.location.href = result.checkoutUrl;
	});

	return (
		<form onSubmit={onSubmit} className="mt-3 flex flex-col gap-3 rounded-lg border border-white/10 bg-navy-900/40 p-4" noValidate>
			{createCheckout.isError && (
				<p role="alert" className="rounded-md bg-error-red/10 p-2 text-sm text-error-red">
					We couldn&rsquo;t start checkout. Please try again.
				</p>
			)}
			<div className="flex flex-col gap-1">
				<label htmlFor={`sponsor-name-${sponsorshipPackage.id}`} className="text-sm font-medium text-slate-300">
					Your name / company <span aria-hidden>*</span>
				</label>
				<input
					id={`sponsor-name-${sponsorshipPackage.id}`}
					type="text"
					{...register("sponsorName")}
					aria-invalid={!!errors.sponsorName}
					className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3 py-2 text-white"
				/>
				{errors.sponsorName && <p role="alert" className="text-sm text-error-red">{errors.sponsorName.message}</p>}
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor={`sponsor-email-${sponsorshipPackage.id}`} className="text-sm font-medium text-slate-300">
					Email (optional)
				</label>
				<input
					id={`sponsor-email-${sponsorshipPackage.id}`}
					type="email"
					{...register("sponsorContactEmail")}
					aria-invalid={!!errors.sponsorContactEmail}
					className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3 py-2 text-white"
				/>
				{errors.sponsorContactEmail && <p role="alert" className="text-sm text-error-red">{errors.sponsorContactEmail.message}</p>}
			</div>
			<PrimaryButton type="submit" loading={isSubmitting}>
				Continue to checkout
			</PrimaryButton>
			<p className="text-xs text-slate-400">
				You&rsquo;ll be redirected to Stripe&rsquo;s secure checkout (test mode) to complete your sponsorship.
			</p>
		</form>
	);
}

function SponsorshipPackageCard({ sponsorshipPackage }: { sponsorshipPackage: PublicSponsorshipPackage }) {
	const [showForm, setShowForm] = useState(false);

	return (
		<div className="rounded-xl border border-white/10 bg-white/5 p-4">
			<div className="flex items-start justify-between gap-4">
				<div>
					<p className="font-heading text-lg font-bold text-white">
						{sponsorshipPackage.name}
						{sponsorshipPackage.exclusive && (
							<span className="ml-2 rounded-full bg-gold-500/10 px-2 py-0.5 text-xs font-medium text-gold-500">Exclusive</span>
						)}
					</p>
					{sponsorshipPackage.description && <p className="mt-1 text-sm text-slate-300">{sponsorshipPackage.description}</p>}
				</div>
				<p className="shrink-0 font-heading text-lg font-bold text-white">
					{formatMoneyMinorUnits(sponsorshipPackage.priceMinor, sponsorshipPackage.currency)}
				</p>
			</div>
			{sponsorshipPackage.soldOut ? (
				<p className="mt-3 text-sm font-medium text-gold-500">Sold out</p>
			) : (
				<PrimaryButton type="button" onClick={() => setShowForm((v) => !v)} className="mt-3">
					{showForm ? "Cancel" : "Sponsor this"}
				</PrimaryButton>
			)}
			{showForm && !sponsorshipPackage.soldOut && <SponsorshipPurchaseForm sponsorshipPackage={sponsorshipPackage} />}
		</div>
	);
}

/** Public sponsor directory section (DESIGN-DOC.md section 14.1 Phase 6 slice 1 scope) — real confirmed sponsors, not demo data. `logoUrl` is null until an org admin assigns one (ADR-018). */
function SponsorDirectory({ organizationSlug }: { organizationSlug: string }) {
	const { data, isLoading, isError } = usePublicSponsorDirectory(organizationSlug);

	if (isLoading || isError || !data || data.length === 0) return null;

	return (
		<div className="mt-12">
			<h2 className="font-heading text-xl font-bold text-white">Our Sponsors</h2>
			<ul className="mt-4 flex flex-wrap gap-4" aria-label="Confirmed sponsors">
				{data.map((entry) => (
					<li key={entry.sponsorId} className="flex flex-col items-center gap-2 rounded-lg border border-white/10 bg-white/5 p-4">
						{entry.logoUrl ? (
							<img src={entry.logoUrl} alt={entry.sponsorName} className="h-16 w-16 rounded-md object-contain" />
						) : (
							<div className="flex h-16 w-16 items-center justify-center rounded-md bg-white/10 text-lg font-bold text-white">
								{entry.sponsorName.charAt(0).toUpperCase()}
							</div>
						)}
						<p className="text-sm font-medium text-white">{entry.sponsorName}</p>
						<p className="text-xs text-slate-400">{entry.packageName}</p>
					</li>
				))}
			</ul>
		</div>
	);
}

/**
 * Public sponsorship packages + directory page for an organization (DESIGN-DOC.md
 * section 14.1 Phase 6 slice 1, ADR-018). Scoped by the organization's own public
 * slug, unlike campaigns/stores which are scoped by their own resource's slug —
 * sponsorship checkout itself only ever needs a packageId once a visitor is here.
 */
export function PublicSponsorshipView() {
	const { slug } = useParams<{ slug: string }>();
	const [searchParams] = useSearchParams();
	const { data: packages, isLoading, isError } = usePublicSponsorshipPackages(slug ?? "");
	const packageId = searchParams.get("packageId");
	const sponsorshipId = searchParams.get("sponsorshipId");
	const checkoutCanceled = searchParams.get("checkoutCanceled") === "1";

	if (isLoading) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<LoadingState label="Loading sponsorship opportunities…" />
			</div>
		);
	}

	if (isError || !packages) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<ErrorState message="This organization's sponsorship packages could not be found." />
			</div>
		);
	}

	return (
		<>
			<Seo title="Sponsorship opportunities" description="Support this organization as a sponsor on LeagueLift." />

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-2xl">
					<h1 className="text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">Sponsorship Opportunities</h1>

					{packageId && sponsorshipId ? (
						<SponsorshipReturnPanel packageId={packageId} sponsorshipId={sponsorshipId} />
					) : (
						<>
							{checkoutCanceled && (
								<p role="alert" className="mt-6 rounded-md bg-gold-500/10 p-3 text-sm text-gold-500">
									Checkout was canceled — no sponsorship was made.
								</p>
							)}

							{packages.length === 0 ? (
								<p className="mt-8 text-slate-300">No sponsorship packages are available right now.</p>
							) : (
								<div className="mt-8 flex flex-col gap-4">
									{packages.map((sponsorshipPackage) => (
										<SponsorshipPackageCard key={sponsorshipPackage.id} sponsorshipPackage={sponsorshipPackage} />
									))}
								</div>
							)}
						</>
					)}

					{slug && <SponsorDirectory organizationSlug={slug} />}

					<SecondaryLightButton to="/" className="mt-8">
						Learn more about LeagueLift
					</SecondaryLightButton>
				</PageContainer>
			</section>
		</>
	);
}
