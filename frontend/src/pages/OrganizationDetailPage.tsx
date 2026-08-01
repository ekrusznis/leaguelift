import { useEffect, type ReactNode } from "react";
import { Link, Navigate, useParams, useSearchParams } from "react-router-dom";
import { Capabilities } from "../authorization/capabilityConstants";
import { useContexts } from "../authorization/api";
import { hasCapability } from "../authorization/capabilities";
import { useCurrentSupportAccess } from "../features/platformAdmin/api";
import { ErrorState } from "../components/states/ErrorState";
import { LoadingState } from "../components/states/LoadingState";
import { OrganizationLogo } from "../dashboard/components/OrganizationLogo";
import { OrganizationDocumentsPanel } from "../features/documents/OrganizationDocumentsPanel";
import { OnboardingPanel } from "../features/onboarding/OnboardingPanel";
import { OrganizationCorrectionReviewPanel } from "../features/profileCorrections/OrganizationCorrectionReviewPanel";
import { EventListPanel } from "../features/events/EventListPanel";
import { FeeTemplateList } from "../features/fees/FeeTemplateList";
import { CampaignList } from "../features/fundraising/CampaignList";
import { HouseholdList } from "../features/households/HouseholdList";
import { IntegrationsPanel } from "../features/integrations/IntegrationsPanel";
import { useMediaAssignments } from "../features/media/api";
import { OrganizationBrandingPanel } from "../features/media/OrganizationBrandingPanel";
import { useOrganization } from "../features/organizations/api";
import type { Organization } from "../features/organizations/types";
import { InvitationsPanel } from "../features/organizations/InvitationsPanel";
import { OnboardingChecklist } from "../features/organizations/OnboardingChecklist";
import { OrganizationProfileForm } from "../features/organizations/OrganizationProfileForm";
import { useRefreshPayoutStatus } from "../features/payouts/api";
import { PayoutConnectPanel } from "../features/payouts/PayoutConnectPanel";
import { PayoutSummaryPanel } from "../features/payouts/PayoutSummaryPanel";
import { PublicPagesPanel } from "../features/publicpage/PublicPagesPanel";
import { OrganizationReportsPanel } from "../features/reporting/OrganizationReportsPanel";
import { SponsorshipPackageList } from "../features/sponsorship/SponsorshipPackageList";
import { StoreList } from "../features/store/StoreList";
import { TeamList } from "../features/teams/TeamList";
import { TournamentList } from "../features/tournaments/TournamentList";
import { appPaths, type OrganizationSection } from "../routes/appPaths";

const SECTION_LABELS: Array<{ id: OrganizationSection; label: string }> = [
	{ id: "overview", label: "Overview" },
	{ id: "onboarding", label: "Onboarding" },
	{ id: "corrections", label: "Corrections" },
	{ id: "teams", label: "Teams" },
	{ id: "tournaments", label: "Tournaments" },
	{ id: "households", label: "Households & Athletes" },
	{ id: "events", label: "Events" },
	{ id: "fees", label: "Fees & Payments" },
	{ id: "fundraising", label: "Fundraising" },
	{ id: "stores", label: "Stores & Orders" },
	{ id: "sponsorships", label: "Sponsorships" },
	{ id: "reports", label: "Reports" },
	{ id: "documents", label: "Documents" },
	{ id: "members", label: "Members" },
	{ id: "settings", label: "Settings" },
];

function isOrganizationSection(value: string | undefined): value is OrganizationSection {
	return SECTION_LABELS.some((section) => section.id === value);
}

