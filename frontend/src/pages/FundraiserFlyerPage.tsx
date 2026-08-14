import { useEffect } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "../components/Button";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { useCampaign, useCampaignShareLink } from "../features/fundraising/api";
import { formatMoneyMinorUnits } from "../lib/money";

export function FundraiserFlyerPage() {
	const { organizationId = "", campaignId = "" } = useParams<{ organizationId: string; campaignId: string }>();
	const campaign = useCampaign(organizationId, campaignId);
	const { data: qrData, isPending: qrPending, isError: qrError, mutate: generateQr } = useCampaignShareLink(organizationId);
	const publicUrl = campaign.data ? `${window.location.origin}/campaigns/${campaign.data.slug}` : "";
	useEffect(() => {
		if (publicUrl && !qrData && !qrPending) generateQr(publicUrl);
	}, [publicUrl, qrData, qrPending, generateQr]);
	if (campaign.isLoading) return <LoadingState label="Preparing fundraiser flyer…" />;
	if (campaign.isError || !campaign.data) return <ErrorState message="Could not load this fundraiser." onRetry={() => campaign.refetch()} />;
	const item = campaign.data;
	return <div className="min-h-screen bg-ice-white p-4 text-navy sm:p-8 print:bg-white print:p-0">
		<style>{`@media print { .fundraiser-flyer-actions { display:none !important; } .fundraiser-flyer { box-shadow:none !important; border:none !important; min-height:100vh; } @page { margin: 0.45in; } }`}</style>
		<div className="fundraiser-flyer-actions mx-auto mb-4 flex max-w-3xl flex-wrap justify-between gap-2"><Link to={`/app/organizations/${organizationId}/fundraising`} className="inline-flex min-h-11 items-center rounded-md border border-slate-gray/30 px-3 text-sm font-medium">← Back to fundraising</Link><Button type="button" onClick={() => window.print()}>Print / Save as PDF</Button></div>
		<main className="fundraiser-flyer mx-auto flex max-w-3xl flex-col items-center rounded-2xl border border-slate-gray/20 bg-white p-8 text-center shadow-sm sm:p-12">
			<p className="font-heading text-sm font-bold uppercase tracking-[0.18em] text-victory-green">Rally26 Fundraiser</p>
			<h1 className="mt-4 max-w-2xl font-heading text-4xl font-extrabold sm:text-5xl">{item.name}</h1>
			{item.description && <p className="mt-5 max-w-2xl text-lg leading-relaxed text-slate-gray">{item.description}</p>}
			{item.eventLocationName || item.eventAddress ? <div className="mt-6 rounded-xl bg-ice-white px-6 py-4"><p className="font-semibold">Join us in person</p>{item.eventLocationName && <p>{item.eventLocationName}</p>}{item.eventAddress && <p className="text-sm text-slate-gray">{item.eventAddress}</p>}</div> : null}
			<div className="mt-8 grid w-full max-w-xl grid-cols-2 gap-3 rounded-xl bg-ice-white p-5"><div><p className="text-xs uppercase tracking-wide text-slate-gray">Goal</p><p className="font-heading text-2xl font-bold">{formatMoneyMinorUnits(item.goalAmountMinor, item.currency)}</p></div><div><p className="text-xs uppercase tracking-wide text-slate-gray">Campaign dates</p><p className="font-semibold">{item.startDate ?? "Starts now"}{item.endDate ? ` – ${item.endDate}` : ""}</p></div></div>
			<div className="mt-10 flex flex-col items-center"><p className="font-heading text-2xl font-bold">Scan to support us</p><p className="mt-2 text-slate-gray">Open the fundraiser, contribute securely, and share it with friends and family.</p>{qrPending && <div className="mt-5"><LoadingState label="Creating QR code…" /></div>}{qrData && <img src={qrData.qrCodeDataUri} alt="QR code for fundraiser" className="mt-5 size-64" />}{qrError && <p className="mt-4 text-error-red">QR code could not be generated. Return to fundraising and try again.</p>}<p className="mt-4 break-all text-sm font-medium">{publicUrl}</p></div>
			<p className="mt-10 text-sm text-slate-gray">Powered by Rally26 · More revenue. Lower fees. Stronger programs.</p>
		</main>
	</div>;
}
