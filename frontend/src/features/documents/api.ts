import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { DocumentAcknowledgmentListResponse, DocumentItem, DocumentListResponse } from "./types";

const organizationDocumentsQueryKey = (organizationId: string) => ["organizations", organizationId, "documents"] as const;
const householdDocumentsQueryKey = (organizationId: string, householdId: string) =>
	["organizations", organizationId, "households", householdId, "documents"] as const;

export function useOrganizationDocuments(organizationId: string) {
	return useQuery({
		queryKey: organizationDocumentsQueryKey(organizationId),
		queryFn: () => apiFetch<DocumentListResponse>(`/organizations/${organizationId}/documents`),
		enabled: !!organizationId,
	});
}

export function useAssignOrganizationDocument(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (params: { assetId: string; title?: string }) =>
			apiFetch<DocumentItem>(`/organizations/${organizationId}/documents`, { method: "POST", body: params }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationDocumentsQueryKey(organizationId) }),
	});
}

export function useBroadcastDocumentToAllHouseholds(organizationId: string) {
	return useMutation({
		mutationFn: (params: { assetId: string; title?: string }) =>
			apiFetch<DocumentListResponse>(`/organizations/${organizationId}/documents/broadcast`, { method: "POST", body: params }),
	});
}

export function useRemoveDocument(organizationId: string, invalidateKey: readonly unknown[]) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentId: string) => apiFetch(`/organizations/${organizationId}/documents/${assignmentId}`, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: invalidateKey }),
	});
}

export function useDocumentAcknowledgments(organizationId: string, assignmentId: string | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "documents", assignmentId, "acknowledgments"],
		queryFn: () => apiFetch<DocumentAcknowledgmentListResponse>(`/organizations/${organizationId}/documents/${assignmentId}/acknowledgments`),
		enabled: !!organizationId && !!assignmentId,
	});
}

export function useHouseholdDocuments(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: householdDocumentsQueryKey(organizationId, householdId),
		queryFn: () => apiFetch<DocumentListResponse>(`/organizations/${organizationId}/households/${householdId}/documents`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useAssignHouseholdDocument(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (params: { assetId: string; title?: string }) =>
			apiFetch<DocumentItem>(`/organizations/${organizationId}/households/${householdId}/documents`, { method: "POST", body: params }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: householdDocumentsQueryKey(organizationId, householdId) }),
	});
}

export function useAcknowledgeDocument(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentId: string) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/documents/${assignmentId}/acknowledge`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: householdDocumentsQueryKey(organizationId, householdId) }),
	});
}

export { organizationDocumentsQueryKey, householdDocumentsQueryKey };
