import { useParams } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { usePublicCampaign } from "../../features/fundraising/api";
import type { CampaignType } from "../../features/fundraising/types";
import { PageContainer } from "../../marketing/components/PageContainer";
import { Seo } from "../../marketing/components/Seo";
import { SecondaryLightButton } from "../../marketing/components/buttons";
import { formatMoneyMinorUnits } from "../../lib/money";

const CAMPAIGN_TYPE_LABELS: Record<CampaignType, string> = {
	ORGANIZATION_GENERAL: "Organization general fund",
	TEAM_GENERAL: "Team general fund",
	TRAVEL: "Travel",
	TOURNAMENT_FEES: "Tournament fees",
	UNIFORMS: "Uniforms",
	EQUIPMENT: "Equipment",
	FACILITY_IMPROVEMENTS: "Facility improvements",
	SCHOLARSHIPS: "Scholarships",
	SPECIAL_EVENTS: "Special events",
	APPAREL_BASED: "Apparel-based",
	SPONSOR_SUPPORTED: "Sponsor-supported",
};

function formatDate(value: string | null): string | null {
	if (!value) return null;
	return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" });
}

/**
 * Public campaign page (DESIGN-DOC.md sections 8.5, 16.6). Contribution
 * tracking/online giving doesn't exist yet (that's a later slice), so this
 * shows the goal honestly without a fabricated "amount raised" progress bar.
 */
export function PublicCampaignView() {
	const { slug } = useParams<{ slug: string }>();
	const { data: campaign, isLoading, isError } = usePublicCampaign(slug ?? "");

	if (isLoading) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<LoadingState label="Loading campaign…" />
			</div>
		);
	}

	if (isError || !campaign) {
		return (
			<div className="flex min-h-[60vh] items-center justify-center">
				<ErrorState message="This campaign could not be found or is not currently active." />
			</div>
		);
	}

	const startDate = formatDate(campaign.startDate);
	const endDate = formatDate(campaign.endDate);

	return (
		<>
			<Seo title={campaign.name} description={campaign.description ?? `Support ${campaign.name} on LeagueLift.`} />

			<section className="bg-navy-950 py-20 sm:py-28">
				<PageContainer className="max-w-2xl">
					<p className="font-heading text-xs font-semibold uppercase tracking-wide text-green-400">
						{CAMPAIGN_TYPE_LABELS[campaign.campaignType]}
					</p>
					<h1 className="mt-3 text-balance font-heading text-3xl font-extrabold text-white sm:text-4xl">{campaign.name}</h1>
					{campaign.description && <p className="mt-4 text-lg leading-relaxed text-slate-300">{campaign.description}</p>}

					<div className="mt-8 rounded-2xl border border-white/10 bg-white/5 p-6">
						<p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Goal</p>
						<p className="mt-1 font-heading text-3xl font-extrabold text-white">
							{formatMoneyMinorUnits(campaign.goalAmountMinor, campaign.currency)}
						</p>
						{(startDate || endDate) && (
							<p className="mt-3 text-sm text-slate-400">
								{startDate && endDate ? `${startDate} – ${endDate}` : (startDate ?? endDate)}
							</p>
						)}
					</div>

					<div className="mt-6 rounded-xl border border-gold-500/30 bg-gold-500/10 p-4 text-sm text-gold-500">
						Online giving for this campaign isn&rsquo;t available yet. Contact the organization directly to
						contribute.
					</div>

					<SecondaryLightButton to="/" className="mt-8">
						Learn more about LeagueLift
					</SecondaryLightButton>
				</PageContainer>
			</section>
		</>
	);
}
