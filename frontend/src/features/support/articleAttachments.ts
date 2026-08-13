import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import { uploadToSignedUrl } from "../media/uploadToSignedUrl";
import type { RequestUploadResponse } from "../media/types";

export interface ArticleAttachment {
	id: string;
	assetId: string;
	url: string;
	contentType: string | null;
	byteSizeBytes: number | null;
	widthPx: number | null;
	heightPx: number | null;
	createdAt: string;
}

interface ArticleAttachmentListResponse {
	items: ArticleAttachment[];
}

interface ConfirmArticleAttachmentUploadResponse {
	assetId: string;
	status: "READY" | "REJECTED";
	rejectionReason: string | null;
}

const articleAttachmentsQueryKey = (articleId: string) => ["platform", "help", articleId, "attachments"] as const;

export function useArticleAttachments(articleId: string | undefined) {
	return useQuery({
		queryKey: articleAttachmentsQueryKey(articleId ?? ""),
		queryFn: () => apiFetch<ArticleAttachmentListResponse>(`/platform/admin/help/articles/${articleId}/attachments`),
		enabled: !!articleId,
	});
}

/** Uploads a file and adds it to the article's attachment gallery in one call, mirroring the household media panel's request/PUT/confirm/assign flow. */
export function useUploadArticleAttachment(articleId: string | undefined) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (file: File) => {
			if (!articleId) throw new Error("Save the article before adding attachments.");
			const requested = await apiFetch<RequestUploadResponse>(`/platform/admin/help/articles/${articleId}/attachments/uploads`, {
				method: "POST",
				body: { fileName: file.name, contentType: file.type, fileSizeBytes: file.size },
			});
			await uploadToSignedUrl(requested.uploadUrl, file, requested.requiredHeaders);
			const confirmed = await apiFetch<ConfirmArticleAttachmentUploadResponse>(
				`/platform/admin/help/articles/${articleId}/attachments/uploads/${requested.assetId}/confirm`,
				{ method: "POST" },
			);
			if (confirmed.status === "REJECTED") {
				throw new Error(confirmed.rejectionReason ?? "This file could not be used.");
			}
			return apiFetch<ArticleAttachment>(`/platform/admin/help/articles/${articleId}/attachments`, {
				method: "POST",
				body: { assetId: requested.assetId },
			});
		},
		onSuccess: () => {
			if (articleId) queryClient.invalidateQueries({ queryKey: articleAttachmentsQueryKey(articleId) });
		},
	});
}

export function useRemoveArticleAttachment(articleId: string | undefined) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentId: string) =>
			apiFetch(`/platform/admin/help/articles/${articleId}/attachments/${assignmentId}`, { method: "DELETE" }),
		onSuccess: () => {
			if (articleId) queryClient.invalidateQueries({ queryKey: articleAttachmentsQueryKey(articleId) });
		},
	});
}
