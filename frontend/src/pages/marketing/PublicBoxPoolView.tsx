import { useState } from "react";
import { useParams } from "react-router-dom";
import { Modal } from "../../components/Modal";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { BoxPoolGrid } from "../../features/boxpool/BoxPoolGrid";
import { useReserveBox, usePublicBoxPool } from "../../features/boxpool/api";
import type { BoxPoolBox } from "../../features/boxpool/types";
import { usePublicCampaign } from "../../features/fundraising/api";
import { formatMoneyMinorUnits } from "../../lib/money";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";

/**
 * Public box-pool claim page (Phase 42) — a public link is the claim mechanism
 * (not email-reply, per the founder's confirmed decision: no inbound-email
 * infrastructure exists anywhere in this codebase). Claiming a box reuses the exact
 * same Stripe-checkout redirect every other public contribution/order page uses.
 */
export function PublicBoxPoolView() {
	const { slug } = useParams<{ slug: string }>();
	const { data: campaign, isLoading: campaignLoading } = usePublicCampaign(slug ?? "");
	const { data: pool, isLoading: poolLoading, isError: poolError } = usePublicBoxPool(slug ?? "");
	const reserveBox = useReserveBox(slug ?? "");
	const [selectedBox, setSelectedBox] = useState<BoxPoolBox | null>(null);
	const [claimantName, setClaimantName] = useState("");
	const [claimantEmail, setClaimantEmail] = useState("");
	const [submitError, setSubmitError] = useState<string | null>(null);

	if (campaignLoading || poolLoading) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<LoadingState label="Loading box pool…" />
			</div>
		);
	}

	if (poolError || !pool || !campaign) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<ErrorState message="This box pool could not be found or is no longer active." />
			</div>
		);
	}

	async function submitClaim() {
		if (!selectedBox || !claimantName.trim()) return;
		setSubmitError(null);
		try {
			const returnBase = `${window.location.origin}${window.location.pathname}`;
			const result = await reserveBox.mutateAsync({
				rowIndex: selectedBox.rowIndex,
				colIndex: selectedBox.colIndex,
				claimantName: claimantName.trim(),
				claimantEmail: claimantEmail.trim() || null,
				successUrl: `${returnBase}?claimed=1`,
				cancelUrl: `${returnBase}?claimCanceled=1`,
			});
			window.location.href = result.checkoutUrl;
		} catch {
			setSubmitError("This box may have just been claimed by someone else — pick another one.");
		}
	}

	return (
		<>
			<Seo title={campaign.name} description={`Claim a box in ${campaign.name} on Rally26.`} />
			<section className="bg-navy-950 py-20 sm:py-28" style={campaign.primaryColor ? { backgroundColor: campaign.primaryColor } : undefined}>
				<PageContainer className="max-w-3xl">
					{campaign.logoUrl && <img src={campaign.logoUrl} alt="" className="mb-4 h-16 w-16 rounded-lg object-cover" />}
					<h1 className="text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">{campaign.name}</h1>
					<p className="mt-2 text-slate-300">
						{pool.sport} box pool · {formatMoneyMinorUnits(pool.pricePerBoxMinor, "USD")} per box
						{pool.prizeDescription && <> · Prize: {pool.prizeDescription}</>}
					</p>

					<div className="mt-8 rounded-2xl border border-white/10 bg-white/5 p-6">
						<BoxPoolGrid
							rows={pool.rows}
							cols={pool.cols}
							boxes={pool.boxes}
							rowAxisLabel={pool.rowAxisLabel}
							colAxisLabel={pool.colAxisLabel}
							onSelectBox={setSelectedBox}
						/>
					</div>
				</PageContainer>
			</section>

			<Modal
				open={!!selectedBox}
				onClose={() => setSelectedBox(null)}
				title={selectedBox ? `Claim row ${selectedBox.rowIndex + 1}, column ${selectedBox.colIndex + 1}` : ""}
				actions={
					<>
						<button type="button" onClick={() => setSelectedBox(null)} className="min-h-11 rounded-md border border-slate-gray/30 px-4 text-sm font-medium">
							Cancel
						</button>
						<button
							type="button"
							onClick={() => void submitClaim()}
							disabled={!claimantName.trim() || reserveBox.isPending}
							className="min-h-11 rounded-md bg-victory-green px-4 text-sm font-medium text-pure-white disabled:opacity-50"
						>
							{reserveBox.isPending ? "Starting checkout…" : `Pay ${formatMoneyMinorUnits(pool.pricePerBoxMinor, "USD")}`}
						</button>
					</>
				}
			>
				<div className="flex flex-col gap-3">
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium">Your name <span aria-hidden>*</span></span>
						<input
							value={claimantName}
							onChange={(e) => setClaimantName(e.target.value)}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							maxLength={120}
						/>
					</label>
					<label className="flex flex-col gap-1">
						<span className="text-sm font-medium">Email (optional)</span>
						<input
							type="email"
							value={claimantEmail}
							onChange={(e) => setClaimantEmail(e.target.value)}
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</label>
					{submitError && <p role="alert" className="text-sm text-error-red">{submitError}</p>}
					<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">
						You&rsquo;ll be redirected to Stripe&rsquo;s secure checkout to pay for this box.
					</p>
				</div>
			</Modal>
		</>
	);
}
