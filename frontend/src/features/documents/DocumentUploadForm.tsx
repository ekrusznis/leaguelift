import { useRef, useState } from "react";
import { Button } from "../../components/Button";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";

const MAX_DOCUMENT_BYTES = 15 * 1024 * 1024;

/**
 * The upload half of document assignment (DESIGN-DOC.md section 13, Phase 7
 * completion) — requests a signed URL, PUTs the file, confirms, then hands the
 * ready assetId to the caller's `onAssign` (which POSTs it to the org, a household,
 * or broadcasts it to every household — the three call sites this form is shared
 * across). PDF only, matching the backend's DOCUMENT slot (UploadLimits.kt).
 */
export function DocumentUploadForm({
	organizationId,
	onAssign,
	submitLabel,
}: {
	organizationId: string;
	onAssign: (assetId: string, title: string | undefined) => Promise<unknown>;
	submitLabel: string;
}) {
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const [file, setFile] = useState<File | null>(null);
	const [title, setTitle] = useState("");
	const [status, setStatus] = useState<"idle" | "uploading" | "error">("idle");
	const [error, setError] = useState<string | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);

	async function handleSubmit(event: React.FormEvent) {
		event.preventDefault();
		if (!file) return;
		if (file.type !== "application/pdf") {
			setStatus("error");
			setError("Only PDF files are supported.");
			return;
		}
		if (file.size > MAX_DOCUMENT_BYTES) {
			setStatus("error");
			setError("File is too large (15MB max).");
			return;
		}

		setStatus("uploading");
		setError(null);
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot: "DOCUMENT",
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setStatus("error");
				setError(confirmed.rejectionReason ?? "This file could not be used.");
				return;
			}
			await onAssign(requested.assetId, title.trim() || undefined);
			setStatus("idle");
			setFile(null);
			setTitle("");
			if (fileInputRef.current) fileInputRef.current.value = "";
		} catch {
			setStatus("error");
			setError("Upload failed. Please try again.");
		}
	}

	return (
		<form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4" aria-label="Upload a document">
			<div className="flex flex-col gap-1">
				<label htmlFor="document-title" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					Title
				</label>
				<input
					id="document-title"
					type="text"
					value={title}
					onChange={(event) => setTitle(event.target.value)}
					placeholder="e.g. 2026 Season Waiver"
					className="min-h-11 rounded-md border border-slate-gray/30 px-3 py-2 text-sm"
				/>
			</div>
			<div className="flex flex-col gap-1">
				<label htmlFor="document-file" className="text-sm font-medium text-navy dark:text-[#f8fafc]">
					PDF file
				</label>
				<input
					ref={fileInputRef}
					id="document-file"
					type="file"
					accept="application/pdf"
					onChange={(event) => setFile(event.target.files?.[0] ?? null)}
					disabled={status === "uploading"}
					className="text-sm"
				/>
			</div>
			<Button type="submit" disabled={!file || status === "uploading"}>
				{status === "uploading" ? "Uploading…" : submitLabel}
			</Button>
			{error && (
				<p role="alert" className="w-full text-sm text-error-red">
					{error}
				</p>
			)}
		</form>
	);
}
