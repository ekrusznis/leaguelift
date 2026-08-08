import { useEffect, useMemo, useState } from "react";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useDuplicateIdentityCandidates, useDuplicateMergePreview } from "./duplicateIdentityApi";
import type { DuplicateCandidateGroup, DuplicateIdentity, DuplicateIdentityRef, MergePlanSeverity } from "./duplicateIdentityTypes";

function refKey(ref: DuplicateIdentityRef) { return `${ref.kind}:${ref.id}`; }
function label(identity: DuplicateIdentity) { return `${identity.displayName} · ${identity.ref.kind === "APP_USER" ? "App user" : "Guardian shell"}`; }
function defaultPair(group: DuplicateCandidateGroup): [DuplicateIdentityRef | null, DuplicateIdentityRef | null] {
	const users = group.identities.filter((identity) => identity.ref.kind === "APP_USER").sort((a, b) => a.createdAt.localeCompare(b.createdAt));
	if (users.length > 0) {
		const target = users[0];
		const source = group.identities.find((identity) => refKey(identity.ref) !== refKey(target.ref));
		return [source?.ref ?? null, target.ref];
	}
	return [group.identities[0]?.ref ?? null, group.identities[1]?.ref ?? null];
}
function severityClass(severity: MergePlanSeverity) {
	if (severity === "BLOCKER") return "border-red-200 bg-red-50 text-red-900";
	if (severity === "WARNING") return "border-amber-200 bg-amber-50 text-amber-900";
	return "border-slate-200 bg-slate-50 text-slate-700";
}

