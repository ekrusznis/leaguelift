import { useParams } from "react-router-dom";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { FeeTemplateList } from "../features/fees/FeeTemplateList";
import { CampaignList } from "../features/fundraising/CampaignList";
import { HouseholdList } from "../features/households/HouseholdList";
import { useOrganization } from "../features/organizations/api";
import { InvitationsPanel } from "../features/organizations/InvitationsPanel";
import { OnboardingChecklist } from "../features/organizations/OnboardingChecklist";
import { OrganizationProfileForm } from "../features/organizations/OrganizationProfileForm";
import { PublicPagesPanel } from "../features/publicpage/PublicPagesPanel";
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
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy">{organization.name}</h1>
				<p className="text-slate-gray">/{organization.slug}</p>
			</div>

			<OnboardingChecklist organizationId={organization.id} />

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
				<h2 className="font-heading text-lg font-semibold text-navy">Fee Templates</h2>
				<FeeTemplateList organizationId={organization.id} />
			</section>

			<section aria-label="Fundraising campaigns" className="flex flex-col gap-3">
				<h2 className="font-heading text-lg font-semibold text-navy">Fundraising Campaigns</h2>
				<CampaignList organizationId={organization.id} />
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
