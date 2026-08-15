import { Link, Navigate, useParams } from "react-router-dom";
import { Capabilities } from "../authorization/capabilityConstants";
import { useContexts } from "../authorization/api";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { CampaignList } from "../features/fundraising/CampaignList";
import { FundraisingSettingsPanel } from "../features/fundraising/FundraisingSettingsPanel";
import { useOrganization } from "../features/organizations/api";
import { appPaths } from "../routes/appPaths";

/**
 * Dedicated fundraising surface used by Owner/Admin/Coach/Guardian.
 * Backend campaign permissions remain authoritative; these capability checks only shape UI.
 */
export function FundraisingPage() {
	const { organizationId } = useParams<{ organizationId: string }>();
	const contexts = useContexts();
	const organization = useOrganization(organizationId ?? "", !!organizationId);

	if (!organizationId) return <ErrorState message="No organization selected." />;
	if (contexts.isLoading || organization.isLoading) return <LoadingState label="Loading fundraising…" />;
	if (contexts.isError || organization.isError || !organization.data) {
		return <ErrorState message="Could not load fundraising." onRetry={() => { void contexts.refetch(); void organization.refetch(); }} />;
	}

	const organizationContexts = (contexts.data ?? []).filter((context) => context.organizationId === organizationId);
	if (organizationContexts.length === 0) return <Navigate to="/app" replace />;

	const holds = (capability: string) => organizationContexts.some((context) => context.capabilities.includes(capability));
	const canManageOrganization = holds(Capabilities.ORG_MANAGE);
	const canApprove = holds(Capabilities.ORG_FUNDRAISING_APPROVE);
	const canCreate =
		canManageOrganization ||
		holds(Capabilities.TEAM_FUNDRAISING_CREATE) ||
		holds(Capabilities.HOUSEHOLD_FUNDRAISING_CREATE);

	return (
		<div className="flex flex-col gap-6">
			<div>
				<Link to={appPaths.organization(organizationId, "overview")} className="text-sm font-medium text-azure-blue hover:underline">← {organization.data.name}</Link>
				<div className="mt-2 flex flex-wrap items-end justify-between gap-3">
					<div>
						<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">Fundraising</h1>
						<p className="mt-1 max-w-2xl text-sm text-slate-gray dark:text-[#cbd5e1]">Create, share, approve, and track organization or team fundraisers.</p>
					</div>
				</div>
			</div>

			<FundraisingSettingsPanel organizationId={organizationId} canApprove={canApprove} />
			<CampaignList organizationId={organizationId} canCreate={canCreate} canApprove={canApprove} canManageOrganization={canManageOrganization} />
		</div>
	);
}
