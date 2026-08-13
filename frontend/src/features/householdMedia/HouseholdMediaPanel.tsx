import { useRef, useState } from "react";
import { Button } from "../../components/Button";
import { Modal } from "../../components/Modal";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useConfirmMediaUpload, useRequestMediaUpload } from "../media/api";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import { formatBytes } from "../documents/format";
import { useAssignHouseholdMedia, useHouseholdMedia, useReleaseHouseholdMediaPublicly, useRemoveHouseholdMedia } from "./api";

const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
const MAX_VIDEO_BYTES = 250 * 1024 * 1024;
const ALLOWED_TYPES = new Set(["image/png", "image/jpeg", "image/webp", "video/mp4", "video/quicktime"]);

/**
 * Household media center (Track 5, 2026-08-13) — a guardian's own photos/videos for
 * their household, with a bulk "release publicly" action enabling org use for social
 * sharing/highlight content. Explicitly no thumbnail/resize pipeline (founder decision,
 * 2026-08-13): photos render full-size via the same signed-URL pattern every other
 * media slot already uses; video gets a generic badge, not an extracted frame.
 */
export function HouseholdMediaPanel({ organizationId, householdId }: { organizationId: string; householdId: string }) {
	const { data, isLoading, isError, refetch } = useHouseholdMedia(organizationId, householdId);
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const assign = useAssignHouseholdMedia(organizationId, householdId);
	const removeMedia = useRemoveHouseholdMedia(organizationId, householdId);
	const releasePublicly = useReleaseHouseholdMediaPublicly(organizationId, householdId);

	const [uploadStatus, setUploadStatus] = useState<"idle" | "uploading" | "error">("idle");
	const [uploadError, setUploadError] = useState<string | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);
	const [selected, setSelected] = useState<Set<string>>(new Set());
	const [showReleaseModal, setShowReleaseModal] = useState(false);

	async function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
		const file = event.target.files?.[0];
		if (!file) return;
		if (!ALLOWED_TYPES.has(file.type)) {
			setUploadStatus("error");
			setUploadError("Only photos (PNG/JPEG/WebP) or videos (MP4/MOV) are supported.");
			return;
		}
		const isVideo = file.type.startsWith("video/");
		if (file.size > (isVideo ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES)) {
			setUploadStatus("error");
			setUploadError(isVideo ? "Video is too large (250MB max)." : "Photo is too large (15MB max).");
			return;
		}

		setUploadStatus("uploading");
		setUploadError(null);
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot: "HOUSEHOLD_MEDIA",
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
				entityType: "HOUSEHOLD",
				entityId: householdId,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setUploadStatus("error");
				setUploadError(confirmed.rejectionReason ?? "This file could not be used.");
				return;
			}
			await assign.mutateAsync(requested.assetId);
			setUploadStatus("idle");
			if (fileInputRef.current) fileInputRef.current.value = "";
		} catch {
			setUploadStatus("error");
			setUploadError("Upload failed. Please try again.");
		}
	}

	function toggleSelected(id: string) {
		setSelected((current) => {
			const next = new Set(current);
			if (next.has(id)) next.delete(id);
			else next.add(id);
			return next;
		});
	}

	const items = data?.items ?? [];
	const allSelected = items.length > 0 && items.every((item) => selected.has(item.id));

	async function confirmRelease() {
		await releasePublicly.mutateAsync(Array.from(selected));
		setSelected(new Set());
		setShowReleaseModal(false);
	}

	return (
		<div className="flex flex-col gap-3">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<label className="flex flex-col gap-1">
					<span className="text-sm font-medium text-navy dark:text-[#f8fafc]">Add a photo or video</span>
					<input
						ref={fileInputRef}
						type="file"
						accept="image/png,image/jpeg,image/webp,video/mp4,video/quicktime"
						onChange={(event) => void handleFileChange(event)}
						disabled={uploadStatus === "uploading"}
						className="text-sm"
					/>
				</label>
				{items.length > 0 && (
					<div className="flex items-center gap-2">
						<button
							type="button"
							className="text-sm font-medium text-info-blue hover:underline"
							onClick={() => setSelected(allSelected ? new Set() : new Set(items.map((item) => item.id)))}
						>
							{allSelected ? "Clear all" : "Select all"}
						</button>
						<Button type="button" variant="secondary" disabled={selected.size === 0} onClick={() => setShowReleaseModal(true)}>
							Release publicly ({selected.size})
						</Button>
					</div>
				)}
			</div>
			{uploadStatus === "uploading" && <p className="text-sm text-slate-gray dark:text-[#cbd5e1]">Uploading…</p>}
			{uploadError && <p role="alert" className="text-sm text-error-red">{uploadError}</p>}

			{isLoading && <LoadingState label="Loading media…" />}
			{isError && <ErrorState message="Could not load media." onRetry={() => refetch()} />}
			{data && items.length === 0 && <EmptyState title="No photos or videos yet" description="Photos and video clips this household uploads will appear here." />}

			{items.length > 0 && (
				<ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-label="Household media">
					{items.map((item) => (
						<li key={item.id} className="flex flex-col gap-2 rounded-lg border border-slate-gray/20 bg-pure-white dark:bg-[#111827] p-3">
							<div className="flex items-start justify-between gap-2">
								<label className="flex items-center gap-2">
									<input type="checkbox" checked={selected.has(item.id)} onChange={() => toggleSelected(item.id)} className="size-4" />
									<span className="text-xs text-slate-gray dark:text-[#cbd5e1]">{item.publicationStatus === "PUBLISHED" ? "Public" : "Household only"}</span>
								</label>
								<Button type="button" variant="secondary" onClick={() => removeMedia.mutate(item.id)} disabled={removeMedia.isPending} className="px-2 py-1 text-xs">
									Remove
								</Button>
							</div>
							{item.isVideo ? (
								<div className="flex h-32 items-center justify-center rounded-md bg-ice-white dark:bg-[#0f172a] text-sm text-slate-gray dark:text-[#cbd5e1]">
									🎥 Video
								</div>
							) : (
								<img src={item.url} alt="" className="h-32 w-full rounded-md object-cover" />
							)}
							<p className="text-xs text-slate-gray dark:text-[#cbd5e1]">{formatBytes(item.byteSizeBytes)}</p>
						</li>
					))}
				</ul>
			)}

			<Modal
				open={showReleaseModal}
				onClose={() => setShowReleaseModal(false)}
				title="Release this media publicly?"
				actions={
					<>
						<Button type="button" variant="secondary" onClick={() => setShowReleaseModal(false)}>Cancel</Button>
						<Button type="button" disabled={releasePublicly.isPending} onClick={() => void confirmRelease()}>
							{releasePublicly.isPending ? "Releasing…" : `Release ${selected.size} item${selected.size === 1 ? "" : "s"}`}
						</Button>
					</>
				}
			>
				<p>Releasing this media publicly means it becomes visible outside your household — the organization may use it for social sharing or highlight content.</p>
				<p className="mt-2">This does not undo automatically. You can remove an item entirely at any time, but a publicly-released item may already have been shared or saved elsewhere by then.</p>
			</Modal>
		</div>
	);
}
