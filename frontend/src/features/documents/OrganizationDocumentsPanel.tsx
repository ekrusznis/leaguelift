import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatBytes } from "./format";
import { DocumentUploadForm } from "./DocumentUploadForm";
import {
	organizationDocumentsQueryKey,
	useAssignOrganizationDocument,
	useBroadcastDocumentToAllHouseholds,
	useOrganizationDocuments,
	useRemoveDocument,
} from "./api";

/** Org-level document storage (DESIGN-DOC.md section 13, Phase 7 completion) — general uploads any active org member can see (handbooks, policies), plus a broadcast action that assigns the same file to every household (e.g. a season waiver). */
export function OrganizationDocumentsPanel({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useOrganizationDocuments(organizationId);
	const assignToOrganization = useAssignOrganizationDocument(organizationId);
	const broadcastToAllHouseholds = useBroadcastDocumentToAllHouseholds(organizationId);
	const removeDocument = useRemoveDocument(organizationId, organizationDocumentsQueryKey(organizationId));
	const [mode, setMode] = useState<"none" | "organization" | "broadcast">("none");

	return (
		<div className="flex flex-col gap-3">
			<div className="flex items-center gap-2">
				<Button type="button" variant="secondary" onClick={() => setMode(mode === "organization" ? "none" : "organization")}>
					{mode === "organization" ? "Cancel" : "Add document"}
				</Button>
				<Button type="button" variant="secondary" onClick={() => setMode(mode === "broadcast" ? "none" : "broadcast")}>
					{mode === "broadcast" ? "Cancel" : "Send to every household"}
				</Button>
			</div>

			{mode === "organization" && (
				<DocumentUploadForm
					organizationId={organizationId}
					submitLabel="Add document"
					onAssign={async (assetId, title) => {
						await assignToOrganization.mutateAsync({ assetId, title });
						setMode("none");
					}}
				/>
			)}
			{mode === "broadcast" && (
				<DocumentUploadForm
					organizationId={organizationId}
					submitLabel="Send to every household"
					onAssign={async (assetId, title) => {
						await broadcastToAllHouseholds.mutateAsync({ assetId, title });
						setMode("none");
					}}
				/>
			)}

			{isLoading && <LoadingState label="Loading documents…" />}
			{isError && <ErrorState message="Could not load documents." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && mode === "none" && (
				<EmptyState title="No documents yet" description="Upload a handbook, policy, or form for your organization." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Organization documents">
					{data.items.map((doc) => (
						<li key={doc.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="min-w-0">
								<a href={doc.url} target="_blank" rel="noreferrer" className="font-medium text-azure-blue hover:underline">
									{doc.title ?? "Document"}
								</a>
								<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{formatBytes(doc.byteSizeBytes)}</p>
							</div>
							<Button type="button" variant="secondary" onClick={() => removeDocument.mutate(doc.id)} disabled={removeDocument.isPending}>
								Remove
							</Button>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
