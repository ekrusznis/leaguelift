import { useRef, useState, type Dispatch, type SetStateAction } from "react";
import { Button } from "../../components/Button";
import { LoadingState } from "../../components/states/LoadingState";
import { useOrganizationDocuments } from "../documents/api";
import {
	useBulkDocumentAssignments,
	useBulkFeeAssignments,
	useBulkStaffInvitations,
	useBulkTeamAssignments,
	useExecuteOnboardingImport,
	useOnboardingFeeTemplates,
	useOnboardingHouseholds,
	useOnboardingTeams,
	useOrganizationParticipants,
	usePreviewOnboardingImport,
} from "./api";
import type { BulkActionResult, OnboardingImportPreview, OnboardingImportResult } from "./types";

const CSV_TEMPLATE = `record_type,external_id,household_external_id,team_external_id,display_name,name,sport,season,first_name,last_name,email,phone,relationship,is_primary,date_of_birth,contact_email,contact_phone,notes
TEAM,team-u14-blue,,,,U14 Blue,Volleyball,2026-2027,,,,,,,,coach@example.com,,
HOUSEHOLD,household-smith,,,Smith Family,,,,,,,,,,,parent@example.com,555-555-0101,
GUARDIAN,guardian-jamie-smith,household-smith,,,,,,Jamie,Smith,jamie@example.com,555-555-0101,Parent,true,,,,
PARTICIPANT,participant-riley-smith,household-smith,team-u14-blue,,,,,Riley,Smith,,,,,2012-09-15,,,`;

const STAFF_ROLES = [
	"ADMINISTRATOR",
	"TEAM_ADMINISTRATOR",
	"TOURNAMENT_ADMINISTRATOR",
	"VIEWER",
] as const;

function downloadText(fileName: string, content: string, type = "text/csv;charset=utf-8") {
	const url = URL.createObjectURL(new Blob([content], { type }));
	const anchor = document.createElement("a");
	anchor.href = url;
	anchor.download = fileName;
	anchor.click();
	URL.revokeObjectURL(url);
}

