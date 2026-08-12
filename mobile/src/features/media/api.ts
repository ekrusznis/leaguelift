import { useMutation } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { ConfirmUploadResponse, MediaEntityType, MediaUsageSlot, RequestUploadResponse } from './types';

/** Mirrors frontend/src/features/media/api.ts's two upload mutations exactly — only the entity-scoped request/confirm/assign trio mobile actually needs (Phase 37.9); the org-level LOGO/COVER-only assignment endpoints stay web-only for now. */
export function useRequestMediaUpload(organizationId: string) {
  return useMutation({
    mutationFn: (params: {
      usageSlot: MediaUsageSlot;
      fileName: string;
      contentType: string;
      fileSizeBytes: number;
      entityType?: MediaEntityType;
      entityId?: string;
    }) =>
      apiFetch<RequestUploadResponse>(`/organizations/${organizationId}/media/uploads`, {
        method: 'POST',
        body: params,
      }),
  });
}

export function useConfirmMediaUpload(organizationId: string) {
  return useMutation({
    mutationFn: (assetId: string) =>
      apiFetch<ConfirmUploadResponse>(`/organizations/${organizationId}/media/uploads/${assetId}/confirm`, {
        method: 'POST',
      }),
  });
}
