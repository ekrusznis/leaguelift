import { useEffect, useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { useExecuteSeasonRollover, usePreviewSeasonRollover, useSeasonRolloverTeams } from "./api";
import type { SeasonRolloverPreview, SeasonRolloverRequest, SeasonRolloverResult } from "./types";

const INITIAL_OPTIONS = {
	archiveSourceTeam: true,
	copyRoster: true,
	copyStaff: true,
	copyBranding: true,
};

export function SeasonRolloverPanel({ organizationId }: { organizationId: string }) {
	const teams = useSeasonRolloverTeams(organizationId);
	const previewMutation = usePreviewSeasonRollover(organizationId);
	const executeMutation = useExecuteSeasonRollover(organizationId);
	const activeTeams = useMemo(() => teams.data?.items.filter((team) => team.status === "ACTIVE") ?? [], [teams.data]);
	const [sourceTeamId, setSourceTeamId] = useState("");
	const [newTeamName, setNewTeamName] = useState("");
	const [newSeason, setNewSeason] = useState("");
	const [options, setOptions] = useState(INITIAL_OPTIONS);
	const [preview, setPreview] = useState<SeasonRolloverPreview | null>(null);
	const [result, setResult] = useState<SeasonRolloverResult | null>(null);
	const [error, setError] = useState<string | null>(null);

	const source = activeTeams.find((team) => team.id === sourceTeamId);

	useEffect(() => {
		setPreview(null);
		setResult(null);
	}, [sourceTeamId, newTeamName, newSeason, options]);

	function request(): SeasonRolloverRequest {
		return { sourceTeamId, newTeamName, newSeason, ...options };
	}

	function selectSource(teamId: string) {
		setSourceTeamId(teamId);
		const selected = activeTeams.find((team) => team.id === teamId);
		if (selected) {
			setNewTeamName(`${selected.name} - ${selected.season ? "Next Season" : "New Season"}`);
			setNewSeason("");
		}
	}

	async function previewRollover(event: React.FormEvent) {
		event.preventDefault();
		setError(null);
		setResult(null);
		try {
			setPreview(await previewMutation.mutateAsync(request()));
		} catch {
			setPreview(null);
			setError("The rollover could not be previewed. Check the source team, new name, and season.");
		}
	}

	async function executeRollover() {
		if (!preview) return;
		setError(null);
		try {
			const completed = await executeMutation.mutateAsync({
				...request(),
				expectedConfirmationHash: preview.confirmationHash,
			});
			setResult(completed);
			setPreview(null);
		} catch {
			setError("The source setup changed or the destination name is no longer available. Preview the rollover again.");
		}
	}

	return (
		<section className="flex flex-col gap-4 rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<div>
				<h3 className="font-heading text-base font-semibold text-navy">Season rollover</h3>
				<p className="mt-1 max-w-3xl text-sm text-slate-gray">
					Create the next-season team from an active team, preview every selected setup item, and optionally archive the source.
					Historical and financial records are never moved or copied.
				</p>
			</div>

			<form onSubmit={previewRollover} className="flex flex-col gap-4">
				<div className="grid gap-3 md:grid-cols-3">
					<label className="flex flex-col gap-1 text-sm font-medium text-navy" htmlFor="rollover-source-team">
						Source team
						<select
							id="rollover-source-team"
							value={sourceTeamId}
							onChange={(event) => selectSource(event.target.value)}
							required
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						>
							<option value="">Select an active team</option>
							{activeTeams.map((team) => (
								<option key={team.id} value={team.id}>{team.name}{team.season ? ` (${team.season})` : ""}</option>
							))}
						</select>
					</label>
					<label className="flex flex-col gap-1 text-sm font-medium text-navy" htmlFor="rollover-new-team-name">
						New team name
						<input
							id="rollover-new-team-name"
							value={newTeamName}
							onChange={(event) => setNewTeamName(event.target.value)}
							maxLength={120}
							required
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</label>
					<label className="flex flex-col gap-1 text-sm font-medium text-navy" htmlFor="rollover-new-season">
						New season
						<input
							id="rollover-new-season"
							value={newSeason}
							onChange={(event) => setNewSeason(event.target.value)}
							placeholder="2027-2028"
							maxLength={120}
							required
							className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
						/>
					</label>
				</div>

				<fieldset className="rounded-lg border border-slate-gray/20 p-4">
					<legend className="px-1 text-sm font-semibold text-navy">Select what to carry forward</legend>
					<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
						<Option checked={options.copyRoster} onChange={(checked) => setOptions((current) => ({ ...current, copyRoster: checked }))} label="Active roster links" />
						<Option checked={options.copyStaff} onChange={(checked) => setOptions((current) => ({ ...current, copyStaff: checked }))} label="Explicit team staff" />
						<Option checked={options.copyBranding} onChange={(checked) => setOptions((current) => ({ ...current, copyBranding: checked }))} label="Logo and cover" />
						<Option checked={options.archiveSourceTeam} onChange={(checked) => setOptions((current) => ({ ...current, archiveSourceTeam: checked }))} label="Archive source team" />
					</div>
				</fieldset>

				<div className="flex flex-wrap items-center gap-3">
					<Button type="submit" disabled={!source || previewMutation.isPending || !newTeamName.trim() || !newSeason.trim()}>
						{previewMutation.isPending ? "Building preview…" : "Preview rollover"}
					</Button>
					<p className="text-xs text-slate-gray">Preview is required again whenever an option or source value changes.</p>
				</div>
			</form>

			{teams.isError && <p role="alert" className="text-sm text-error-red">Could not load teams for rollover.</p>}
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
			{preview && <RolloverPreview preview={preview} pending={executeMutation.isPending} onConfirm={executeRollover} />}
			{result && (
				<div role="status" className="rounded-lg border border-victory-green/30 bg-victory-green/5 p-4 text-sm text-navy">
					<p className="font-semibold">Season rollover complete: {result.destinationTeam.name} ({result.destinationTeam.season}).</p>
					<p className="mt-1">
						Copied {result.rosterCopiedCount} roster link{result.rosterCopiedCount === 1 ? "" : "s"}, {result.staffCopiedCount} staff grant{result.staffCopiedCount === 1 ? "" : "s"}, and {result.brandingCopiedCount} branding assignment{result.brandingCopiedCount === 1 ? "" : "s"}.
						{result.sourceArchived ? " The source team was archived." : " The source team remains active."}
					</p>
				</div>
			)}
		</section>
	);
}

function Option({ checked, onChange, label }: { checked: boolean; onChange: (checked: boolean) => void; label: string }) {
	return (
		<label className="flex min-h-11 items-center gap-2 rounded-md border border-slate-gray/20 px-3 py-2 text-sm text-navy">
			<input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
			{label}
		</label>
	);
}

function RolloverPreview({ preview, pending, onConfirm }: { preview: SeasonRolloverPreview; pending: boolean; onConfirm: () => void }) {
	return (
		<div className="flex flex-col gap-4 rounded-lg border border-info-blue/30 bg-info-blue/5 p-4">
			<div>
				<h4 className="font-heading font-semibold text-navy">Confirm exact rollover</h4>
				<p className="mt-1 text-sm text-slate-gray">
					{preview.sourceTeam.name} → {preview.destinationTeam.name} ({preview.destinationTeam.season}); sport and contact email are carried forward.
				</p>
			</div>
			<div className="grid gap-3 md:grid-cols-3">
				<PreviewList title={`Roster links (${preview.roster.length})`} items={preview.roster.map((item) => `${item.displayName}${item.priorJoinedAt ? ` — prior joined ${item.priorJoinedAt}` : ""}`)} empty="No roster links selected or available." />
				<PreviewList title={`Team staff (${preview.staff.length})`} items={preview.staff.map((item) => `${item.displayName} — ${item.role.replaceAll("_", " ")}`)} empty="No explicit team staff selected or available." />
				<PreviewList title={`Branding (${preview.branding.length})`} items={preview.branding.map((item) => `${item.usageSlot}: ${item.fileName}`)} empty="No ready logo or cover selected or available." />
			</div>
			{preview.warnings.length > 0 && (
				<div className="rounded-md border border-championship-gold/40 bg-championship-gold/10 p-3 text-sm text-navy">
					<p className="font-medium">Important behavior</p>
					<ul className="mt-1 list-disc space-y-1 pl-5">{preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>
				</div>
			)}
			<div className="rounded-md border border-slate-gray/20 bg-pure-white p-3 text-sm">
				<p className="font-medium text-navy">Never copied by this workflow</p>
				<ul className="mt-1 list-disc space-y-1 pl-5 text-slate-gray">{preview.excludedData.map((item) => <li key={item}>{item}</li>)}</ul>
			</div>
			<div className="flex flex-wrap items-center gap-3">
				<Button type="button" onClick={onConfirm} disabled={pending}>{pending ? "Completing rollover…" : "Confirm and create next-season team"}</Button>
				<p className="text-xs text-slate-gray">Confirmation hash: <span className="font-mono">{preview.confirmationHash.slice(0, 12)}…</span></p>
			</div>
		</div>
	);
}

function PreviewList({ title, items, empty }: { title: string; items: string[]; empty: string }) {
	return (
		<div className="min-w-0 rounded-md border border-slate-gray/20 bg-pure-white p-3">
			<p className="text-sm font-semibold text-navy">{title}</p>
			{items.length > 0 ? <ul className="mt-2 max-h-48 list-disc space-y-1 overflow-auto pl-5 text-sm text-slate-gray">{items.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ul> : <p className="mt-2 text-sm text-slate-gray">{empty}</p>}
		</div>
	);
}
