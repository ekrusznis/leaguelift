import { useRef, useState } from "react";
import { Button } from "../../components/Button";
import { useArticleAttachments, useRemoveArticleAttachment, useUploadArticleAttachment, type ArticleAttachment } from "./articleAttachments";
import { formatBytes } from "../documents/format";

function isVideo(contentType: string | null) {
	return contentType?.startsWith("video/") ?? false;
}

function isImage(contentType: string | null) {
	return contentType?.startsWith("image/") ?? false;
}

/** `attachment:<id>` is a durable placeholder the backend resolves into a fresh signed URL on every reader-facing read (see SupportArticleService.resolveAttachments) — never insert the (ephemeral, 15-minute) `url` this component receives directly into the body. */
function embedFor(attachment: ArticleAttachment): string {
	const label = attachment.contentType ?? "attachment";
	if (isImage(attachment.contentType) || isVideo(attachment.contentType)) return `![${label}](attachment:${attachment.id})`;
	return `[Download attachment](attachment:${attachment.id})`;
}

/**
 * Help Center article attachments (Track 4, 2026-08-13) — lets a platform admin
 * upload an image/GIF/video/PDF and insert a markdown embed/link referencing it.
 * Only usable once the article has a real id, so this is hidden for a brand-new,
 * not-yet-saved article (mirrors the household media panel's upload flow).
 */
export function ArticleAttachmentPicker({ articleId, onInsert }: { articleId: string; onInsert: (markdown: string) => void }) {
	const { data, isLoading } = useArticleAttachments(articleId);
	const upload = useUploadArticleAttachment(articleId);
	const remove = useRemoveArticleAttachment(articleId);
	const [error, setError] = useState<string | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);

	async function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
		const file = event.target.files?.[0];
		if (!file) return;
		setError(null);
		try {
			await upload.mutateAsync(file);
			if (fileInputRef.current) fileInputRef.current.value = "";
		} catch (err) {
			setError(err instanceof Error ? err.message : "Upload failed. Please try again.");
		}
	}

	return (
		<div className="rounded-lg border border-slate-200 dark:border-[#334155] p-3">
			<div className="flex items-center justify-between gap-2">
				<span className="text-sm font-semibold text-navy-900 dark:text-[#f8fafc]">Attachments</span>
				<input
					ref={fileInputRef}
					type="file"
					accept="image/png,image/jpeg,image/webp,image/gif,video/mp4,video/quicktime,application/pdf"
					onChange={(event) => void handleFileChange(event)}
					disabled={upload.isPending}
					className="text-xs"
				/>
			</div>
			{upload.isPending && <p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]">Uploading…</p>}
			{error && <p role="alert" className="mt-2 text-xs text-error-700">{error}</p>}
			{isLoading && <p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]">Loading attachments…</p>}
			{data && data.items.length === 0 && <p className="mt-2 text-xs text-slate-500 dark:text-[#cbd5e1]">No attachments yet.</p>}
			{data && data.items.length > 0 && (
				<ul className="mt-2 flex flex-wrap gap-2">
					{data.items.map((attachment) => (
						<li key={attachment.id} className="flex w-40 flex-col gap-1 rounded-md border border-slate-200 dark:border-[#334155] p-2">
							{isImage(attachment.contentType) ? (
								<img src={attachment.url} alt="" className="h-20 w-full rounded object-cover" />
							) : (
								<div className="flex h-20 w-full items-center justify-center rounded bg-slate-100 dark:bg-slate-800 text-xs text-slate-500">
									{isVideo(attachment.contentType) ? "🎥 Video" : "📄 File"}
								</div>
							)}
							<span className="truncate text-[11px] text-slate-500 dark:text-[#cbd5e1]">{formatBytes(attachment.byteSizeBytes)}</span>
							<div className="flex gap-1">
								<Button type="button" onClick={() => onInsert(embedFor(attachment))} className="flex-1 px-2 py-1 text-[11px]">
									Insert
								</Button>
								<Button
									type="button"
									variant="secondary"
									onClick={() => remove.mutate(attachment.id)}
									disabled={remove.isPending}
									className="px-2 py-1 text-[11px]"
								>
									Remove
								</Button>
							</div>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
