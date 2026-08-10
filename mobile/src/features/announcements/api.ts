import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import type { AnnouncementResponse, MyAnnouncementResponse, SaveAnnouncementRequest } from './types';

export function useMyAnnouncements() {
  return useQuery({
    queryKey: ['me', 'announcements'],
    queryFn: ({ signal }) => apiFetch<PageResponse<MyAnnouncementResponse>>('/me/announcements?size=50', { signal }),
  });
}

export function useMarkAnnouncementRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (announcementId: string) => apiFetch(`/me/announcements/${announcementId}/read`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'announcements'] });
    },
  });
}

/** GET /organizations/{organizationId}/announcements — management list, any active member can view (ADR-105). */
export function useManagedAnnouncements(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'announcements'],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<AnnouncementResponse>>(`/organizations/${organizationId}/announcements?size=50`, { signal }),
    enabled: !!organizationId,
  });
}

export function useCreateAnnouncementDraft(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: SaveAnnouncementRequest) =>
      apiFetch<AnnouncementResponse>(`/organizations/${organizationId}/announcements`, { method: 'POST', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'announcements'] });
    },
  });
}

export function usePublishAnnouncement(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (announcementId: string) =>
      apiFetch<AnnouncementResponse>(`/organizations/${organizationId}/announcements/${announcementId}/publish`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'announcements'] });
    },
  });
}