export function OrganizationDetailPage() {
	const { organizationId, section } = useParams<{ organizationId: string; section?: string }>();
	const contexts = useContexts();
	const isPlatformAdmin = hasCapability(contexts.data, Capabilities.PLATFORM_SUPPORT_ACCESS, { contextType: "PLATFORM_ADMIN", resourceId: null });
	const supportAccess = useCurrentSupportAccess(isPlatformAdmin);
	const hasMatchingPlatformAccess = !isPlatformAdmin || (
		supportAccess.data?.organizationId === organizationId && supportAccess.data?.status === "ACTIVE"
	);
	const canLoadOrganization = !contexts.isLoading && hasMatchingPlatformAccess;
	const { data: organization, isLoading, isError, refetch } = useOrganization(organizationId ?? "", canLoadOrganization);

	if (!organizationId) return <ErrorState message="No organization selected." />;
	if (section && !isOrganizationSection(section)) return <Navigate to={appPaths.organization(organizationId, "overview")} replace />;
	if (contexts.isLoading || (isPlatformAdmin && supportAccess.isLoading)) return <LoadingState label="Loading organization…" />;
	if (contexts.isError || (isPlatformAdmin && supportAccess.isError)) {
		return <ErrorState message="Could not validate access to this organization." onRetry={() => { void contexts.refetch(); void supportAccess.refetch(); }} />;
	}
	if (isPlatformAdmin && !hasMatchingPlatformAccess) {
		return <Navigate to={appPaths.platformOrganization(organizationId)} replace />;
	}
	if (isLoading) return <LoadingState label="Loading organization…" />;
	if (isError || !organization) {
		return <ErrorState message="Could not load this organization." onRetry={() => { void refetch(); void contexts.refetch(); }} />;
	}

	const activeSection: OrganizationSection = section && isOrganizationSection(section) ? section : "overview";
	const isPlatformSupportMode = isPlatformAdmin;
	const canReadEvents = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.EVENT_READ, { contextType: "ORGANIZATION", resourceId: organization.id });
	const canManageEvents = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_EVENT_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id });
	const canManageOrganization = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id });
	const canManageTeams = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_TEAM_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id }) || canManageOrganization;
	const canManageTournaments = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_TOURNAMENT_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id }) || canManageOrganization;
	const canManageMembers = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_MEMBERS_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id });
	const canManagePayouts = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_PAYOUT_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id });
	const canViewReports = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_REPORT_VIEW, { contextType: "ORGANIZATION", resourceId: organization.id });

	const visibleSections = SECTION_LABELS.filter(({ id }) => {
		if (id === "overview") return true;
		if (id === "onboarding" || id === "corrections") return canManageOrganization;
		if (id === "teams") return canManageTeams;
		if (id === "tournaments") return canManageTournaments;
		if (id === "events") return canReadEvents || canManageEvents;
		if (id === "fees" || id === "reports") return canViewReports || canManageOrganization;
		if (id === "members") return canManageMembers;
		if (id === "settings") return canManageOrganization || canManagePayouts;
		if (["households", "fundraising", "stores", "sponsorships", "documents"].includes(id)) return canManageOrganization;
		return false;
	});

	if (!visibleSections.some((item) => item.id === activeSection)) {
		return <Navigate to={appPaths.organization(organization.id, "overview")} replace />;
	}

	return (
		<div className="flex flex-col gap-6">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<div className="flex items-center gap-4">
					<OrganizationHeaderLogo organizationId={organization.id} organizationName={organization.name} />
					<div>
						<Link to={isPlatformSupportMode ? `/app/platform/organizations/${organization.id}` : "/app"} className="text-sm text-azure-blue hover:underline">
							← {isPlatformSupportMode ? "Organization console" : "Dashboard"}
						</Link>
						<h1 className="font-heading text-2xl font-bold text-navy">{organization.name}</h1>
						<p className="text-slate-gray">/{organization.slug}</p>
					</div>
				</div>
			</div>

			{canManagePayouts && <PayoutStripeReturnHandler organizationId={organization.id} />}

			<nav aria-label="Organization sections" className="flex gap-2 overflow-x-auto border-b border-slate-gray/20 pb-3">
				{visibleSections.map((item) => (
					<Link
						key={item.id}
						to={appPaths.organization(organization.id, item.id)}
						aria-current={activeSection === item.id ? "page" : undefined}
						className={`shrink-0 rounded-lg px-3 py-2 text-sm font-medium ${activeSection === item.id ? "bg-navy text-white" : "text-slate-gray hover:bg-ice-white hover:text-navy"}`}
					>
						{item.label}
					</Link>
				))}
			</nav>

			<OrganizationSectionContent
				section={activeSection}
				organization={organization}
				canManageEvents={canManageEvents}
				canManageOrganization={canManageOrganization}
				canManagePayouts={canManagePayouts}
				canViewReports={canViewReports}
				visibleSections={visibleSections}
			/>
		</div>
	);
}

