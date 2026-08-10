import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import { generateIdempotencyKey } from '@/lib/idempotency';
import type { PageResponse } from '@/lib/types';

import type { BroadcastMessageResponse, MyBroadcastMessageResponse, MyMessageThreadResponse } from './types';

/** GET /me/message-threads — caller's inbox across every team/org thread they can see (ADR-102). */
export function useMyMessageThreads() {
  return useQuery({
    queryKey: ['me', 'message-threads'],
    queryFn: ({ signal }) => apiFetch<PageResponse<MyMessageThreadResponse>>('/me/message-threads?size=50', { signal }),
  });
}

export function useThreadMessages(threadId: string | null) {
  return useQuery({
    queryKey: ['me', 'message-threads', threadId, 'messages'],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<MyBroadcastMessageResponse>>(`/me/message-threads/${threadId}/messages?size=100`, { signal }),
    enabled: !!threadId,
  });
}

export function useSendReply(threadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: string) =>
      apiFetch<BroadcastMessageResponse>(`/me/message-threads/${threadId}/messages`, {
        method: 'POST',
        body: { idempotencyKey: generateIdempotencyKey(), body },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'message-threads', threadId, 'messages'] });
      queryClient.invalidateQueries({ queryKey: ['me', 'message-threads'] });
    },
  });
}

export function useMarkMessageRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) => apiFetch(`/me/messages/${messageId}/read`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'message-threads'] });
    },
  });
}
