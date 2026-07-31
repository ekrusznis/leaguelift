import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import { organizationQueryKey } from "../organizations/api";
import type {
	ConfirmUploadResponse,
	MediaAssignment,
	MediaAssignmentListResponse,
	MediaTarget,
	MediaUsageSlot,
	RequestUploadResponse,
} from "./types";

const mediaAssignmentsQueryKey = (organizationId: string) => ["organizations", organizationId, "mediaAssignments"] as const;
export const entityMediaAssignmentsQueryKey = (organizationId: string, target: MediaTarget) =>
	["organizations", organizationId, "mediaAssignments", target.entityType, target.entityId] as const;

export function useMediaAssignments(organizationId: string) {
	return useQuery({
		queryKey: mediaAssignmentsQueryKey(organizationId),
		queryFn: () => apiFetch<MediaAssignmentListResponse>(`/organizations/${organizationId}/media/assignments`),
		enabled: !!organizationId,
	});
}

export function useEntityMediaAssignments(organizationId: string, target: MediaTarget) {
	return useQuery({
		queryKey: entityMediaAssignmentsQueryKey(organizationId, target),
		queryFn: () =>
			apiFetch<MediaAssignmentListResponse>(
				`/organizations/${organizationId}/media/assignments/entities/${target.entityType}/${target.entityId}`,
			),
		enabled: !!organizationId && !!target.entityId,
	});
}

export function useRequestMediaUpload(organizationId: string) {
	return useMutation({
		mutationFn: (params: {
			usageSlot: MediaUsageSlot;
			fileName: string;
			contentType: string;
			fileSizeBytes: number;
			entityType?: MediaTarget["entityType"];
			entityId?: string;
		}) =>
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

export function useAssignEntityMedia(organizationId: string, target: MediaTarget) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (params: { usageSlot: MediaUsageSlot; assetId: string; altText?: string }) =>
			apiFetch<MediaAssignment>(
				`/organizations/${organizationId}/media/assignments/entities/${target.entityType}/${target.entityId}/${params.usageSlot}`,
				{ method: "PUT", body: { assetId: params.assetId, altText: params.altText ?? null } },
			),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: entityMediaAssignmentsQueryKey(organizationId, target) });
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

export function useRemoveEntityMedia(organizationId: string, target: MediaTarget) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (usageSlot: MediaUsageSlot) =>
			apiFetch(
				`/organizations/${organizationId}/media/assignments/entities/${target.entityType}/${target.entityId}/${usageSlot}`,
				{ method: "DELETE" },
			),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: entityMediaAssignmentsQueryKey(organizationId, target) });
		},
	});
}
