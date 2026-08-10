import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { DocumentAcknowledgmentListResponse, DocumentListResponse } from './types';

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