export function PlatformDuplicateIdentitiesPage() {
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [selectedIndex, setSelectedIndex] = useState(0);
	const [source, setSource] = useState<DuplicateIdentityRef | null>(null);
	const [target, setTarget] = useState<DuplicateIdentityRef | null>(null);
	const [previewEnabled, setPreviewEnabled] = useState(false);
	const candidates = useDuplicateIdentityCandidates(query);
	const selected = candidates.data?.items[selectedIndex] ?? null;

	useEffect(() => {
		if (!selected) { setSource(null); setTarget(null); setPreviewEnabled(false); return; }
		const [nextSource, nextTarget] = defaultPair(selected);
		setSource(nextSource); setTarget(nextTarget); setPreviewEnabled(false);
	}, [selectedIndex, selected?.matchType, selected?.normalizedValue]);

	const identitiesByKey = useMemo(() => new Map(selected?.identities.map((identity) => [refKey(identity.ref), identity]) ?? []), [selected]);
	const preview = useDuplicateMergePreview(source, target, previewEnabled);

	if (candidates.isLoading) return <LoadingState label="Scanning duplicate identity candidates…" />;
	if (candidates.isError || !candidates.data) return <ErrorState message="Could not load duplicate identity candidates." onRetry={() => candidates.refetch()} />;

	return (
		<div className="flex flex-col gap-6">
			<div>
				<h1 className="font-heading text-2xl font-bold text-navy-900">Data Integrity · Duplicate Identities</h1>
				<p className="mt-1 max-w-4xl text-slate-500">Read-only review of likely duplicate app users and unlinked guardian shells. Matching uses normalized email and phone evidence; external import IDs are shown as corroborating evidence. This page cannot merge, link, suspend, or delete records.</p>
			</div>

			<form onSubmit={(event) => { event.preventDefault(); setSelectedIndex(0); setQuery(queryInput); }} className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4">
				<label className="flex min-w-64 flex-1 flex-col gap-1 text-sm font-medium text-navy-900">Search identity evidence<input value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="Email or phone" className="min-h-11 rounded-md border border-slate-300 px-3 py-2 font-normal" /></label>
				<button type="submit" className="min-h-11 rounded-md bg-navy-900 px-4 py-2 font-semibold text-white hover:bg-navy-800">Search</button>
			</form>

			<div className="grid gap-5 xl:grid-cols-[minmax(300px,0.8fr)_minmax(0,1.7fr)]">
				<section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
					<div className="border-b border-slate-200 px-4 py-3"><h2 className="font-semibold text-navy-900">Candidate groups</h2><p className="text-xs text-slate-500">{candidates.data.items.length} potential duplicate keys</p></div>
					<div className="max-h-[620px] divide-y divide-slate-100 overflow-y-auto">
						{candidates.data.items.map((group, index) => (
							<button key={`${group.matchType}:${group.normalizedValue}`} type="button" onClick={() => setSelectedIndex(index)} className={`w-full px-4 py-3 text-left hover:bg-ice-50 ${index === selectedIndex ? "bg-ice-50" : ""}`}>
								<div className="flex items-center justify-between gap-2"><span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600">{group.matchType}</span><span className="text-xs text-slate-400">{group.identities.length} identities</span></div>
								<p className="mt-1 break-all font-mono text-xs text-navy-900">{group.normalizedValue}</p>
								<p className="mt-1 truncate text-xs text-slate-500">{group.identities.map((identity) => identity.displayName).join(" · ")}</p>
							</button>
						))}
						{candidates.data.items.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No duplicate keys match this search.</p>}
					</div>
				</section>

				<section className="flex flex-col gap-5">
					{selected ? <>
						<div className="rounded-xl border border-slate-200 bg-white p-5">
							<div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-semibold text-navy-900">Side-by-side identity review</h2><p className="text-sm text-slate-500">Choose which identity is the source and which app user should survive.</p></div><span className="rounded-full bg-ice-50 px-3 py-1 text-xs font-semibold text-navy-900">{selected.matchType}: {selected.normalizedValue}</span></div>
							<div className="mt-4 grid gap-3 md:grid-cols-2">
								{selected.identities.map((identity) => <IdentityCard key={refKey(identity.ref)} identity={identity} source={source} target={target} onSource={() => { setSource(identity.ref); if (target && refKey(target) === refKey(identity.ref)) setTarget(null); setPreviewEnabled(false); }} onTarget={() => { setTarget(identity.ref); if (source && refKey(source) === refKey(identity.ref)) setSource(null); setPreviewEnabled(false); }} />)}
							</div>
							<div className="mt-4 flex flex-wrap items-center gap-3">
								<button type="button" disabled={!source || !target} onClick={() => setPreviewEnabled(true)} className="rounded-md bg-navy-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">Build dry-run preview</button>
								{source && target && <p className="text-xs text-slate-500">Source: {identitiesByKey.get(refKey(source))?.displayName} → Target: {identitiesByKey.get(refKey(target))?.displayName}</p>}
							</div>
						</div>

						{previewEnabled && preview.isLoading && <LoadingState label="Building dependency and conflict preview…" />}
						{previewEnabled && preview.isError && <ErrorState message="Could not build the duplicate resolution preview." onRetry={() => preview.refetch()} />}
						{preview.data && <div className="rounded-xl border border-slate-200 bg-white p-5">
							<div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-semibold text-navy-900">Deterministic resolution plan</h2><p className="text-sm text-slate-500">Strategy: <span className="font-semibold text-navy-900">{preview.data.strategy.replaceAll("_", " ")}</span></p></div><span className={`rounded-full px-3 py-1 text-xs font-semibold ${preview.data.canProceedToMutationSlice ? "bg-green-100 text-green-800" : "bg-red-100 text-red-900"}`}>{preview.data.canProceedToMutationSlice ? "No hard blockers" : "Blocked"}</span></div>
							<div className="mt-4 flex flex-col gap-2">{preview.data.plan.map((item) => <div key={item.code} className={`rounded-lg border px-3 py-2 text-sm ${severityClass(item.severity)}`}><span className="mr-2 text-[11px] font-bold">{item.severity}</span>{item.summary}</div>)}</div>
							<div className="mt-5"><h3 className="text-sm font-semibold text-navy-900">Dependency inventory</h3>{preview.data.dependencies.length > 0 ? <div className="mt-2 overflow-x-auto rounded-lg border border-slate-200"><table className="w-full text-left text-xs"><thead className="bg-slate-50 text-slate-500"><tr><th className="px-3 py-2">Table</th><th className="px-3 py-2">Column</th><th className="px-3 py-2">Rows</th><th className="px-3 py-2">Treatment</th></tr></thead><tbody className="divide-y divide-slate-100">{preview.data.dependencies.map((dependency) => <tr key={`${dependency.tableName}:${dependency.columnName}`}><td className="px-3 py-2 font-mono">{dependency.tableName}</td><td className="px-3 py-2 font-mono">{dependency.columnName}</td><td className="px-3 py-2">{dependency.count}</td><td className="px-3 py-2">{dependency.historical ? "Preserve attribution" : "Resolve in 27.4"}</td></tr>)}</tbody></table></div> : <p className="mt-2 text-sm text-slate-500">No foreign-key dependencies were found for the source identity.</p>}</div>
							<p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">Preview only. Phase 27.3 intentionally exposes no mutation endpoint.</p>
						</div>}
					</> : <div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">Select a candidate group to review it.</div>}
				</section>
			</div>
		</div>
	);
}

function IdentityCard({ identity, source, target, onSource, onTarget }: { identity: DuplicateIdentity; source: DuplicateIdentityRef | null; target: DuplicateIdentityRef | null; onSource: () => void; onTarget: () => void; }) {
	const key = refKey(identity.ref);
	return <div className="rounded-lg border border-slate-200 p-4">
		<div className="flex items-start justify-between gap-2"><div><p className="font-semibold text-navy-900">{identity.displayName}</p><p className="text-xs text-slate-500">{identity.ref.kind === "APP_USER" ? "App user" : "Guardian shell"} · {identity.status}</p></div>{identity.platformAdministrator && <span className="rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-bold text-red-900">PLATFORM ADMIN</span>}</div>
		<dl className="mt-3 grid gap-2 text-xs"><div><dt className="text-slate-400">Email</dt><dd className="break-all text-slate-700">{identity.email ?? "—"}</dd></div><div><dt className="text-slate-400">Phone</dt><dd className="text-slate-700">{identity.phone ?? "—"}</dd></div>{identity.organizationName && <div><dt className="text-slate-400">Organization / household</dt><dd className="text-slate-700">{identity.organizationName} · {identity.householdName ?? "—"}</dd></div>}<div><dt className="text-slate-400">External IDs</dt><dd className="break-all text-slate-700">{identity.externalIds.join(", ") || "—"}</dd></div><div><dt className="text-slate-400">Identity ID</dt><dd className="break-all font-mono text-[10px] text-slate-500">{identity.ref.id}</dd></div></dl>
		{identity.memberships.length > 0 && <div className="mt-3 text-xs"><p className="font-semibold text-slate-500">Memberships</p>{identity.memberships.map((membership) => <p key={membership.organizationId} className="mt-1 text-slate-600">{membership.organizationName}: {membership.role} / {membership.status}</p>)}</div>}
		<div className="mt-4 flex gap-2"><button type="button" onClick={onSource} className={`rounded-md border px-3 py-1.5 text-xs font-semibold ${source && refKey(source) === key ? "border-navy-900 bg-navy-900 text-white" : "border-slate-300 text-slate-600"}`}>Source</button><button type="button" onClick={onTarget} className={`rounded-md border px-3 py-1.5 text-xs font-semibold ${target && refKey(target) === key ? "border-azure-blue bg-ice-50 text-navy-900" : "border-slate-300 text-slate-600"}`}>Surviving target</button></div>
	</div>;
}
