import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { appPaths } from "../../routes/appPaths";
import { useCurrentSupportAccess } from "./api";
import {
	useDuplicateIdentityCandidates,
	useDuplicateMergePreview,
	useResolveDuplicateIdentity,
} from "./duplicateIdentityApi";
import type {
	DuplicateCandidateGroup,
	DuplicateIdentity,
	DuplicateIdentityRef,
	IdentityDependency,
	MergePlanSeverity,
} from "./duplicateIdentityTypes";

function refKey(ref: DuplicateIdentityRef) {
	return `${ref.kind}:${ref.id}`;
}

function defaultPair(group: DuplicateCandidateGroup): [DuplicateIdentityRef | null, DuplicateIdentityRef | null] {
	const users = group.identities
		.filter((identity) => identity.ref.kind === "APP_USER")
		.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
	if (users.length > 0) {
		const target = users[0];
		const source = group.identities.find((identity) => refKey(identity.ref) !== refKey(target.ref));
		return [source?.ref ?? null, target.ref];
	}
	return [group.identities[0]?.ref ?? null, group.identities[1]?.ref ?? null];
}

function severityClass(severity: MergePlanSeverity) {
	if (severity === "BLOCKER") return "border-red-200 bg-red-50 dark:bg-red-950 text-red-900";
	if (severity === "WARNING") return "border-amber-200 bg-amber-50 dark:bg-amber-950 text-amber-900";
	return "border-slate-200 dark:border-[#334155] bg-slate-50 dark:bg-[#1e293b] text-slate-700 dark:text-[#cbd5e1]";
}

function dependencyTreatment(dependency: IdentityDependency) {
	if (dependency.historical) return "Preserve attribution";
	if (dependency.tableName === "email_verification_token" || dependency.tableName === "password_reset_token") return "Invalidate source token";
	if (["organization_membership", "role_assignment", "guardian_relationship"].includes(dependency.tableName)) return "Domain move / dedupe";
	return "Blocked unless explicitly supported";
}

