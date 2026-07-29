import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import { organizationQueryKey } from "../organizations/api";
import type {
	ConfirmUploadResponse,
	MediaAssignment,
	MediaAssignmentListResponse,
	MediaUsageSlot,
	RequestUploadResponse,
} from "./types";

const mediaAssignmentsQueryKey = (organizationId: string) => ["organizations", organizationId, "mediaAssignments"] as const;

export function useMediaAssignments(organizationId: string) {
	return useQuery({
		queryKey: mediaAssignmentsQueryKey(organizationId),
		queryFn: () => apiFetch<MediaAssignmentListResponse>(`/organizations/${organizationId}/media/assignments`),
		enabled: !!organizationId,
	});
}

export function useRequestMediaUpload(organizationId: string) {
	return useMutation({
		mutationFn: (params: { usageSlot: MediaUsageSlot; fileName: string; contentType: string; fileSizeBytes: number }) =>
			apiFetch<RequestUploadResponse>(`/organizations/${organizationId}/media/uploads`, {
				method: "POST",
				body: params,
			}),
	});
}

export function useConfirmMediaUpload(organizationId: string) {
	return useMutation({
		mutationFn: (assetId: string) =>
			apiFetch<ConfirmUploadResponse>(`/organizations/${organizationId}/media/uploads/${assetId}/confirm`, {
				method: "POST",
			}),
	});
}

export function useAssignMedia(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (params: { usageSlot: MediaUsageSlot; assetId: string; altText?: string }) =>
			apiFetch<MediaAssignment>(`/organizations/${organizationId}/media/assignments/${params.usageSlot}`, {
				method: "PUT",
				body: { assetId: params.assetId, altText: params.altText ?? null },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: mediaAssignmentsQueryKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: organizationQueryKey(organizationId) });
		},
	});
}

export function useRemoveMediaAssignment(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (usageSlot: MediaUsageSlot) =>
			apiFetch(`/organizations/${organizationId}/media/assignments/${usageSlot}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: mediaAssignmentsQueryKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: organizationQueryKey(organizationId) });
		},
	});
}
