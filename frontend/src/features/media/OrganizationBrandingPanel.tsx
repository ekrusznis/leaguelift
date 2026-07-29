import { useRef, useState } from "react";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { OrganizationLogo } from "../../dashboard/components/OrganizationLogo";
import { useAssignMedia, useConfirmMediaUpload, useMediaAssignments, useRequestMediaUpload } from "./api";
import { fileSchemaFor } from "./schema";
import type { MediaUsageSlot } from "./types";
import { uploadToSignedUrl } from "./uploadToSignedUrl";

const SLOT_LABELS: Record<MediaUsageSlot, string> = { LOGO: "Logo", COVER: "Cover image" };
const SLOT_ACCEPT: Record<MediaUsageSlot, string> = {
	LOGO: "image/png,image/jpeg,image/webp,image/svg+xml",
	COVER: "image/png,image/jpeg,image/webp",
};

type SlotState = { status: "idle" | "uploading" | "error"; error?: string };

/**
 * Orchestrates the signed-URL upload flow (request -> uploadToSignedUrl -> confirm ->
 * assign) for an organization's LOGO and COVER slots (DESIGN-DOC.md section 11.3,
 * Phase 1). Team/tournament branding reuse the same backend endpoints as a fast-follow.
 */
export function OrganizationBrandingPanel({
	organizationId,
	organizationName,
}: {
	organizationId: string;
	organizationName: string;
}) {
	const { data, isLoading, isError, refetch } = useMediaAssignments(organizationId);
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const assignMedia = useAssignMedia(organizationId);
	const [slotStates, setSlotStates] = useState<Record<MediaUsageSlot, SlotState>>({
		LOGO: { status: "idle" },
		COVER: { status: "idle" },
	});
	const logoInputRef = useRef<HTMLInputElement>(null);
	const coverInputRef = useRef<HTMLInputElement>(null);
	const inputRefs: Record<MediaUsageSlot, React.RefObject<HTMLInputElement | null>> = {
		LOGO: logoInputRef,
		COVER: coverInputRef,
	};

	if (isLoading) {
		return <LoadingState label="Loading branding…" />;
	}
	if (isError) {
		return <ErrorState message="Could not load branding." onRetry={() => refetch()} />;
	}

	const assignmentsBySlot = new Map((data?.items ?? []).map((item) => [item.usageSlot, item]));

	async function handleFileSelected(usageSlot: MediaUsageSlot, file: File | undefined) {
		if (!file) return;
		const validation = fileSchemaFor(usageSlot).safeParse(file);
		if (!validation.success) {
			setSlotStates((prev) => ({
				...prev,
				[usageSlot]: { status: "error", error: validation.error.issues[0]?.message ?? "This file cannot be used." },
			}));
			return;
		}

		setSlotStates((prev) => ({ ...prev, [usageSlot]: { status: "uploading" } }));
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot,
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setSlotStates((prev) => ({
					...prev,
					[usageSlot]: { status: "error", error: confirmed.rejectionReason ?? "This file could not be used." },
				}));
				return;
			}
			await assignMedia.mutateAsync({ usageSlot, assetId: requested.assetId });
			setSlotStates((prev) => ({ ...prev, [usageSlot]: { status: "idle" } }));
		} catch {
			setSlotStates((prev) => ({ ...prev, [usageSlot]: { status: "error", error: "Upload failed. Please try again." } }));
		} finally {
			const input = inputRefs[usageSlot].current;
			if (input) input.value = "";
		}
	}

	return (
		<div className="flex flex-col gap-4">
			{(["LOGO", "COVER"] as const).map((usageSlot) => {
				const assignment = assignmentsBySlot.get(usageSlot);
				const slotState = slotStates[usageSlot];
				return (
					<div
						key={usageSlot}
						className="flex items-center gap-4 rounded-lg border border-slate-gray/20 bg-pure-white p-4"
					>
						{usageSlot === "LOGO" ? (
							<OrganizationLogo name={organizationName} src={assignment?.url} />
						) : assignment?.url ? (
							<img
								src={assignment.url}
								alt={`${organizationName} cover`}
								className="h-14 w-24 rounded-md object-cover"
							/>
						) : (
							<span className="flex h-14 w-24 shrink-0 items-center justify-center rounded-md bg-navy-900 text-xs text-slate-gray">
								No cover
							</span>
						)}
						<div className="flex flex-1 flex-col gap-1">
							<label htmlFor={`media-upload-${usageSlot}`} className="text-sm font-medium text-navy">
								{SLOT_LABELS[usageSlot]}
							</label>
							<input
								ref={inputRefs[usageSlot]}
								id={`media-upload-${usageSlot}`}
								type="file"
								accept={SLOT_ACCEPT[usageSlot]}
								onChange={(event) => handleFileSelected(usageSlot, event.target.files?.[0])}
								disabled={slotState.status === "uploading"}
								className="text-sm"
							/>
							{slotState.status === "uploading" && <p className="text-sm text-slate-gray">Uploading…</p>}
							{slotState.status === "error" && (
								<p role="alert" className="text-sm text-error-red">
									{slotState.error}
								</p>
							)}
						</div>
					</div>
				);
			})}
		</div>
	);
}
