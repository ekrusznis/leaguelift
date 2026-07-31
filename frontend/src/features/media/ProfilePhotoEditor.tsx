import { useRef, useState } from "react";
import { Button } from "../../components/Button";
import { Avatar } from "../../dashboard/components/Avatar";
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

type ProfileEntityType = Extract<MediaEntityType, "HOUSEHOLD_ADULT" | "PARTICIPANT">;

export function ProfilePhotoEditor({
	organizationId,
	entityType,
	entityId,
	name,
	canEdit,
}: {
	organizationId: string;
	entityType: ProfileEntityType;
	entityId: string;
	name: string;
	canEdit: boolean;
}) {
	const target = { entityType, entityId } as const;
	const assignments = useEntityMediaAssignments(organizationId, target);
	const requestUpload = useRequestMediaUpload(organizationId);
	const confirmUpload = useConfirmMediaUpload(organizationId);
	const assignMedia = useAssignEntityMedia(organizationId, target);
	const removeMedia = useRemoveEntityMedia(organizationId, target);
	const inputRef = useRef<HTMLInputElement>(null);
	const [state, setState] = useState<{ uploading: boolean; error?: string }>({ uploading: false });
	const assignment = assignments.data?.items.find((item) => item.usageSlot === "PROFILE_PHOTO");
	const inputId = `profile-photo-${entityType.toLowerCase()}-${entityId}`;

	async function handleFile(file: File | undefined) {
		if (!file) return;
		const validation = fileSchemaFor("PROFILE_PHOTO").safeParse(file);
		if (!validation.success) {
			setState({ uploading: false, error: validation.error.issues[0]?.message ?? "This photo cannot be used." });
			return;
		}
		setState({ uploading: true });
		try {
			const requested = await requestUpload.mutateAsync({
				usageSlot: "PROFILE_PHOTO",
				fileName: file.name,
				contentType: file.type,
				fileSizeBytes: file.size,
				entityType,
				entityId,
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await confirmUpload.mutateAsync(requested.assetId);
			if (confirmed.status === "REJECTED") {
				setState({ uploading: false, error: confirmed.rejectionReason ?? "This photo could not be used." });
				return;
			}
			await assignMedia.mutateAsync({
				usageSlot: "PROFILE_PHOTO",
				assetId: requested.assetId,
				altText: `Profile photo for ${name}`,
			});
			setState({ uploading: false });
		} catch {
			setState({ uploading: false, error: "Photo upload failed. Please try again." });
		} finally {
			if (inputRef.current) inputRef.current.value = "";
		}
	}

	return (
		<div className="flex shrink-0 items-center gap-2">
			<Avatar name={name} size="md" src={assignment?.url} />
			{canEdit && (
				<div className="flex flex-wrap items-center gap-2">
					<label htmlFor={inputId} className="inline-flex min-h-11 cursor-pointer items-center rounded-md border border-slate-gray/30 bg-pure-white px-3 py-2 text-sm font-medium text-navy hover:bg-ice-white">
						{assignment ? "Replace photo" : "Add photo"}
					</label>
					<input
						ref={inputRef}
						id={inputId}
						type="file"
						accept="image/png,image/jpeg,image/webp"
						disabled={state.uploading}
						onChange={(event) => handleFile(event.target.files?.[0])}
						className="sr-only"
					/>
					{assignment && (
						<Button type="button" variant="secondary" disabled={removeMedia.isPending} onClick={() => removeMedia.mutate("PROFILE_PHOTO")}>
							Remove
						</Button>
					)}
				</div>
			)}
			{state.uploading && <span className="text-xs text-slate-gray">Uploading…</span>}
			{state.error && <span role="alert" className="max-w-48 text-xs text-error-red">{state.error}</span>}
		</div>
	);
}
