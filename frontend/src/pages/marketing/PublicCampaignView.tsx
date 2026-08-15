import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useContributionStatus, useCreateContributionCheckout, usePublicCampaign } from "../../features/fundraising/api";
import { usePublicFundraisingGame } from "../../features/fundraisingGames/api";
import { createContributionSchema } from "../../features/fundraising/schema";
import type { CampaignType } from "../../features/fundraising/types";
import { formatMoneyMinorUnits } from "../../lib/money";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { PrimaryButton, SecondaryLightButton } from "../../marketing/components/buttons";

const CAMPAIGN_TYPE_LABELS: Record<CampaignType, string> = {
	ORGANIZATION_GENERAL: "Organization general fund", TEAM_GENERAL: "Team general fund", TRAVEL: "Travel",
	TOURNAMENT_FEES: "Tournament fees", UNIFORMS: "Uniforms", EQUIPMENT: "Equipment",
	FACILITY_IMPROVEMENTS: "Facility improvements", SCHOLARSHIPS: "Scholarships", SPECIAL_EVENTS: "Special events",
	APPAREL_BASED: "Apparel-based", SPONSOR_SUPPORTED: "Sponsor-supported",
};
function formatDate(value: string | null): string | null {
	if (!value) return null;
	return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" });
}

function ContributionReturnPanel({ slug, contributionId }: { slug: string; contributionId: string }) {
	const { data: contribution, isLoading, isError } = useContributionStatus(slug, contributionId);
	if (isLoading) return <div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-6 text-center"><LoadingState label="Confirming your contribution…" /></div>;
	if (isError || !contribution) return <div className="mt-6 rounded-xl border border-error-red/30 bg-error-red/10 p-6 text-center text-error-red">We couldn&rsquo;t look up your contribution. If you completed checkout, it may still be processing — check back shortly.</div>;
	if (contribution.status === "CONFIRMED") return <div className="mt-6 rounded-xl border border-green-500/30 bg-green-500/10 p-6 text-center"><p className="font-heading text-xl font-bold text-white">Thank you for your contribution!</p><p className="mt-2 text-slate-300">Your gift of {formatMoneyMinorUnits(contribution.amountMinor, contribution.currency)} has been confirmed.</p></div>;
	if (contribution.status === "CANCELED") return <div className="mt-6 rounded-xl border border-gold-500/30 bg-gold-500/10 p-6 text-center text-gold-500">This contribution was canceled before it completed.</div>;
	return <div className="mt-6 rounded-xl border border-white/10 bg-white/5 p-6 text-center"><LoadingState label="Confirming your contribution…" /><p className="mt-2 text-sm text-slate-400">This can take a few seconds after checkout.</p></div>;
}

function ContributionForm({ slug, campaignName, attributionCode }: { slug: string; campaignName: string; attributionCode: string | null }) {
	const createCheckout = useCreateContributionCheckout(slug);
	const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<z.input<typeof createContributionSchema>, unknown, z.output<typeof createContributionSchema>>({
		resolver: zodResolver(createContributionSchema),
		defaultValues: { amountDollars: 25, supporterName: "", isAnonymous: false, supporterEmail: "" },
	});
	const isAnonymous = watch("isAnonymous");
	const onSubmit = handleSubmit(async (values) => {
		const returnBase = `${window.location.origin}${window.location.pathname}`;
		const result = await createCheckout.mutateAsync({ ...values, successUrl: `${returnBase}?contributionId={CONTRIBUTION_ID}`, cancelUrl: `${returnBase}?checkoutCanceled=1`, attributionCode });
		window.location.href = result.checkoutUrl;
	});
	return <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-3 rounded-xl border border-white/10 bg-white/5 p-6" noValidate>
		<h2 className="font-heading text-lg font-bold text-white">Support {campaignName}</h2>
		{createCheckout.isError && <p role="alert" className="rounded-md bg-error-red/10 p-2 text-sm text-error-red">We couldn&rsquo;t start checkout. Please try again.</p>}
		<div className="flex flex-col gap-1"><label htmlFor="contribution-amount" className="text-sm font-medium text-slate-300">Contribution amount ($) <span aria-hidden>*</span></label><input id="contribution-amount" type="number" min={1} max={50000} step="0.01" inputMode="decimal" {...register("amountDollars")} aria-invalid={!!errors.amountDollars} className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3 py-2 text-white" />{errors.amountDollars && <p role="alert" className="text-sm text-error-red">{errors.amountDollars.message}</p>}</div>
		<div className="flex flex-col gap-1"><label htmlFor="contribution-name" className="text-sm font-medium text-slate-300">Your name</label><input id="contribution-name" type="text" disabled={isAnonymous} {...register("supporterName")} className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3 py-2 text-white disabled:opacity-50" /></div>
		<label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" {...register("isAnonymous")} className="size-4" />Give anonymously</label>
		<div className="flex flex-col gap-1"><label htmlFor="contribution-email" className="text-sm font-medium text-slate-300">Email (optional)</label><input id="contribution-email" type="email" {...register("supporterEmail")} className="min-h-11 rounded-md border border-white/20 bg-white/5 px-3 py-2 text-white" />{errors.supporterEmail && <p role="alert" className="text-sm text-error-red">{errors.supporterEmail.message}</p>}</div>
		<PrimaryButton type="submit" loading={isSubmitting} className="mt-2">Contribute</PrimaryButton>
		<p className="text-xs text-slate-400">You&rsquo;ll be redirected to Stripe&rsquo;s secure checkout to complete your contribution.</p>
	</form>;
}

