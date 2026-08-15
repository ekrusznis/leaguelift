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
import { OfflineFinancialRecordsPanel } from "../features/offlineFinance/OfflineFinancialRecordsPanel";
import { FinancialCorrectionsPanel } from "../features/financialCorrections/FinancialCorrectionsPanel";
import { CreditMarkupSettingsPanel } from "../features/credit/CreditMarkupSettingsPanel";
import { SafeSportOrgPolicyPanel } from "../features/messaging/SafeSportPolicyPanel";
import { ReconciliationPanel } from "../features/reconciliation/ReconciliationPanel";
import { OrganizationCorrectionReviewPanel } from "../features/profileCorrections/OrganizationCorrectionReviewPanel";
import { EventListPanel } from "../features/events/EventListPanel";
import { EligibilityRequirementList } from "../features/eligibility/EligibilityRequirementList";
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
import { SeasonRolloverPanel } from "../features/seasonRollover/SeasonRolloverPanel";
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
	{ id: "swag-shop", label: "Swag Shop" },
	{ id: "financial-operations", label: "Financial Operations" },
	{ id: "sponsorships", label: "Sponsorships" },
	{ id: "reports", label: "Reports" },
	{ id: "documents", label: "Documents" },
	{ id: "eligibility", label: "Eligibility & Waivers" },
	{ id: "members", label: "Members" },
	{ id: "integrations", label: "Integrations" },
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
	const canManageEligibility = isPlatformSupportMode || hasCapability(contexts.data, Capabilities.ORG_ELIGIBILITY_MANAGE, { contextType: "ORGANIZATION", resourceId: organization.id }) || canManageOrganization;

	const visibleSections = SECTION_LABELS.filter(({ id }) => {
		if (id === "overview") return true;
		if (id === "onboarding" || id === "corrections") return canManageOrganization;
		if (id === "teams") return canManageTeams;
		if (id === "tournaments") return canManageTournaments;
		if (id === "events") return canReadEvents || canManageEvents;
		if (id === "fees" || id === "reports") return canViewReports || canManageOrganization;
		if (id === "eligibility") return canManageEligibility;
		if (id === "members") return canManageMembers;
		if (id === "integrations") return canManageOrganization;
		if (id === "settings") return canManageOrganization || canManagePayouts;
		if (["households", "fundraising", "swag-shop", "financial-operations", "sponsorships", "documents"].includes(id)) return canManageOrganization;
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
						<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">{organization.name}</h1>
						<p className="text-slate-gray dark:text-[#cbd5e1]">/{organization.slug}</p>
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
						className={`shrink-0 rounded-lg px-3 py-2 text-sm font-medium ${activeSection === item.id ? "bg-navy text-white" : "text-slate-gray dark:text-[#cbd5e1] hover:bg-ice-white hover:dark:bg-[#0f172a] hover:text-navy hover:dark:text-[#f8fafc]"}`}
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
				isPlatformSupportMode={isPlatformSupportMode}
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
	isPlatformSupportMode,
	visibleSections,
}: {
	section: OrganizationSection;
	organization: Organization;
	canManageEvents: boolean;
	canManageOrganization: boolean;
	canManagePayouts: boolean;
	canViewReports: boolean;
	isPlatformSupportMode: boolean;
	visibleSections: Array<{ id: OrganizationSection; label: string }>;
}) {
	switch (section) {
		case "overview":
			return (
				<div className="flex flex-col gap-6">
					{canManageOrganization && <OnboardingChecklist organizationId={organization.id} />}
					<section className="rounded-xl border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-5">
						<h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">Organization workspace</h2>
						<p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Use the sections above to manage teams, households, events, revenue programs, reports, documents, and integrations.</p>
						<div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
							{visibleSections.filter((item) => !["overview", "settings"].includes(item.id)).slice(0, 9).map((item) => (
								<Link key={item.id} to={appPaths.organization(organization.id, item.id)} className="rounded-lg border border-slate-gray/20 p-3 text-sm font-medium text-navy dark:text-[#f8fafc] hover:border-green-500 hover:text-green-600">
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
			return (
				<Section title="Teams">
					<div className="flex flex-col gap-6">
						<TeamList organizationId={organization.id} />
						{canManageOrganization && <SeasonRolloverPanel organizationId={organization.id} />}
					</div>
				</Section>
			);
		case "tournaments":
			return <Section title="Tournaments"><TournamentList organizationId={organization.id} /></Section>;
		case "households":
			return <Section title="Households & Athletes"><HouseholdList organizationId={organization.id} /></Section>;
		case "events":
			return <Section title="Events" description="Manage organization-wide events or open a team/tournament schedule for scoped events."><EventListPanel scope={{ type: "organization", organizationId: organization.id }} canManage={canManageEvents} /></Section>;
		case "fees":
			return canManageOrganization ? (
				<Section
				title="Fees & Payments"
				action={
					<div className="flex flex-wrap gap-4">
						<Link to={appPaths.disputes(organization.id)} className="text-sm font-medium text-azure-blue hover:underline">
							View disputes
						</Link>
						<Link to={appPaths.collections(organization.id)} className="text-sm font-medium text-azure-blue hover:underline">
							View collections and export →
						</Link>
					</div>
				}
			>
					<FeeTemplateList organizationId={organization.id} />
				</Section>
			) : (
				<Section title="Fees & Payments" description="Read-only collections and revenue reporting for your organization role.">
					{canViewReports ? <OrganizationReportsPanel organizationId={organization.id} /> : <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">You do not have permission to view financial reports.</p>}
				</Section>
			);
		case "fundraising":
			return <Section title="Fundraising Campaigns"><CampaignList organizationId={organization.id} canManageOrganization={canManageOrganization} /></Section>;
		case "swag-shop":
			return <Section title="Swag Shop"><StoreList organizationId={organization.id} /></Section>;
		case "financial-operations":
			return (
				<Section title="Financial Operations" description="Record offline money, preview append-only corrections, and run durable reconciliation checks.">
					<div className="flex flex-col gap-8">
						<OfflineFinancialRecordsPanel organizationId={organization.id} />
						<FinancialCorrectionsPanel organizationId={organization.id} />
						<ReconciliationPanel organizationId={organization.id} />
					</div>
				</Section>
			);
		case "sponsorships":
			return <Section title="Sponsorship Packages"><SponsorshipPackageList organizationId={organization.id} organizationSlug={organization.slug} /></Section>;
		case "reports":
			return <Section title="Reports"><OrganizationReportsPanel organizationId={organization.id} /></Section>;
		case "documents":
			return <Section title="Documents"><OrganizationDocumentsPanel organizationId={organization.id} /></Section>;
		case "eligibility":
			return (
				<Section title="Eligibility & Waivers" description="Define the waivers, acknowledgments, and documents guardians must complete before an athlete is roster-eligible.">
					<EligibilityRequirementList organizationId={organization.id} />
				</Section>
			);
		case "members":
			return <Section title="Members & Invitations"><InvitationsPanel organizationId={organization.id} /></Section>;
		case "integrations":
			return <Section title="Integrations" description="Connect organization-owned accounting and sports-data providers, or use reviewed CSV and ICS workflows."><IntegrationsPanel organizationId={organization.id} readOnly={isPlatformSupportMode} /></Section>;
		case "settings":
			return (
				<div className="flex flex-col gap-8">
					{canManageOrganization && (
						<>
							<Section title="Branding"><OrganizationBrandingPanel organizationId={organization.id} organizationName={organization.name} /></Section>
							<Section title="Organization Profile"><OrganizationProfileForm organization={organization} /></Section>
							<Section title="Public Pages"><PublicPagesPanel organizationId={organization.id} organizationName={organization.name} /></Section>
							<Section title="Credit & Markup"><CreditMarkupSettingsPanel organizationId={organization.id} /></Section>
							<Section title="Messaging Safety"><SafeSportOrgPolicyPanel organizationId={organization.id} canReview={isPlatformSupportMode} /></Section>
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
				<div><h2 className="font-heading text-lg font-semibold text-navy dark:text-[#f8fafc]">{title}</h2>{description && <p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">{description}</p>}</div>
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
