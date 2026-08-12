import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { DocumentAcknowledgmentListResponse, DocumentListResponse, DocumentResponse } from './types';

export function useHouseholdDocuments(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'documents'],
    queryFn: ({ signal }) =>
      apiFetch<DocumentListResponse>(`/organizations/${organizationId}/households/${householdId}/documents`, { signal }),
    enabled: !!organizationId && !!householdId,
  });
}

/** Any household adult's acknowledgment appears here — check acknowledgedByUserId against the current user to know "did I acknowledge this." */
export function useDocumentAcknowledgments(organizationId: string | null, assignmentId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'documents', assignmentId, 'acknowledgments'],
    queryFn: ({ signal }) =>
      apiFetch<DocumentAcknowledgmentListResponse>(`/organizations/${organizationId}/documents/${assignmentId}/acknowledgments`, {
        signal,
      }),
    enabled: !!organizationId && !!assignmentId,
  });
}

export function useAcknowledgeDocument(organizationId: string | null, householdId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (assignmentId: string) =>
      apiFetch(`/organizations/${organizationId}/households/${householdId}/documents/${assignmentId}/acknowledge`, {
        method: 'POST',
      }),
    onSuccess: (_data, assignmentId) => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'documents', assignmentId, 'acknowledgments'] });
    },
  });
}

// --- Owner/organization-level document management (Phase 37.12, ADR-119) ---

const organizationDocumentsKey = (organizationId: string | null) => ['organizations', organizationId, 'documents'] as const;

export function useOrganizationDocuments(organizationId: string | null) {
  return useQuery({
    queryKey: organizationDocumentsKey(organizationId),
    queryFn: ({ signal }) => apiFetch<DocumentListResponse>(`/organizations/${organizationId}/documents`, { signal }),
    enabled: !!organizationId,
  });
}

export function useAssignOrganizationDocument(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (params: { assetId: string; title?: string }) =>
      apiFetch<DocumentResponse>(`/organizations/${organizationId}/documents`, { method: 'POST', body: params }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationDocumentsKey(organizationId) }),
  });
}

/** Assigns the same asset to every household in the organization in one call. */
export function useBroadcastDocumentToAllHouseholds(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (params: { assetId: string; title?: string }) =>
      apiFetch<DocumentResponse>(`/organizations/${organizationId}/documents/broadcast`, { method: 'POST', body: params }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationDocumentsKey(organizationId) }),
  });
}

export function useRemoveDocument(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (assignmentId: string) =>
      apiFetch(`/organizations/${organizationId}/documents/${assignmentId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationDocumentsKey(organizationId) }),
  });
}