export function PublicCampaignView() {
	const { slug } = useParams<{ slug: string }>();
	const [searchParams] = useSearchParams();
	const { data: campaign, isLoading, isError } = usePublicCampaign(slug ?? "");
	const freeGame = usePublicFundraisingGame(slug ?? "");
	const [checkoutCanceled] = useState(() => searchParams.get("checkoutCanceled") === "1");
	const contributionId = searchParams.get("contributionId");
	if (isLoading) return <div className="flex min-h-[60vh] items-center justify-center"><LoadingState label="Loading campaign…" /></div>;
	if (isError || !campaign) return <div className="flex min-h-[60vh] items-center justify-center"><ErrorState message="This campaign could not be found or is not currently active." /></div>;
	const startDate = formatDate(campaign.startDate); const endDate = formatDate(campaign.endDate);
	const progressPercent = campaign.goalAmountMinor > 0 ? Math.min(100, Math.round((campaign.raisedMinor / campaign.goalAmountMinor) * 100)) : 0;
	return <><Seo title={campaign.name} description={campaign.description ?? `Support ${campaign.name} on Rally26.`} />
		<section className="bg-navy-950 py-20 sm:py-28"><PageContainer className="max-w-2xl">
			{campaign.logoUrl && <img src={campaign.logoUrl} alt="" className="mb-4 h-16 w-16 rounded-lg object-cover" />}
			<p className="font-heading text-xs font-semibold uppercase tracking-wide text-green-400">{CAMPAIGN_TYPE_LABELS[campaign.campaignType]}</p>
			<h1 className="mt-3 text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">{campaign.name}</h1>
			{campaign.description && <p className="mt-4 text-lg leading-relaxed text-slate-300">{campaign.description}</p>}
			{campaign.eventLocationName || campaign.eventAddress ? <div className="mt-5 rounded-xl border border-white/10 bg-white/5 p-4 text-slate-200"><p className="font-semibold">Event location</p>{campaign.eventLocationName && <p className="mt-1">{campaign.eventLocationName}</p>}{campaign.eventAddress && <p className="text-sm text-slate-300">{campaign.eventAddress}</p>}</div> : null}
			{freeGame.data && <Link to={`/campaigns/${campaign.slug}/play`} className="mt-5 flex min-h-14 items-center justify-between rounded-xl border border-orange-400/40 bg-orange-500/10 px-4 font-semibold text-orange-200"><span>Play {freeGame.data.title} — free</span><span aria-hidden>→</span></Link>}
			<div className="mt-8 rounded-2xl border border-white/10 bg-white/5 p-6"><div className="flex flex-wrap items-baseline justify-between gap-2"><p className="font-heading text-3xl font-extrabold text-white">{formatMoneyMinorUnits(campaign.raisedMinor, campaign.currency)}</p><p className="text-sm text-slate-400">raised of {formatMoneyMinorUnits(campaign.goalAmountMinor, campaign.currency)} goal</p></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-white/10" role="progressbar" aria-valuenow={progressPercent} aria-valuemin={0} aria-valuemax={100}><div className="h-full rounded-full bg-green-500" style={{ width: `${progressPercent}%` }} /></div>{(startDate || endDate) && <p className="mt-3 text-sm text-slate-400">{startDate && endDate ? `${startDate} – ${endDate}` : (startDate ?? endDate)}</p>}</div>
			{contributionId ? <ContributionReturnPanel slug={campaign.slug} contributionId={contributionId} /> : <>{checkoutCanceled && <p role="alert" className="mt-6 rounded-md bg-gold-500/10 p-3 text-sm text-gold-500">Checkout was canceled — no contribution was made.</p>}<ContributionForm slug={campaign.slug} campaignName={campaign.name} attributionCode={searchParams.get("ref")} /></>}
			<SecondaryLightButton to="/" className="mt-8">Learn more about Rally26</SecondaryLightButton>
		</PageContainer></section></>;
}