function csvEscape(value: string) {
	const spreadsheetSafeValue = /^[=+\-@]/.test(value) ? `'${value}` : value;
	return /[",\r\n]/.test(spreadsheetSafeValue)
		? `"${spreadsheetSafeValue.replaceAll('"', '""')}"`
		: spreadsheetSafeValue;
}

function BulkResultSummary({ result }: { result: BulkActionResult | null }) {
	if (!result) return null;
	return (
		<div className="rounded-lg border border-slate-gray/20 bg-ice-white p-3 text-sm">
			<p className="font-medium text-navy">
				{result.succeededCount} succeeded, {result.skippedCount} skipped, {result.failedCount} failed.
			</p>
			{result.items.some((item) => item.status !== "SUCCEEDED") && (
				<ul className="mt-2 flex max-h-40 flex-col gap-1 overflow-auto text-slate-gray">
					{result.items.filter((item) => item.status !== "SUCCEEDED").map((item, index) => (
						<li key={`${item.reference}-${index}`}>
							<span className={item.status === "FAILED" ? "text-error-red" : "text-slate-gray"}>
								{item.reference}: {item.message ?? item.status.toLowerCase()}
							</span>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}

function SelectionList({
	label,
	items,
	selected,
	setSelected,
}: {
	label: string;
	items: Array<{ id: string; label: string; detail?: string }>;
	selected: Set<string>;
	setSelected: Dispatch<SetStateAction<Set<string>>>;
}) {
	const allSelected = items.length > 0 && items.every((item) => selected.has(item.id));
	return (
		<fieldset className="rounded-lg border border-slate-gray/20 p-3">
			<legend className="font-medium text-navy">{label}</legend>
			<div className="flex justify-end">
				<button
					type="button"
					className="text-sm font-medium text-info-blue hover:underline"
					onClick={() => setSelected(allSelected ? new Set() : new Set(items.map((item) => item.id)))}
				>
					{allSelected ? "Clear all" : "Select all"}
				</button>
			</div>
			<div className="mt-2 grid max-h-52 gap-2 overflow-auto sm:grid-cols-2">
				{items.length === 0 && <p className="text-sm text-slate-gray">No records available.</p>}
				{items.map((item) => (
					<label key={item.id} className="flex min-w-0 items-start gap-2 rounded-md border border-slate-gray/15 p-2">
						<input
							type="checkbox"
							checked={selected.has(item.id)}
							onChange={() => {
								setSelected((current) => {
									const next = new Set(current);
									if (next.has(item.id)) next.delete(item.id);
									else next.add(item.id);
									return next;
								});
							}}
							className="mt-1 size-4 shrink-0"
						/>
						<span className="min-w-0">
							<span className="block break-words text-sm font-medium text-navy">{item.label}</span>
							{item.detail && <span className="block break-words text-xs text-slate-gray">{item.detail}</span>}
						</span>
					</label>
				))}
			</div>
		</fieldset>
	);
}

function CsvOnboardingImport({ organizationId }: { organizationId: string }) {
	const fileRef = useRef<HTMLInputElement>(null);
	const previewImport = usePreviewOnboardingImport(organizationId);
	const executeImport = useExecuteOnboardingImport(organizationId);
	const [fileName, setFileName] = useState<string | null>(null);
	const [csvContent, setCsvContent] = useState<string | null>(null);
	const [preview, setPreview] = useState<OnboardingImportPreview | null>(null);
	const [result, setResult] = useState<OnboardingImportResult | null>(null);
	const [error, setError] = useState<string | null>(null);

	function selectFile(event: React.ChangeEvent<HTMLInputElement>) {
		const file = event.target.files?.[0];
		if (!file) return;
		setPreview(null);
		setResult(null);
		setCsvContent(null);
		if (file.size > 2 * 1024 * 1024) {
			setFileName(file.name);
			setError("The CSV file must be 2 MB or smaller.");
			return;
		}
		setFileName(file.name);
		setError(null);
		const reader = new FileReader();
		reader.onload = () => {
			if (typeof reader.result === "string") setCsvContent(reader.result);
			else setError("Could not read the selected CSV.");
		};
		reader.onerror = () => setError("Could not read the selected CSV.");
		reader.readAsText(file);
	}

	async function previewSelectedFile() {
		if (!csvContent) {
			setError("Choose a CSV file first.");
			return;
		}
		setError(null);
		setResult(null);
		try {
			setPreview(await previewImport.mutateAsync({ fileName, csvContent }));
		} catch {
			setPreview(null);
			setError("Could not preview this file. Check the required columns and file size.");
		}
	}

	async function confirmImport() {
		if (!csvContent || !preview) return;
		setError(null);
		try {
			const imported = await executeImport.mutateAsync({
				fileName,
				csvContent,
				expectedContentHash: preview.contentHash,
			});
			setResult(imported);
			setPreview(null);
		} catch {
			setError("The import could not be completed. Preview the current file again and review its errors.");
		}
	}

	function downloadErrors() {
		const source = result?.errors ?? preview?.rows
			.filter((row) => row.errors.length > 0)
			.map((row) => ({
				rowNumber: row.rowNumber,
				recordType: row.recordType,
				externalId: row.externalId,
				message: row.errors.join(" "),
			})) ?? [];
		const lines = [
			"row_number,record_type,external_id,message",
			...source.map((item) =>
				[item.rowNumber.toString(), item.recordType ?? "", item.externalId ?? "", item.message]
					.map(csvEscape)
					.join(","),
			),
		];
		downloadText("rally26-onboarding-errors.csv", lines.join("\r\n"));
	}

	return (
		<section className="flex flex-col gap-4 rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div>
					<h3 className="font-heading text-base font-semibold text-navy">CSV onboarding import</h3>
					<p className="mt-1 max-w-3xl text-sm text-slate-gray">
						Preview teams, household shells, guardian contacts, participants, and participant-team assignments before writing anything.
						Stable external IDs make repeat imports update or match instead of duplicate.
					</p>
				</div>
				<Button type="button" variant="secondary" onClick={() => downloadText("rally26-onboarding-template.csv", CSV_TEMPLATE)}>
					Download template
				</Button>
			</div>

			<div className="flex flex-wrap items-end gap-3">
				<div className="flex min-w-64 flex-1 flex-col gap-1">
					<label htmlFor="onboarding-csv-file" className="text-sm font-medium text-navy">CSV file</label>
					<input ref={fileRef} id="onboarding-csv-file" type="file" accept=".csv,text/csv" onChange={selectFile} className="text-sm text-navy" />
					{fileName && <p className="text-xs text-slate-gray">Selected: {fileName}</p>}
				</div>
				<Button type="button" onClick={previewSelectedFile} disabled={previewImport.isPending || !csvContent}>
					{previewImport.isPending ? "Previewing…" : "Preview import"}
				</Button>
			</div>

			<p className="text-xs text-slate-gray">
				Required columns: <span className="font-mono">record_type</span> and <span className="font-mono">external_id</span>.
				Guardian and participant rows reference <span className="font-mono">household_external_id</span>; participant rows may also reference{" "}
				<span className="font-mono">team_external_id</span>. The source file itself is not retained.
			</p>

			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}

			{preview && (
				<div className="flex flex-col gap-3 rounded-lg border border-info-blue/30 bg-info-blue/5 p-4">
					<div className="grid gap-2 text-sm sm:grid-cols-3 lg:grid-cols-6">
						<div><span className="block text-slate-gray">Rows</span><strong className="text-navy">{preview.totalRows}</strong></div>
						<div><span className="block text-slate-gray">Valid</span><strong className="text-navy">{preview.validRows}</strong></div>
						<div><span className="block text-slate-gray">Errors</span><strong className={preview.errorRows ? "text-error-red" : "text-navy"}>{preview.errorRows}</strong></div>
						<div><span className="block text-slate-gray">Create</span><strong className="text-navy">{preview.createCount}</strong></div>
						<div><span className="block text-slate-gray">Update</span><strong className="text-navy">{preview.updateCount}</strong></div>
						<div><span className="block text-slate-gray">Match</span><strong className="text-navy">{preview.matchCount}</strong></div>
					</div>
					<div className="max-h-80 overflow-auto rounded-md border border-slate-gray/20 bg-pure-white">
						<table className="w-full min-w-[720px] text-left text-sm">
							<thead className="sticky top-0 bg-ice-white text-slate-gray">
								<tr><th className="p-2">Row</th><th className="p-2">Type</th><th className="p-2">External ID</th><th className="p-2">Record</th><th className="p-2">Action</th><th className="p-2">Notes</th></tr>
							</thead>
							<tbody>
								{preview.rows.map((row) => (
									<tr key={row.rowNumber} className="border-t border-slate-gray/10 align-top">
										<td className="p-2">{row.rowNumber}</td>
										<td className="p-2">{row.recordType ?? "—"}</td>
										<td className="p-2 font-mono text-xs">{row.externalId ?? "—"}</td>
										<td className="p-2">{row.label}</td>
										<td className="p-2">{row.action.replace("_", " ")}</td>
										<td className="p-2">
											{row.errors.length > 0 && <span className="text-error-red">{row.errors.join(" ")}</span>}
											{row.errors.length === 0 && row.warnings.length > 0 && <span className="text-slate-gray">{row.warnings.join(" ")}</span>}
											{row.errors.length === 0 && row.warnings.length === 0 && "Ready"}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
					<div className="flex flex-wrap gap-2">
						<Button type="button" onClick={confirmImport} disabled={executeImport.isPending || preview.validRows === 0}>
							{executeImport.isPending ? "Importing…" : `Import ${preview.validRows} valid row${preview.validRows === 1 ? "" : "s"}`}
						</Button>
						{preview.errorRows > 0 && <Button type="button" variant="secondary" onClick={downloadErrors}>Download error report</Button>}
					</div>
				</div>
			)}

			{result && (
				<div className="rounded-lg border border-victory-green/30 bg-victory-green/5 p-4 text-sm">
					<p className="font-medium text-navy">
						Import complete: {result.createdCount} created, {result.updatedCount} updated, {result.matchedCount} matched,{" "}
						{result.teamAssignmentsCreated} team assignment{result.teamAssignmentsCreated === 1 ? "" : "s"} created.
					</p>
					{result.errors.length > 0 && (
						<div className="mt-2">
							<p className="text-error-red">{result.errors.length} row{result.errors.length === 1 ? "" : "s"} could not be imported.</p>
							<Button type="button" variant="secondary" className="mt-2" onClick={downloadErrors}>Download error report</Button>
						</div>
					)}
				</div>
			)}
		</section>
	);
}

function BulkStaffInvitations({ organizationId }: { organizationId: string }) {
	const mutation = useBulkStaffInvitations(organizationId);
	const [emails, setEmails] = useState("");
	const [role, setRole] = useState<(typeof STAFF_ROLES)[number]>("VIEWER");
	const [result, setResult] = useState<BulkActionResult | null>(null);
	const [error, setError] = useState<string | null>(null);

	async function submit(event: React.FormEvent) {
		event.preventDefault();
		const parsed = emails.split(/[\n,;]+/).map((email) => email.trim()).filter(Boolean);
		if (parsed.length === 0) {
			setError("Enter at least one staff email.");
			return;
		}
		if (parsed.length > 500) {
			setError("Send no more than 500 staff invitations at once.");
			return;
		}
		setError(null);
		try {
			setResult(await mutation.mutateAsync({ items: parsed.map((email) => ({ email, role })) }));
		} catch {
			setError("Could not create the bulk invitations.");
		}
	}

	return (
		<section className="flex flex-col gap-4 rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<div>
				<h3 className="font-heading text-base font-semibold text-navy">Bulk staff invitations</h3>
				<p className="mt-1 text-sm text-slate-gray">
					Send one organization role to several adults. This reuses the existing single-use invitation and email-outbox flow; it never creates passwords or active accounts.
				</p>
			</div>
			<form onSubmit={submit} className="grid gap-3 lg:grid-cols-[1fr_240px_auto] lg:items-end">
				<div className="flex flex-col gap-1">
					<label htmlFor="bulk-invite-emails" className="text-sm font-medium text-navy">Email addresses</label>
					<textarea
						id="bulk-invite-emails"
						value={emails}
						onChange={(event) => setEmails(event.target.value)}
						rows={4}
						placeholder={"coach1@example.com\ncoach2@example.com"}
						className="rounded-md border border-slate-gray/30 px-3 py-2"
					/>
				</div>
				<div className="flex flex-col gap-1">
					<label htmlFor="bulk-invite-role" className="text-sm font-medium text-navy">Organization role</label>
					<select id="bulk-invite-role" value={role} onChange={(event) => setRole(event.target.value as typeof role)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						{STAFF_ROLES.map((item) => <option key={item} value={item}>{item.replaceAll("_", " ")}</option>)}
					</select>
				</div>
				<Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? "Sending…" : "Send invitations"}</Button>
			</form>
			<p className="text-xs text-slate-gray">
				Guardian contacts imported from CSV remain household shells. Guardian account activation requires the dedicated guardian-invitation workflow and is not converted into a broad organization membership here.
			</p>
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
			<BulkResultSummary result={result} />
		</section>
	);
}

function BulkAssignments({ organizationId }: { organizationId: string }) {
	const teams = useOnboardingTeams(organizationId);
	const households = useOnboardingHouseholds(organizationId);
	const participants = useOrganizationParticipants(organizationId);
	const feeTemplates = useOnboardingFeeTemplates(organizationId);
	const documents = useOrganizationDocuments(organizationId);
	const teamMutation = useBulkTeamAssignments(organizationId);
	const feeMutation = useBulkFeeAssignments(organizationId);
	const documentMutation = useBulkDocumentAssignments(organizationId);

	const [teamId, setTeamId] = useState("");
	const [participantIds, setParticipantIds] = useState<Set<string>>(new Set());
	const [feeTemplateId, setFeeTemplateId] = useState("");
	const [feeHouseholdIds, setFeeHouseholdIds] = useState<Set<string>>(new Set());
	const [dueDate, setDueDate] = useState("");
	const [assetId, setAssetId] = useState("");
	const [documentHouseholdIds, setDocumentHouseholdIds] = useState<Set<string>>(new Set());
	const [documentTitle, setDocumentTitle] = useState("");
	const [teamResult, setTeamResult] = useState<BulkActionResult | null>(null);
	const [feeResult, setFeeResult] = useState<BulkActionResult | null>(null);
	const [documentResult, setDocumentResult] = useState<BulkActionResult | null>(null);
	const [error, setError] = useState<string | null>(null);

	const householdItems = (households.data?.items ?? []).filter((household) => household.status === "ACTIVE").map((household) => ({
		id: household.id,
		label: household.displayName,
		detail: household.contactEmail ?? undefined,
	}));
	const participantItems = (participants.data?.items ?? []).filter((participant) => participant.status === "ACTIVE").map((participant) => ({
		id: participant.id,
		label: `${participant.firstName} ${participant.lastName}`,
		detail: householdItems.find((household) => household.id === participant.householdId)?.label,
	}));

	const selectionQueries = [teams, households, participants, feeTemplates, documents];
	const selectionsLoading = selectionQueries.some((query) => query.isLoading);
	const selectionsFailed = selectionQueries.some((query) => query.isError);

	async function assignTeam() {
		if (!teamId || participantIds.size === 0) {
			setError("Choose a team and at least one participant.");
			return;
		}
		setError(null);
		try {
			setTeamResult(await teamMutation.mutateAsync({ teamId, participantIds: [...participantIds] }));
		} catch {
			setError("Could not complete the team assignments.");
		}
	}

	async function assignFee() {
		if (!feeTemplateId || feeHouseholdIds.size === 0) {
			setError("Choose a fee template and at least one household.");
			return;
		}
		setError(null);
		try {
			setFeeResult(await feeMutation.mutateAsync({
				feeTemplateId,
				householdIds: [...feeHouseholdIds],
				dueDate: dueDate || null,
			}));
		} catch {
			setError("Could not complete the fee assignments.");
		}
	}

	async function assignDocument() {
		if (!assetId || documentHouseholdIds.size === 0) {
			setError("Choose an organization document and at least one household.");
			return;
		}
		setError(null);
		try {
			setDocumentResult(await documentMutation.mutateAsync({
				assetId,
				householdIds: [...documentHouseholdIds],
				title: documentTitle.trim() || null,
			}));
		} catch {
			setError("Could not complete the document assignments.");
		}
	}

	return (
		<section className="flex flex-col gap-5 rounded-xl border border-slate-gray/20 bg-pure-white p-5">
			<div>
				<h3 className="font-heading text-base font-semibold text-navy">Bulk assignments</h3>
				<p className="mt-1 text-sm text-slate-gray">Apply existing teams, fee templates, and uploaded organization documents to selected records. Repeating an identical assignment is skipped.</p>
			</div>
			{error && <p role="alert" className="text-sm text-error-red">{error}</p>}
			{selectionsLoading && <LoadingState label="Loading onboarding records…" />}
			{selectionsFailed && (
				<p role="alert" className="rounded-lg border border-error-red/30 bg-error-red/5 p-3 text-sm text-error-red">
					Some teams, households, participants, fee templates, or documents could not be loaded. Refresh before making a bulk assignment.
				</p>
			)}

			<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 p-4">
				<h4 className="font-medium text-navy">Assign participants to a team</h4>
				<label htmlFor="bulk-team-id" className="text-sm font-medium text-navy">Team</label>
				<select id="bulk-team-id" value={teamId} onChange={(event) => setTeamId(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
					<option value="">Choose a team</option>
					{teams.data?.items.filter((team) => team.status === "ACTIVE").map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
				</select>
				<SelectionList label="Participants" items={participantItems} selected={participantIds} setSelected={setParticipantIds} />
				<Button type="button" className="self-start" onClick={assignTeam} disabled={teamMutation.isPending || selectionsLoading || selectionsFailed}>{teamMutation.isPending ? "Assigning…" : "Assign team"}</Button>
				<BulkResultSummary result={teamResult} />
			</div>

			<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 p-4">
				<h4 className="font-medium text-navy">Assign a fee template to households</h4>
				<div className="grid gap-3 sm:grid-cols-2">
					<div className="flex flex-col gap-1">
						<label htmlFor="bulk-fee-template-id" className="text-sm font-medium text-navy">Fee template</label>
						<select id="bulk-fee-template-id" value={feeTemplateId} onChange={(event) => setFeeTemplateId(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="">Choose a fee template</option>
							{feeTemplates.data?.items.filter((template) => template.status === "ACTIVE").map((template) => <option key={template.id} value={template.id}>{template.name}</option>)}
						</select>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="bulk-fee-due-date" className="text-sm font-medium text-navy">Due date (optional)</label>
						<input id="bulk-fee-due-date" type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</div>
				</div>
				<SelectionList label="Households" items={householdItems} selected={feeHouseholdIds} setSelected={setFeeHouseholdIds} />
				<Button type="button" className="self-start" onClick={assignFee} disabled={feeMutation.isPending || selectionsLoading || selectionsFailed}>{feeMutation.isPending ? "Assigning…" : "Assign fee"}</Button>
				<BulkResultSummary result={feeResult} />
			</div>

			<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 p-4">
				<h4 className="font-medium text-navy">Assign an uploaded document to households</h4>
				<div className="grid gap-3 sm:grid-cols-2">
					<div className="flex flex-col gap-1">
						<label htmlFor="bulk-document-asset-id" className="text-sm font-medium text-navy">Organization document</label>
						<select id="bulk-document-asset-id" value={assetId} onChange={(event) => setAssetId(event.target.value)} className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2">
						<option value="">Choose an organization document</option>
							{documents.data?.items.map((document) => <option key={document.id} value={document.assetId}>{document.title ?? document.assetId}</option>)}
						</select>
					</div>
					<div className="flex flex-col gap-1">
						<label htmlFor="bulk-document-title" className="text-sm font-medium text-navy">Household-facing title (optional)</label>
						<input id="bulk-document-title" value={documentTitle} onChange={(event) => setDocumentTitle(event.target.value)} placeholder="Household-facing title (optional)" className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2" />
					</div>
				</div>
				<SelectionList label="Households" items={householdItems} selected={documentHouseholdIds} setSelected={setDocumentHouseholdIds} />
				<Button type="button" className="self-start" onClick={assignDocument} disabled={documentMutation.isPending || selectionsLoading || selectionsFailed}>{documentMutation.isPending ? "Assigning…" : "Assign document"}</Button>
				<BulkResultSummary result={documentResult} />
			</div>
		</section>
	);
}

export function OnboardingPanel({ organizationId }: { organizationId: string }) {
	return (
		<div className="flex flex-col gap-6">
			<CsvOnboardingImport organizationId={organizationId} />
			<BulkStaffInvitations organizationId={organizationId} />
			<BulkAssignments organizationId={organizationId} />
		</div>
	);
}
