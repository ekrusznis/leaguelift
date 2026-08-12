import { useRef, useState } from "react";
import { Button } from "../../components/Button";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { TeamLogo } from "../../dashboard/components/TeamLogo";
import {
	useAssignEntityMedia,
	useConfirmMediaUpload,
	useEntityMediaAssignments,
	useRemoveEntityMedia,
	useRequestMediaUpload,
} from "./api";
import { fileSchemaFor } from "./schema";
import type { MediaEntityType } from "./types";
import { uploadToSignedUrl } from "./uploadToSignedUrl";

type BrandingEntityType = Extract<MediaEntityType, "TEAM" | "TOURNAMENT">;
type BrandingSlot = "LOGO" | "COVER";
type SlotState = { status: "idle" | "uploading" | "error"; error?: string };

const SLOT_LABELS: Record<BrandingSlot, string> = { LOGO: "Logo", COVER: "Cover image" };
const SLOT_ACCEPT: Record<BrandingSlot, string> = {
	LOGO: "image/png,image/jpeg,image/webp,image/svg+xml",
	COVER: "image/png,image/jpeg,image/webp",
};

export function EntityBrandingPanel({
	organizationId,
	entityType,
	entityId,
	entityName,
}: {
	organizationId: string;
	entityType: BrandingEntityType;
	entityId: string;
	entityName: string;
}) {
	const target = { entityType, entityId } as const;
	const assignments = useEntityMediaAssignments(organizationId, target);
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const assignMedia = useAssignEntityMedia(organizationId, target);
	const removeMedia = useRemoveEntityMedia(organizationId, target);
	const [slotStates, setSlotStates] = useState<Record<BrandingSlot, SlotState>>({
		LOGO: { status: "idle" },
		COVER: { status: "idle" },
	});
	const logoInputRef = useRef<HTMLInputElement>(null);
	const coverInputRef = useRef<HTMLInputElement>(null);
	const inputRefs: Record<BrandingSlot, React.RefObject<HTMLInputElement | null>> = {
		LOGO: logoInputRef,
		COVER: coverInputRef,
	};

	if (assignments.isLoading) return <LoadingState label={`Loading ${entityName} branding…`} />;
	if (assignments.isError) return <ErrorState message="Could not load branding." onRetry={() => assignments.refetch()} />;

	const assignmentsBySlot = new Map((assignments.data?.items ?? []).map((item) => [item.usageSlot, item]));

	async function handleFileSelected(usageSlot: BrandingSlot, file: File | undefined) {
		if (!file) return;
		const validation = fileSchemaFor(usageSlot).safeParse(file);
		if (!validation.success) {
			setSlotStates((previous) => ({
				...previous,
				[usageSlot]: { status: "error", error: validation.error.issues[0]?.message ?? "This file cannot be used." },
			}));
			return;
		}

		setSlotStates((previous) => ({ ...previous, [usageSlot]: { status: "uploading" } }));
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot,
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
				entityType,
				entityId,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setSlotStates((previous) => ({
					...previous,
					[usageSlot]: { status: "error", error: confirmed.rejectionReason ?? "This file could not be used." },
				}));
				return;
			}
			await assignMedia.mutateAsync({
				usageSlot,
				assetId: requested.assetId,
				altText: usageSlot === "LOGO" ? `${entityName} logo` : `${entityName} cover image`,
			});
			setSlotStates((previous) => ({ ...previous, [usageSlot]: { status: "idle" } }));
		} catch {
			setSlotStates((previous) => ({
				...previous,
				[usageSlot]: { status: "error", error: "Upload failed. Please try again." },
			}));
		} finally {
			const input = inputRefs[usageSlot].current;
			if (input) input.value = "";
		}
	}

	return (
		<div className="flex flex-col gap-3 rounded-lg border border-slate-gray/20 bg-ice-white dark:bg-[#0f172a] p-4">
			<p className="text-sm text-slate-gray dark:text-[#cbd5e1]">
				Images are private until the matching public page is published. Replacing an image retires the prior assignment.
			</p>
			{(["LOGO", "COVER"] as const).map((usageSlot) => {
				const assignment = assignmentsBySlot.get(usageSlot);
				const state = slotStates[usageSlot];
				const inputId = `${entityType.toLowerCase()}-${entityId}-${usageSlot.toLowerCase()}`;
				return (
					<div key={usageSlot} className="flex flex-wrap items-center gap-4 rounded-lg bg-pure-white dark:bg-[#111827] p-3">
						{usageSlot === "LOGO" && assignment?.url ? (
							<TeamLogo src={assignment.url} alt={assignment.altText ?? `${entityName} logo`} size="lg" />
						) : usageSlot === "LOGO" ? (
							<span className="flex size-14 shrink-0 items-center justify-center rounded-lg bg-navy-900 text-xs font-bold text-white">
								{entityName.slice(0, 2).toUpperCase()}
							</span>
						) : assignment?.url ? (
							<img src={assignment.url} alt={assignment.altText ?? `${entityName} cover`} className="h-16 w-28 rounded-md object-cover" />
						) : (
							<span className="flex h-16 w-28 shrink-0 items-center justify-center rounded-md bg-navy-900 text-xs text-slate-300">
								No cover
							</span>
						)}
						<div className="min-w-0 flex-1">
							<label htmlFor={inputId} className="block text-sm font-medium text-navy dark:text-[#f8fafc]">
								{SLOT_LABELS[usageSlot]}
							</label>
							<input
								ref={inputRefs[usageSlot]}
								id={inputId}
								type="file"
								accept={SLOT_ACCEPT[usageSlot]}
								disabled={state.status === "uploading"}
								onChange={(event) => handleFileSelected(usageSlot, event.target.files?.[0])}
								className="mt-1 max-w-full text-sm"
							/>
							{state.status === "uploading" && <p className="mt-1 text-sm text-slate-gray dark:text-[#cbd5e1]">Uploading…</p>}
							{state.status === "error" && <p role="alert" className="mt-1 text-sm text-error-red">{state.error}</p>}
						</div>
						{assignment && (
							<Button
								type="button"
								variant="secondary"
								disabled={removeMedia.isPending}
								onClick={() => removeMedia.mutate(usageSlot)}
							>
								Remove
							</Button>
						)}
					</div>
				);
			})}
		</div>
	);
}
