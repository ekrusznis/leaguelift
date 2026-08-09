import { Link } from "react-router-dom";
import { useContexts } from "../../authorization/api";
import { Capabilities } from "../../authorization/capabilityConstants";
import { hasCapability } from "../../authorization/capabilities";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useOrganization } from "../organizations/api";
import { useCurrentSupportAccess } from "../platformAdmin/api";
import { organizationSettingsGroups } from "./organizationSettings";

export function OrganizationSettingsDirectory() {
	const contexts = useContexts();
	const isPlatformAdmin = hasCapability(contexts.data, Capabilities.PLATFORM_SUPPORT_ACCESS, {
		contextType: "PLATFORM_ADMIN",
		resourceId: null,
	});
	const supportAccess = useCurrentSupportAccess(isPlatformAdmin);

	if (contexts.isLoading || (isPlatformAdmin && supportAccess.isLoading)) {
		return <LoadingState label="Loading organization settings access…" />;
	}
	if (contexts.isError || (isPlatformAdmin && supportAccess.isError)) {
		return (
			<ErrorState
				message="Could not determine which organization settings you can manage."
				onRetry={() => {
					void contexts.refetch();
					if (isPlatformAdmin) void supportAccess.refetch();
				}}
			/>
		);
	}

	const organizationIds = new Set<string>();
	for (const context of contexts.data ?? []) {
		if (context.contextType !== "ORGANIZATION" || !context.resourceId) continue;
		const organizationId = context.resourceId;
		const canManageOrganization = hasCapability(contexts.data, Capabilities.ORG_MANAGE, {
			contextType: "ORGANIZATION",
			resourceId: organizationId,
		});
		const canManagePayouts = hasCapability(contexts.data, Capabilities.ORG_PAYOUT_MANAGE, {
			contextType: "ORGANIZATION",
			resourceId: organizationId,
		});
		if (canManageOrganization || canManagePayouts) organizationIds.add(organizationId);
	}

	const supportOrganizationId =
		isPlatformAdmin && supportAccess.data?.status === "ACTIVE"
			? supportAccess.data.organizationId
			: null;
	if (supportOrganizationId) organizationIds.add(supportOrganizationId);

	if (organizationIds.size === 0) return null;

	return (
		<section className="rounded-xl border border-slate-200 bg-white p-5" aria-labelledby="organization-settings-heading">
			<div>
				<p className="text-sm font-semibold uppercase tracking-wide text-victory-green">Organization controls</p>
				<h2 id="organization-settings-heading" className="mt-1 font-heading text-xl font-semibold text-navy-900">Organization settings</h2>
				<p className="mt-1 max-w-3xl text-sm text-slate-600">
					Only organizations you can manage appear here. These links open the existing domain-owned controls; this page does not copy business settings into a parallel settings store.
				</p>
			</div>
			<div className="mt-5 space-y-5">
				{Array.from(organizationIds).map((organizationId) => (
					<OrganizationSettingsCard
						key={organizationId}
						organizationId={organizationId}
						contexts={contexts.data}
						supportMode={supportOrganizationId === organizationId}
					/>
				))}
			</div>
		</section>
	);
}

function OrganizationSettingsCard({
	organizationId,
	contexts,
	supportMode,
}: {
	organizationId: string;
	contexts: ReturnType<typeof useContexts>["data"];
	supportMode: boolean;
}) {
	const organization = useOrganization(organizationId, true);
	const canManageOrganization = supportMode || hasCapability(contexts, Capabilities.ORG_MANAGE, {
		contextType: "ORGANIZATION",
		resourceId: organizationId,
	});
	const canManagePayouts = supportMode || hasCapability(contexts, Capabilities.ORG_PAYOUT_MANAGE, {
		contextType: "ORGANIZATION",
		resourceId: organizationId,
	});
	const groups = organizationSettingsGroups(organizationId, { canManageOrganization, canManagePayouts });

	if (organization.isLoading) return <LoadingState label="Loading organization…" />;
	if (organization.isError || !organization.data) {
		return <ErrorState message="Could not load one of your manageable organizations." onRetry={() => organization.refetch()} />;
	}

	return (
		<div className="rounded-xl border border-slate-200 bg-ice-white p-4">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h3 className="font-heading text-lg font-semibold text-navy-900">{organization.data.name}</h3>
					<p className="mt-1 text-xs text-slate-500">/{organization.data.slug}</p>
					{supportMode && (
						<p className="mt-2 text-xs font-semibold uppercase tracking-wide text-amber-700">Reasoned Platform Support access</p>
					)}
				</div>
				<Link to={`/app/organizations/${organizationId}/settings`} className="rounded-lg bg-navy px-4 py-2 text-sm font-semibold text-white hover:bg-navy/90">
					Open organization settings
				</Link>
			</div>

			<div className="mt-4 grid gap-3 md:grid-cols-2">
				{groups.map((group) => (
					<div key={group.key} className="rounded-lg border border-slate-200 bg-white p-4">
						<h4 className="font-semibold text-navy-900">{group.title}</h4>
						<p className="mt-1 text-xs leading-5 text-slate-600">{group.description}</p>
						<div className="mt-3 flex flex-wrap gap-x-3 gap-y-2">
							{group.links.map((link) => (
								<Link key={`${group.key}-${link.label}`} to={link.to} className="text-sm font-semibold text-azure-blue hover:underline">
									{link.label} →
								</Link>
							))}
						</div>
					</div>
				))}
			</div>

			{canManageOrganization && (
				<p className="mt-4 text-xs text-slate-500">
					No organization-level notification or event-default toggle is added in this slice unless an existing delivery/event service consumes it. Individual SMS consent and Phase 25 safety requirements remain non-overridable.
				</p>
			)}
		</div>
	);
}