function OrganizationSectionContent({
	section,
	organization,
	canManageEvents,
	canManageOrganization,
	canManagePayouts,
	canViewReports,
	visibleSections,
}: {
	section: OrganizationSection;
	organization: Organization;
	canManageEvents: boolean;
	canManageOrganization: boolean;
	canManagePayouts: boolean;
	canViewReports: boolean;
	visibleSections: Array<{ id: OrganizationSection; label: string }>;
}) {
	switch (section) {
		case "overview":
			return (
				<div className="flex flex-col gap-6">
					{canManageOrganization && <OnboardingChecklist organizationId={organization.id} />}
					<section className="rounded-xl border border-slate-gray/20 bg-pure-white p-5">
						<h2 className="font-heading text-lg font-semibold text-navy">Organization workspace</h2>
						<p className="mt-1 text-sm text-slate-gray">Use the sections above to manage teams, households, events, revenue programs, reports, documents, and integrations.</p>
						<div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
							{visibleSections.filter((item) => !["overview", "settings"].includes(item.id)).slice(0, 9).map((item) => (
								<Link key={item.id} to={appPaths.organization(organization.id, item.id)} className="rounded-lg border border-slate-gray/20 p-3 text-sm font-medium text-navy hover:border-green-500 hover:text-green-600">
									{item.label} →
								</Link>
							))}
						</div>
					</section>
				</div>
			);
		case "onboarding":
			return <Section title="Manual onboarding" description="Preview and import organization setup data, then complete repeatable bulk actions."><OnboardingPanel organizationId={organization.id} /></Section>;
		case "corrections":
			return <Section title="Profile correction requests" description="Review guardian, athlete, and scoped-staff requests before organization-owned profile fields change."><OrganizationCorrectionReviewPanel organizationId={organization.id} /></Section>;
		case "teams":
			return <Section title="Teams"><TeamList organizationId={organization.id} /></Section>;
		case "tournaments":
			return <Section title="Tournaments"><TournamentList organizationId={organization.id} /></Section>;
		case "households":
			return <Section title="Households & Athletes"><HouseholdList organizationId={organization.id} /></Section>;
		case "events":
			return <Section title="Events" description="Manage organization-wide events or open a team/tournament schedule for scoped events."><EventListPanel scope={{ type: "organization", organizationId: organization.id }} canManage={canManageEvents} /></Section>;
		case "fees":
			return canManageOrganization ? (
				<Section title="Fees & Payments" action={<Link to={appPaths.collections(organization.id)} className="text-sm font-medium text-azure-blue hover:underline">View collections and export →</Link>}>
					<FeeTemplateList organizationId={organization.id} />
				</Section>
			) : (
				<Section title="Fees & Payments" description="Read-only collections and revenue reporting for your organization role.">
					{canViewReports ? <OrganizationReportsPanel organizationId={organization.id} /> : <p className="text-sm text-slate-gray">You do not have permission to view financial reports.</p>}
				</Section>
			);
		case "fundraising":
			return <Section title="Fundraising Campaigns"><CampaignList organizationId={organization.id} /></Section>;
		case "stores":
			return <Section title="Stores & Orders"><StoreList organizationId={organization.id} /></Section>;
		case "sponsorships":
			return <Section title="Sponsorship Packages"><SponsorshipPackageList organizationId={organization.id} organizationSlug={organization.slug} /></Section>;
		case "reports":
			return <Section title="Reports"><OrganizationReportsPanel organizationId={organization.id} /></Section>;
		case "documents":
			return <Section title="Documents"><OrganizationDocumentsPanel organizationId={organization.id} /></Section>;
		case "members":
			return <Section title="Members & Invitations"><InvitationsPanel organizationId={organization.id} /></Section>;
		case "settings":
			return (
				<div className="flex flex-col gap-8">
					{canManageOrganization && (
						<>
							<Section title="Branding"><OrganizationBrandingPanel organizationId={organization.id} organizationName={organization.name} /></Section>
							<Section title="Organization Profile"><OrganizationProfileForm organization={organization} /></Section>
							<Section title="Public Pages"><PublicPagesPanel organizationId={organization.id} organizationName={organization.name} /></Section>
							<Section title="Integrations"><IntegrationsPanel organizationId={organization.id} /></Section>
						</>
					)}
					{canManagePayouts && (
						<Section title="Payouts">
							<PayoutConnectPanel organizationId={organization.id} />
							<PayoutSummaryPanel organizationId={organization.id} />
						</Section>
					)}
				</div>
			);
	}
}

function Section({ title, description, action, children }: { title: string; description?: string; action?: ReactNode; children: ReactNode }) {
	return (
		<section aria-label={title} className="flex flex-col gap-3">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div><h2 className="font-heading text-lg font-semibold text-navy">{title}</h2>{description && <p className="mt-1 text-sm text-slate-gray">{description}</p>}</div>
				{action}
			</div>
			{children}
		</section>
	);
}

/** Reads the org's uploaded logo (if any) for the page header; falls back to initials via OrganizationLogo itself. */
function OrganizationHeaderLogo({ organizationId, organizationName }: { organizationId: string; organizationName: string }) {
	const { data } = useMediaAssignments(organizationId);
	const logo = data?.items.find((item) => item.usageSlot === "LOGO");
	return <OrganizationLogo name={organizationName} src={logo?.url} size="lg" />;
}

/** Re-syncs Stripe Connect after a return/refresh redirect and removes the marker. */
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
	}, [stripeOnboarding]);

	return null;
}
