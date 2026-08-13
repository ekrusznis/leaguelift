import { useRef, useState } from "react";
import { Button } from "../../components/Button";
import { useTeams } from "../teams/api";
import { useImportEventsCsv } from "./api";
import type { CsvImportResult } from "./types";

/**
 * CSV schedule import (Phase 12 slice 2, ADR-032) — one upload is scoped to exactly
 * one team (or org-wide) and one shared timezone, never per-row. The file is read
 * client-side via FileReader and sent as a plain string in the JSON body, matching
 * this codebase's existing "no multipart anywhere" convention rather than
 * introducing a new upload pattern for one feature.
 */
export function CsvImportSection({ organizationId, readOnly = false }: { organizationId: string; readOnly?: boolean }) {
	const { data: teams } = useTeams(organizationId);
	const importCsv = useImportEventsCsv(organizationId);
	const fileInputRef = useRef<HTMLInputElement>(null);
	const [showForm, setShowForm] = useState(false);
	const [teamId, setTeamId] = useState("");
	const [timezone, setTimezone] = useState("");
	const [fileName, setFileName] = useState<string | null>(null);
	const [csvContent, setCsvContent] = useState<string | null>(null);
	const [result, setResult] = useState<CsvImportResult | null>(null);
	const [error, setError] = useState<string | null>(null);

	function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
		const file = event.target.files?.[0];
		if (!file) return;
		setFileName(file.name);
		setResult(null);
		setError(null);
		const reader = new FileReader();
		reader.onload = () => setCsvContent(typeof reader.result === "string" ? reader.result : null);
		reader.readAsText(file);
	}

	async function handleSubmit(event: React.FormEvent) {
		event.preventDefault();
		setError(null);
		setResult(null);
		if (!csvContent) {
			setError("Choose a CSV file first.");
			return;
		}
		if (!timezone.trim()) {
			setError("Timezone is required.");
			return;
		}
		try {
			const response = await importCsv.mutateAsync({ teamId: teamId || null, timezone: timezone.trim(), csvContent });
			setResult(response);
			setFileName(null);
			setCsvContent(null);
			if (fileInputRef.current) fileInputRef.current.value = "";
		} catch {
			setError("Could not import that file. Check the required external_id/event_type columns and try again.");
		}
	}

	return (
		<div className="flex flex-col gap-3">
			<div className="flex items-center justify-between">
				<h3 className="font-heading text-base font-semibold text-navy dark:text-[#f8fafc]">CSV Import</h3>
				{!readOnly && (
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Import a schedule"}
					</Button>
				)}
			</div>

			{!readOnly && showForm && (
				<form onSubmit={handleSubmit} className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" noValidate aria-label="Import events from CSV">
					<div className="flex flex-wrap gap-3">
						<div className="flex flex-col gap-1">
							<label htmlFor="csv-team" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Team
							</label>
							<select
								id="csv-team"
								value={teamId}
								onChange={(event) => setTeamId(event.target.value)}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							>
								<option value="">Org-wide (no single team)</option>
								{teams?.items?.map((team) => (
									<option key={team.id} value={team.id}>
										{team.name}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1">
							<label htmlFor="csv-timezone" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
								Timezone <span aria-hidden>*</span>
							</label>
							<input
								id="csv-timezone"
								type="text"
								placeholder="America/New_York"
								value={timezone}
								onChange={(event) => setTimezone(event.target.value)}
								className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2"
							/>
						</div>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="csv-file" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
							CSV file <span aria-hidden>*</span>
						</label>
						<input
							ref={fileInputRef}
							id="csv-file"
							type="file"
							accept=".csv,text/csv"
							onChange={handleFileChange}
							className="text-sm text-navy dark:text-[#f8fafc]"
						/>
						<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">
							Required columns: <span className="font-mono">external_id</span>, <span className="font-mono">event_type</span>. Optional:
							title, description, opponent_name, start_at, end_at, arrival_at, venue_name, address, area. Date/time columns must be
							ISO-8601 (e.g. 2026-09-05T15:30:00Z).
						</p>
						{fileName && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Selected: {fileName}</p>}
					</div>

					{error && (
						<p role="alert" className="text-sm text-error-red">
							{error}
						</p>
					)}

					{result && (
						<div className="rounded-md bg-pure-white dark:bg-[#111827] p-3 text-sm">
							<p className="font-medium text-navy dark:text-[#f8fafc]">
								Created {result.createdCount}, updated {result.updatedCount}, unchanged {result.unchangedCount}
								{result.errors.length > 0 ? `, ${result.errors.length} row error${result.errors.length !== 1 ? "s" : ""}` : ""}.
							</p>
							{result.errors.length > 0 && (
								<ul className="mt-2 flex flex-col gap-1 text-error-red">
									{result.errors.map((rowError) => (
										<li key={rowError.rowNumber}>
											Row {rowError.rowNumber}: {rowError.message}
										</li>
									))}
								</ul>
							)}
						</div>
					)}

					<Button type="submit" disabled={importCsv.isPending} className="self-start">
						{importCsv.isPending ? "Importing…" : "Import"}
					</Button>
				</form>
			)}
		</div>
	);
}
