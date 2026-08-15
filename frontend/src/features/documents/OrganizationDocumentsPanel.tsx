import { useState } from "react";
import { Button } from "../../components/Button";
import { ListToolbar } from "../../components/lists/ListToolbar";
import { Pagination } from "../../components/lists/Pagination";
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

type DocumentSort = "UPDATED_DESC" | "UPDATED_ASC" | "TITLE_ASC" | "TITLE_DESC";

/** Org-level document storage (DESIGN-DOC.md section 13, Phase 7 completion). */
export function OrganizationDocumentsPanel({ organizationId }: { organizationId: string }) {
	const { data, isLoading, isError, refetch } = useOrganizationDocuments(organizationId);
	const assignToOrganization = useAssignOrganizationDocument(organizationId);
	const broadcastToAllHouseholds = useBroadcastDocumentToAllHouseholds(organizationId);
	const removeDocument = useRemoveDocument(organizationId, organizationDocumentsQueryKey(organizationId));
	const [mode, setMode] = useState<"none" | "organization" | "broadcast">("none");
	const [query, setQuery] = useState("");
	const [sort, setSort] = useState<DocumentSort>("UPDATED_DESC");
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(25);

	const needle = query.trim().toLowerCase();
	const filtered = (data?.items ?? [])
		.filter((doc) => {
			if (!needle) return true;
			return [doc.title, doc.contentType]
				.filter(Boolean)
				.some((value) => value!.toLowerCase().includes(needle));
		})
		.sort((left, right) => {
			if (sort === "TITLE_ASC" || sort === "TITLE_DESC") {
				const result = (left.title ?? "Document").localeCompare(right.title ?? "Document");
				return sort === "TITLE_ASC" ? result : -result;
			}
			const result = left.updatedAt.localeCompare(right.updatedAt);
			return sort === "UPDATED_ASC" ? result : -result;
		});

	const totalElements = filtered.length;
	const safePage = Math.min(page, Math.max(0, Math.ceil(totalElements / size) - 1));
	const visibleDocuments = filtered.slice(safePage * size, safePage * size + size);

	return (
		<div className="flex flex-col gap-3">
			<ListToolbar
				searchValue={query}
				onSearchChange={(value) => {
					setQuery(value);
					setPage(0);
				}}
				searchPlaceholder="Search organization documents"
				resultCount={totalElements}
				sortValue={sort}
				sortOptions={[
					{ value: "UPDATED_DESC", label: "Recently updated" },
					{ value: "UPDATED_ASC", label: "Oldest updated" },
					{ value: "TITLE_ASC", label: "Title A–Z" },
					{ value: "TITLE_DESC", label: "Title Z–A" },
				]}
				onSortChange={(value) => {
					setSort(value as DocumentSort);
					setPage(0);
				}}
				onClear={() => {
					setQuery("");
					setSort("UPDATED_DESC");
					setPage(0);
				}}
				actions={
					<>
						<Button type="button" variant="secondary" onClick={() => setMode(mode === "organization" ? "none" : "organization")}>
							{mode === "organization" ? "Cancel" : "Add document"}
						</Button>
						<Button type="button" variant="secondary" onClick={() => setMode(mode === "broadcast" ? "none" : "broadcast")}>
							{mode === "broadcast" ? "Cancel" : "Send to every household"}
						</Button>
					</>
				}
			/>

			{mode === "organization" && (
				<DocumentUploadForm
					organizationId={organizationId}
					submitLabel="Add document"
					onAssign={async (assetId, title) => {
						await assignToOrganization.mutateAsync({ assetId, title });
						setMode("none");
						setPage(0);
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
			{data && visibleDocuments.length === 0 && mode === "none" && (
				<EmptyState
					title={query.trim() ? "No results found" : "No documents yet"}
					description={query.trim() ? "Try changing your search." : "Upload a handbook, policy, or form for your organization."}
				/>
			)}
			{visibleDocuments.length > 0 && (
				<ul className="flex flex-col gap-2" aria-label="Organization documents">
					{visibleDocuments.map((doc) => (
						<li key={doc.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="min-w-0">
								<a href={doc.url} target="_blank" rel="noreferrer" className="font-medium text-azure-blue hover:underline">
									{doc.title ?? "Document"}
								</a>
								<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
									{[doc.contentType, formatBytes(doc.byteSizeBytes)].filter(Boolean).join(" · ")}
								</p>
							</div>
							<Button type="button" variant="secondary" onClick={() => removeDocument.mutate(doc.id)} disabled={removeDocument.isPending}>
								Remove
							</Button>
						</li>
					))}
				</ul>
			)}

			<Pagination
				page={safePage}
				size={size}
				totalElements={totalElements}
				onPageChange={setPage}
				onSizeChange={(nextSize) => {
					setSize(nextSize);
					setPage(0);
				}}
			/>
		</div>
	);
}
