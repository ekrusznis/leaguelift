import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import type { MyAnnouncementResponse } from './types';

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
