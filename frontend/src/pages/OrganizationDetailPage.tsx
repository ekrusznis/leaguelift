import { useEffect } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { OrganizationLogo } from "../dashboard/components/OrganizationLogo";
import { FeeTemplateList } from "../features/fees/FeeTemplateList";
import { CampaignList } from "../features/fundraising/CampaignList";
import { HouseholdList } from "../features/households/HouseholdList";
import { useMediaAssignments } from "../features/media/api";
import { OrganizationBrandingPanel } from "../features/media/OrganizationBrandingPanel";
import { useOrganization } from "../features/organizations/api";
import { InvitationsPanel } from "../features/organizations/InvitationsPanel";
import { OnboardingChecklist } from "../features/organizations/OnboardingChecklist";
import { OrganizationProfileForm } from "../features/organizations/OrganizationProfileForm";
import { useRefreshPayoutStatus } from "../features/payouts/api";
import { PayoutConnectPanel } from "../features/payouts/PayoutConnectPanel";
import { PayoutSummaryPanel } from "../features/payouts/PayoutSummaryPanel";
import { PublicPagesPanel } from "../features/publicpage/PublicPagesPanel";
import { SponsorshipPackageList } from "../features/sponsorship/SponsorshipPackageList";
import { StoreList } from "../features/store/StoreList";
import { TeamList } from "../features/teams/TeamList";
import { TournamentList } from "../features/tournaments/TournamentList";

export function OrganizationDetailPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const { data: organization, isLoading, isError, refetch } = useOrganization(organizationId ?? "");

	if (!organizationId) {
		return <ErrorState message="No organization selected." />;
	}
	if (isLoading) {
		return <LoadingState label="Loading organization…" />;
	}
	if (isError || !organization) {
		return <ErrorState message="Could not load this organization." onRetry={() => refetch()} />;
	}

	return (
		<div className="flex flex-col gap-8">
			<div className="flex items-center gap-4">
				<OrganizationHeaderLogo organizationId={organization.id} organizationName={organization.name} />
				<div>
					<h1 className="font-heading text-2xl font-bold text-navy">{organization.name}</h1>
					<p className="text-slate-gray">/{organization.slug}</p>
				</div>
			</div>

			<OnboardingChecklist organizationId={organization.id} />

			<section aria-label="Branding" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Branding</h2>
				<OrganizationBrandingPanel organizationId={organization.id} organizationName={organization.name} />
			</section>

			<section aria-label="Payouts" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Payouts</h2>
				<PayoutStripeReturnHandler organizationId={organization.id} />
				<PayoutConnectPanel organizationId={organization.id} />
				<PayoutSummaryPanel organizationId={organization.id} />
			</section>

			<section aria-label="Organization profile" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Profile</h2>
				<OrganizationProfileForm organization={organization} />
			</section>

			<section aria-label="Teams" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Teams</h2>
				<TeamList organizationId={organization.id} />
			</section>

			<section aria-label="Tournaments" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Tournaments</h2>
				<TournamentList organizationId={organization.id} />
			</section>

			<section aria-label="Households" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Households</h2>
				<HouseholdList organizationId={organization.id} />
			</section>

			<section aria-label="Fee templates" className="flex flex-col gap-3">
				<div className="flex items-center justify-between">
					<h2 className="font-heading text-lg font-semibold text-navy">Fee Templates</h2>
					<Link to={`/app/organizations/${organization.id}/collections`} className="text-sm text-azure-blue hover:underline">
						View collections →
					</Link>
				</div>
				<FeeTemplateList organizationId={organization.id} />
			</section>

			<section aria-label="Fundraising campaigns" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Fundraising Campaigns</h2>
				<CampaignList organizationId={organization.id} />
			</section>

			<section aria-label="Stores" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Stores</h2>
				<StoreList organizationId={organization.id} />
			</section>

			<section aria-label="Sponsorship packages" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Sponsorship Packages</h2>
				<SponsorshipPackageList organizationId={organization.id} organizationSlug={organization.slug} />
			</section>

			<section aria-label="Public pages" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Public Pages</h2>
				<PublicPagesPanel organizationId={organization.id} organizationName={organization.name} />
			</section>

			<section aria-label="Administrators" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Administrators</h2>
				<InvitationsPanel organizationId={organization.id} />
			</section>
		</div>
	);
}

/** Reads the org's uploaded logo (if any) for the page header; falls back to initials via OrganizationLogo itself. */
function OrganizationHeaderLogo({ organizationId, organizationName }: { organizationId: string; organizationName: string }) {
	const { data } = useMediaAssignments(organizationId);
	const logo = data?.items.find((item) => item.usageSlot === "LOGO");
	return <OrganizationLogo name={organizationName} src={logo?.url} size="lg" />;
}

/**
 * When Stripe redirects the browser back here (return_url/refresh_url both point at
 * this page with a `stripeOnboarding` marker — see PayoutConnectPanel), re-sync status
 * from Stripe and strip the marker so a page refresh doesn't repeat the call.
 */
function PayoutStripeReturnHandler({ organizationId }: { organizationId: string }) {
	const [searchParams, setSearchParams] = useSearchParams();
	const refreshStatus = useRefreshPayoutStatus(organizationId);
	const stripeOnboarding = searchParams.get("stripeOnboarding");

	useEffect(() => {
		if (!stripeOnboarding) return;
		refreshStatus.mutate(undefined, {
			onSettled: () => {
				const next = new URLSearchParams(searchParams);
				next.delete("stripeOnboarding");
				setSearchParams(next, { replace: true });
			},
		});
		// Intentionally only re-runs when the marker itself changes, not on every
		// searchParams/refreshStatus identity change.
	}, [stripeOnboarding]);

	return null;
}
