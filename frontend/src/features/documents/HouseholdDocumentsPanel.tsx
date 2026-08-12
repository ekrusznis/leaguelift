import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { formatBytes } from "./format";
import { DocumentUploadForm } from "./DocumentUploadForm";
import { ReminderButton } from "../communications/ReminderButton";
import { householdDocumentsQueryKey, useAcknowledgeDocument, useAssignHouseholdDocument, useHouseholdDocuments, useRemoveDocument } from "./api";

/**
 * One household's documents (DESIGN-DOC.md section 13, Phase 7 completion). Used two
 * ways: `canManage` (org admin, on the household detail page) shows upload/remove;
 * `canAcknowledge` (guardian, on the Parent dashboard) shows the "Acknowledge" action.
 * A user can be neither, either, or both depending on where this renders.
 */
export function HouseholdDocumentsPanel({
	organizationId,
	householdId,
	canManage = false,
	canAcknowledge = false,
}: {
	organizationId: string;
	householdId: string;
	canManage?: boolean;
	canAcknowledge?: boolean;
}) {
	const { data, isLoading, isError, refetch } = useHouseholdDocuments(organizationId, householdId);
	const assignToHousehold = useAssignHouseholdDocument(organizationId, householdId);
	const removeDocument = useRemoveDocument(organizationId, householdDocumentsQueryKey(organizationId, householdId));
	const acknowledge = useAcknowledgeDocument(organizationId, householdId);
	const [showForm, setShowForm] = useState(false);
	const [acknowledgedIds, setAcknowledgedIds] = useState<Set<string>>(new Set());

	return (
		<div className="flex flex-col gap-3">
			{canManage && (
				<div className="flex items-center justify-between">
					<Button type="button" variant="secondary" onClick={() => setShowForm((v) => !v)}>
						{showForm ? "Cancel" : "Add document"}
					</Button>
				</div>
			)}
			{canManage && showForm && (
				<DocumentUploadForm
					organizationId={organizationId}
					submitLabel="Add document"
					onAssign={async (assetId, title) => {
						await assignToHousehold.mutateAsync({ assetId, title });
						setShowForm(false);
					}}
				/>
			)}

			{isLoading && <LoadingState label="Loading documents…" />}
			{isError && <ErrorState message="Could not load documents." onRetry={() => refetch()} />}
			{data && data.items.length === 0 && !showForm && (
				<EmptyState title="No documents" description="Waivers, forms, and other files for this household will appear here." />
			)}
			{data && data.items.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Household documents">
					{data.items.map((doc) => {
						const justAcknowledged = acknowledgedIds.has(doc.id);
						return (
							<li key={doc.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
								<div className="min-w-0">
									<a href={doc.url} target="_blank" rel="noreferrer" className="font-medium text-azure-blue hover:underline">
										{doc.title ?? "Document"}
									</a>
									<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">{formatBytes(doc.byteSizeBytes)}</p>
								</div>
								<div className="flex items-center gap-2">
									{canAcknowledge && (
										<Button
											type="button"
											variant={justAcknowledged ? "secondary" : "primary"}
											disabled={acknowledge.isPending || justAcknowledged}
											onClick={() => {
												acknowledge.mutate(doc.id, {
													onSuccess: () => setAcknowledgedIds((prev) => new Set(prev).add(doc.id)),
												});
											}}
										>
											{justAcknowledged ? "Acknowledged" : "Acknowledge"}
										</Button>
									)}
									{canManage && (
										<ReminderButton organizationId={organizationId} resourceType="DOCUMENT" resourceId={doc.id} label="Remind household" />
									)}
									{canManage && (
										<Button type="button" variant="secondary" onClick={() => removeDocument.mutate(doc.id)} disabled={removeDocument.isPending}>
											Remove
										</Button>
									)}
								</div>
							</li>
						);
					})}
				</ul>
			)}
		</div>
	);
}
