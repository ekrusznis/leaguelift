import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import { generateIdempotencyKey } from '@/lib/idempotency';
import type { PageResponse } from '@/lib/types';

import type {
  AthleteMessagingTeamResponse,
  BroadcastMessageResponse,
  ConversationContactResponse,
  CreateAthleteConversationRequest,
  CreateConversationRequest,
  CreateMessageThreadRequest,
  MessageThreadResponse,
  MyBroadcastMessageResponse,
  MyMessageThreadResponse,
} from './types';

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

/** GET /me/messaging/athlete-teams — every team the caller is an active athlete on, each flagged with its own SafeSport gate state. */
export function useAthleteMessagingTeams(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'messaging', 'athlete-teams'],
    queryFn: ({ signal }) => apiFetch<AthleteMessagingTeamResponse[]>('/me/messaging/athlete-teams', { signal }),
    enabled,
  });
}

/**
 * GET /me/messaging/athlete-contacts — eligible peer contacts for one team. Only call
 * for a team whose athleteMessagingEnabled is true; the backend 409s
 * (ATHLETE_MESSAGING_REVIEW_REQUIRED) otherwise, and can still 403
 * (ATHLETE_MESSAGING_RESTRICTED) if a guardian has restricted this athlete on the team.
 */
export function useAthleteContacts(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['me', 'messaging', 'athlete-contacts', organizationId, teamId],
    queryFn: ({ signal }) =>
      apiFetch<ConversationContactResponse[]>(
        `/me/messaging/athlete-contacts?organizationId=${organizationId}&teamId=${teamId}`,
        { signal },
      ),
    enabled: !!organizationId && !!teamId,
    retry: false,
  });
}

/** GET /organizations/{organizationId}/message-threads — management list, any active member can view (ADR-105). */
export function useManagedThreads(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'message-threads'],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<MessageThreadResponse>>(`/organizations/${organizationId}/message-threads?size=50`, { signal }),
    enabled: !!organizationId,
  });
}

/** GET /organizations/{organizationId}/message-threads/{threadId}/messages — management view of a thread's messages. */
export function useManagedThreadMessages(organizationId: string | null, threadId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'message-threads', threadId, 'messages'],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<BroadcastMessageResponse>>(
        `/organizations/${organizationId}/message-threads/${threadId}/messages?size=100`,
        { signal },
      ),
    enabled: !!organizationId && !!threadId,
  });
}

export function useCreateBroadcastThread(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateMessageThreadRequest) =>
      apiFetch<MessageThreadResponse>(`/organizations/${organizationId}/message-threads`, { method: 'POST', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'message-threads'] });
    },
  });
}

/** POST /organizations/{organizationId}/message-threads/{threadId}/messages — the management-scoped send, distinct from useSendReply's /me-scoped endpoint. */
export function useSendBroadcastMessage(organizationId: string | null, threadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: string) =>
      apiFetch<BroadcastMessageResponse>(`/organizations/${organizationId}/message-threads/${threadId}/messages`, {
        method: 'POST',
        body: { idempotencyKey: generateIdempotencyKey(), body },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'message-threads'] });
    },
  });
}

/** GET /organizations/{organizationId}/message-threads/contacts?teamId= — staff-facing contacts list for "message specific people" (ADR-107), distinct from the athlete-self equivalent. */
export function useTeamContacts(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'message-threads', 'contacts', teamId],
    queryFn: ({ signal }) =>
      apiFetch<ConversationContactResponse[]>(
        `/organizations/${organizationId}/message-threads/contacts?teamId=${teamId}`,
        { signal },
      ),
    enabled: !!organizationId && !!teamId,
  });
}

export function useCreateConversation(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateConversationRequest) =>
      apiFetch<MessageThreadResponse>(`/organizations/${organizationId}/message-threads/conversations`, {
        method: 'POST',
        body: request,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'message-threads'] });
    },
  });
}

export function useCreateAthleteConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateAthleteConversationRequest) =>
      apiFetch<MessageThreadResponse>('/me/messaging/athlete-conversations', { method: 'POST', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'message-threads'] });
    },
  });
}