export function PlatformDuplicateIdentitiesPage() {
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [selectedIndex, setSelectedIndex] = useState(0);
	const [source, setSource] = useState<DuplicateIdentityRef | null>(null);
	const [target, setTarget] = useState<DuplicateIdentityRef | null>(null);
	const [previewEnabled, setPreviewEnabled] = useState(false);
	const [reason, setReason] = useState("");
	const [confirmedTargetEmail, setConfirmedTargetEmail] = useState("");

	const candidates = useDuplicateIdentityCandidates(query);
	const selected = candidates.data?.items[selectedIndex] ?? null;
	const preview = useDuplicateMergePreview(source, target, previewEnabled);
	const supportAccess = useCurrentSupportAccess(previewEnabled && Boolean(preview.data?.requiredSupportOrganizationId));
	const resolveMutation = useResolveDuplicateIdentity();

	useEffect(() => {
		if (!selected) {
			setSource(null);
			setTarget(null);
			setPreviewEnabled(false);
			return;
		}
		const [nextSource, nextTarget] = defaultPair(selected);
		setSource(nextSource);
		setTarget(nextTarget);
		setPreviewEnabled(false);
	}, [selectedIndex, selected?.matchType, selected?.normalizedValue]);

	useEffect(() => {
		setReason("");
		setConfirmedTargetEmail("");
		resolveMutation.reset();
	}, [source?.kind, source?.id, target?.kind, target?.id, preview.data?.previewHash, resolveMutation.reset]);

	const identitiesByKey = useMemo(
		() => new Map(selected?.identities.map((identity) => [refKey(identity.ref), identity]) ?? []),
		[selected],
	);

	if (candidates.isLoading) return <LoadingState label="Scanning duplicate identity candidates…" />;
	if (candidates.isError || !candidates.data) {
		return <ErrorState message="Could not load duplicate identity candidates." onRetry={() => candidates.refetch()} />;
	}

	const requiredOrganizationId = preview.data?.requiredSupportOrganizationId ?? null;
	const supportMatches = Boolean(
		requiredOrganizationId &&
			supportAccess.data?.status === "ACTIVE" &&
			supportAccess.data.organizationId === requiredOrganizationId,
	);
	const targetEmail = preview.data?.target.email ?? "";
	const confirmationMatches = Boolean(targetEmail) && confirmedTargetEmail.trim().toLowerCase() === targetEmail.trim().toLowerCase();
	const reasonValid = reason.trim().length >= 10 && reason.trim().length <= 500;
	const canResolve = Boolean(
		preview.data?.canProceedToMutationSlice &&
			preview.data.previewHash &&
			supportMatches &&
			targetEmail &&
			confirmationMatches &&
			reasonValid &&
			!resolveMutation.isPending,
	);

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900 dark:text-[#f8fafc]">Data Integrity · Duplicate Identities</h1>
				<p className="mt-1 max-w-4xl text-slate-500 dark:text-[#cbd5e1]">
					Review likely duplicate app users and unlinked guardian shells, build a deterministic plan, then explicitly resolve only plans with no blockers. Every write is bound to a fresh preview, an active organization support session, a reason, and a typed target-account confirmation.
				</p>
			</div>

			<form
				onSubmit={(event) => {
					event.preventDefault();
					setSelectedIndex(0);
					setQuery(queryInput);
				}}
				className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-4"
			>
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900 dark:text-[#f8fafc]">
					Search identity evidence
					<input
						value={queryInput}
						onChange={(event) => setQueryInput(event.target.value)}
						placeholder="Email or phone"
						className="min-h-11 rounded-md border border-slate-300 dark:border-[#334155] px-3 py-2 font-normal"
					/>
				</label>
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">
					Search
				</button>
			</form>

			<div className="grid gap-5 xl:grid-cols-[minmax(300px,0.8fr)_minmax(0,1.7fr)]">
				<section className="overflow-hidden rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827]">
					<div className="border-b border-slate-200 dark:border-[#334155] px-4 py-3">
						<h2 className="font-semibold text-navy-900 dark:text-[#f8fafc]">Candidate groups</h2>
						<p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{candidates.data.items.length} potential duplicate keys</p>
					</div>
					<div className="max-h-[720px] divide-y divide-slate-100 dark:divide-[#334155] overflow-y-auto">
						{candidates.data.items.map((group, index) => (
							<button
								key={`${group.matchType}:${group.normalizedValue}`}
								type="button"
								onClick={() => setSelectedIndex(index)}
								className={`w-full px-4 py-3 text-left hover:bg-ice-50 hover:dark:bg-[#0f172a] ${index === selectedIndex ? "bg-ice-50 dark:bg-[#0f172a]" : ""}`}
							>
								<div className="flex items-center justify-between gap-2">
									<span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-[11px] font-semibold text-slate-600 dark:text-[#cbd5e1]">{group.matchType}</span>
									<span className="text-xs text-slate-400">{group.identities.length} identities</span>
								</div>
								<p className="mt-1 break-all font-mono text-xs text-navy-900 dark:text-[#f8fafc]">{group.normalizedValue}</p>
								<p className="mt-1 truncate text-xs text-slate-500 dark:text-[#cbd5e1]">{group.identities.map((identity) => identity.displayName).join(" · ")}</p>
							</button>
						))}
						{candidates.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">No duplicate keys match this search.</p>}
					</div>
				</section>

				<section className="flex flex-col gap-5">
					{selected ? (
						<>
							<div className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
								<div className="flex flex-wrap items-start justify-between gap-3">
									<div>
										<h2 className="font-semibold text-navy-900 dark:text-[#f8fafc]">Side-by-side identity review</h2>
										<p className="text-sm text-slate-500 dark:text-[#cbd5e1]">Choose which identity is the source and which app user should survive.</p>
									</div>
									<span className="rounded-full bg-ice-50 dark:bg-[#0f172a] px-3 py-1 text-xs font-semibold text-navy-900 dark:text-[#f8fafc]">{selected.matchType}: {selected.normalizedValue}</span>
								</div>
								<div className="mt-4 grid gap-3 md:grid-cols-2">
									{selected.identities.map((identity) => (
										<IdentityCard
											key={refKey(identity.ref)}
											identity={identity}
											source={source}
											target={target}
											onSource={() => {
												setSource(identity.ref);
												if (target && refKey(target) === refKey(identity.ref)) setTarget(null);
												setPreviewEnabled(false);
											}}
											onTarget={() => {
												setTarget(identity.ref);
												if (source && refKey(source) === refKey(identity.ref)) setSource(null);
												setPreviewEnabled(false);
											}}
										/>
									))}
								</div>
								<div className="mt-4 flex flex-wrap items-center gap-3">
									<button type="button" disabled={!source || !target} onClick={() => setPreviewEnabled(true)} className="rounded-md bg-navy-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">
										Build resolution preview
									</button>
									{source && target && <p className="text-xs text-slate-500 dark:text-[#cbd5e1]">Source: {identitiesByKey.get(refKey(source))?.displayName} → Target: {identitiesByKey.get(refKey(target))?.displayName}</p>}
								</div>
							</div>

							{previewEnabled && preview.isLoading && <LoadingState label="Building dependency and conflict preview…" />}
							{previewEnabled && preview.isError && <ErrorState message="Could not build the duplicate resolution preview." onRetry={() => preview.refetch()} />}

							{preview.data && (
								<div className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-5">
									<div className="flex flex-wrap items-start justify-between gap-3">
										<div>
											<h2 className="font-semibold text-navy-900 dark:text-[#f8fafc]">Deterministic resolution plan</h2>
											<p className="text-sm text-slate-500 dark:text-[#cbd5e1]">Strategy: <span className="font-semibold text-navy-900 dark:text-[#f8fafc]">{preview.data.strategy.replaceAll("_", " ")}</span></p>
											<p className="mt-1 font-mono text-[11px] text-slate-400">Preview {preview.data.previewHash.slice(0, 16)}…</p>
										</div>
										<span className={`rounded-full px-3 py-1 text-xs font-semibold ${preview.data.canProceedToMutationSlice ? "bg-green-100 text-green-800" : "bg-red-100 text-red-900"}`}>
											{preview.data.canProceedToMutationSlice ? "Eligible to resolve" : "Blocked"}
										</span>
									</div>

									<div className="mt-4 rounded-lg border border-slate-200 dark:border-[#334155] bg-slate-50 dark:bg-[#1e293b] px-3 py-2 text-xs text-slate-700 dark:text-[#cbd5e1]">
										<span className="font-semibold">Shared evidence revalidated:</span>{" "}
										{preview.data.sharedEvidence.length > 0
											? preview.data.sharedEvidence.map((item) => `${item.matchType}: ${item.normalizedValue}`).join(" · ")
											: "none"}
									</div>

									<div className="mt-4 flex flex-col gap-2">
										{preview.data.plan.map((item) => (
											<div key={item.code} className={`rounded-lg border px-3 py-2 text-sm ${severityClass(item.severity)}`}>
												<span className="mr-2 text-[11px] font-bold">{item.severity}</span>{item.summary}
											</div>
										))}
									</div>

									<div className="mt-5">
										<h3 className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">Dependency inventory</h3>
										{preview.data.dependencies.length > 0 ? (
											<div className="mt-2 overflow-x-auto rounded-lg border border-slate-200 dark:border-[#334155]">
												<table className="w-full text-left text-xs">
													<thead className="bg-slate-50 dark:bg-[#1e293b] text-slate-500 dark:text-[#cbd5e1]"><tr><th className="px-3 py-2">Table</th><th className="px-3 py-2">Column</th><th className="px-3 py-2">Rows</th><th className="px-3 py-2">Treatment</th></tr></thead>
													<tbody className="divide-y divide-slate-100 dark:divide-[#334155]">
														{preview.data.dependencies.map((dependency) => (
															<tr key={`${dependency.tableName}:${dependency.columnName}`}>
																<td className="px-3 py-2 font-mono">{dependency.tableName}</td>
																<td className="px-3 py-2 font-mono">{dependency.columnName}</td>
																<td className="px-3 py-2">{dependency.count}</td>
																<td className="px-3 py-2">{dependencyTreatment(dependency)}</td>
															</tr>
														))}
													</tbody>
												</table>
											</div>
										) : <p className="mt-2 text-sm text-slate-500 dark:text-[#cbd5e1]">No foreign-key dependencies were found for the source identity.</p>}
									</div>

									<div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 dark:bg-amber-950 p-4">
										<h3 className="font-semibold text-amber-950">Audited resolution</h3>
										<p className="mt-1 text-sm text-amber-900">
											This action is atomic and cannot be triggered from a stale preview. App-user merges retain the source identity for historical attribution, suspend it, invalidate its active authentication tokens, and never rewrite audit actors.
										</p>

										{requiredOrganizationId ? (
											<div className="mt-3 rounded-lg bg-white/70 px-3 py-2 text-sm text-amber-950">
												{supportAccess.isLoading ? "Checking support-session scope…" : supportMatches ? (
													<>Active support session: <span className="font-semibold">{supportAccess.data?.organizationName}</span></>
												) : (
													<>
														An active support session for organization <span className="font-mono text-xs">{requiredOrganizationId}</span> is required.{" "}
														<Link to={appPaths.platformOrganization(requiredOrganizationId)} className="font-semibold underline">Open organization</Link> to start or switch the reasoned support session.
													</>
												)}
											</div>
										) : <p className="mt-3 text-sm font-medium text-red-800">No safe single-organization support scope exists for this plan.</p>}

										<label className="mt-4 flex flex-col gap-1 text-sm font-medium text-amber-950">
											Resolution reason
											<textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={3} placeholder="Why these records are confirmed duplicates and why this surviving account is correct" className="rounded-md border border-amber-300 bg-white dark:bg-[#111827] px-3 py-2 font-normal text-slate-800 dark:text-slate-200" />
											<span className="text-xs font-normal text-amber-800">{reason.trim().length}/500 · minimum 10 characters</span>
										</label>

										<label className="mt-3 flex flex-col gap-1 text-sm font-medium text-amber-950">
											Type surviving target email to confirm
											<input value={confirmedTargetEmail} onChange={(event) => setConfirmedTargetEmail(event.target.value)} placeholder={targetEmail || "Target must have an email"} className="min-h-11 rounded-md border border-amber-300 bg-white dark:bg-[#111827] px-3 py-2 font-normal text-slate-800 dark:text-slate-200" />
										</label>

										<button
											type="button"
											disabled={!canResolve || !source || !target || !preview.data || !supportAccess.data}
											onClick={() => {
												if (!source || !target || !preview.data || !supportAccess.data) return;
												resolveMutation.mutate({
													sourceKind: source.kind,
													sourceId: source.id,
													targetKind: target.kind,
													targetId: target.id,
													previewHash: preview.data.previewHash,
													supportAccessId: supportAccess.data.id,
													reason: reason.trim(),
													confirmedTargetEmail: confirmedTargetEmail.trim(),
												});
											}}
											className="mt-4 rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-40"
										>
											{resolveMutation.isPending ? "Resolving…" : "Resolve duplicate identity"}
										</button>

										{resolveMutation.isError && <p className="mt-3 rounded-lg bg-red-100 px-3 py-2 text-sm text-red-900">{resolveMutation.error instanceof Error ? resolveMutation.error.message : "Identity resolution failed."}</p>}
										{resolveMutation.data && (
											<div className="mt-4 rounded-lg border border-green-200 bg-green-50 dark:bg-green-950 p-3 text-sm text-green-900">
												<p className="font-semibold">Resolution completed</p>
												<p className="mt-1">Operation <span className="font-mono text-xs">{resolveMutation.data.operationId}</span> · {resolveMutation.data.operationType.replaceAll("_", " ")}</p>
												<p className="mt-1 text-xs">Memberships moved/deduped: {resolveMutation.data.outcome.membershipsMoved}/{resolveMutation.data.outcome.membershipsDeduplicated} · Roles: {resolveMutation.data.outcome.roleAssignmentsMoved}/{resolveMutation.data.outcome.roleAssignmentsDeduplicated} · Guardian links: {resolveMutation.data.outcome.guardianRelationshipsMoved}/{resolveMutation.data.outcome.guardianRelationshipsDeduplicated} · Thread memberships: {resolveMutation.data.outcome.messageThreadMembershipsMoved}/{resolveMutation.data.outcome.messageThreadMembershipsDeduplicated} · Message access moved/already present: {resolveMutation.data.outcome.messageRecipientAccessMoved}/{resolveMutation.data.outcome.messageRecipientAccessAlreadyPresent} · Announcement access: {resolveMutation.data.outcome.announcementRecipientAccessMoved}/{resolveMutation.data.outcome.announcementRecipientAccessAlreadyPresent} · Auth tokens invalidated: {resolveMutation.data.outcome.authTokensInvalidated}</p>
												<Link to="/app/history" className="mt-2 inline-block font-semibold underline">Open audit history</Link>
											</div>
										)}
									</div>
								</div>
							)}
						</>
					) : (
						<div className="rounded-xl border border-slate-200 dark:border-[#334155] bg-white dark:bg-[#111827] p-8 text-center text-sm text-slate-500 dark:text-[#cbd5e1]">Select a candidate group to review it.</div>
					)}
				</section>
			</div>
		</div>
	);
}

function IdentityCard({
	identity,
	source,
	target,
	onSource,
	onTarget,
}: {
	identity: DuplicateIdentity;
	source: DuplicateIdentityRef | null;
	target: DuplicateIdentityRef | null;
	onSource: () => void;
	onTarget: () => void;
}) {
	const key = refKey(identity.ref);
	return (
		<div className="rounded-lg border border-slate-200 dark:border-[#334155] p-4">
			<div className="flex items-start justify-between gap-2">
				<div><p className="font-semibold text-navy-900 dark:text-[#f8fafc]">{identity.displayName}</p><p className="text-xs text-slate-500 dark:text-[#cbd5e1]">{identity.ref.kind === "APP_USER" ? "App user" : "Guardian shell"} · {identity.status}</p></div>
				{identity.platformAdministrator && <span className="rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-bold text-red-900">PLATFORM ADMIN</span>}
			</div>
			<dl className="mt-3 grid gap-2 text-xs">
				<div><dt className="text-slate-400">Email</dt><dd className="break-all text-slate-700 dark:text-[#cbd5e1]">{identity.email ?? "—"}</dd></div>
				<div><dt className="text-slate-400">Phone</dt><dd className="text-slate-700 dark:text-[#cbd5e1]">{identity.phone ?? "—"}</dd></div>
				{identity.organizationName && <div><dt className="text-slate-400">Organization / household</dt><dd className="text-slate-700 dark:text-[#cbd5e1]">{identity.organizationName} · {identity.householdName ?? "—"}</dd></div>}
				<div><dt className="text-slate-400">External IDs</dt><dd className="break-all text-slate-700 dark:text-[#cbd5e1]">{identity.externalIds.join(", ") || "—"}</dd></div>
				<div><dt className="text-slate-400">Identity ID</dt><dd className="break-all font-mono text-[10px] text-slate-500 dark:text-[#cbd5e1]">{identity.ref.id}</dd></div>
			</dl>
			{identity.memberships.length > 0 && <div className="mt-3 text-xs"><p className="font-semibold text-slate-500 dark:text-[#cbd5e1]">Memberships</p>{identity.memberships.map((membership) => <p key={membership.organizationId} className="mt-1 text-slate-600 dark:text-[#cbd5e1]">{membership.organizationName}: {membership.role} / {membership.status}</p>)}</div>}
			{identity.roleAssignments.length > 0 && <p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]">Active resource roles: {identity.roleAssignments.length}</p>}
			{identity.guardianLinks.length > 0 && <p className="mt-1 text-xs text-slate-500 dark:text-[#cbd5e1]">Active guardian links: {identity.guardianLinks.length}</p>}
			{identity.mergedIntoUserId && <p className="mt-2 rounded bg-red-50 dark:bg-red-950 px-2 py-1 text-xs text-red-800">Already merged into {identity.mergedIntoUserId}</p>}
			<div className="mt-4 flex gap-2">
				<button type="button" onClick={onSource} className={`rounded-md border px-3 py-1.5 text-xs font-semibold ${source && refKey(source) === key ? "border-navy-900 bg-navy-900 text-white" : "border-slate-300 dark:border-[#334155] text-slate-600 dark:text-[#cbd5e1]"}`}>Source</button>
				<button type="button" onClick={onTarget} className={`rounded-md border px-3 py-1.5 text-xs font-semibold ${target && refKey(target) === key ? "border-azure-blue bg-ice-50 dark:bg-[#0f172a] text-navy-900 dark:text-[#f8fafc]" : "border-slate-300 dark:border-[#334155] text-slate-600 dark:text-[#cbd5e1]"}`}>Surviving target</button>
			</div>
		</div>
	);
}
