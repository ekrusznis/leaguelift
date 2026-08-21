import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type {
  NotificationPreferences,
  NotificationTopic,
  UpdateNotificationTopicRequest,
  UpdateSmsConsentRequest,
  UpdateUserPreferencesRequest,
  UserPreferences,
} from './types';

export const userPreferencesQueryKey = ['me', 'preferences'] as const;
export const notificationPreferencesQueryKey = ['me', 'notification-preferences'] as const;

export function useUserPreferences(enabled = true) {
  return useQuery({
    queryKey: userPreferencesQueryKey,
    queryFn: ({ signal }) => apiFetch<UserPreferences>('/me/preferences', { signal }),
    enabled,
  });
}

export function useUpdateUserPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateUserPreferencesRequest) =>
      apiFetch<UserPreferences>('/me/preferences', { method: 'PATCH', body: request }),
    onSuccess: (preferences) => {
      queryClient.setQueryData(userPreferencesQueryKey, preferences);
    },
  });
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: notificationPreferencesQueryKey,
    queryFn: ({ signal }) => apiFetch<NotificationPreferences>('/me/notification-preferences', { signal }),
  });
}

export function useUpdateNotificationTopic() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ topic, request }: { topic: NotificationTopic; request: UpdateNotificationTopicRequest }) =>
      apiFetch<NotificationPreferences>(`/me/notification-preferences/${topic}`, { method: 'PATCH', body: request }),
    onSuccess: (preferences) => {
      queryClient.setQueryData(notificationPreferencesQueryKey, preferences);
    },
  });
}

export function useUpdateSmsConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateSmsConsentRequest) =>
      apiFetch<NotificationPreferences>('/me/sms-consent', { method: 'PATCH', body: request }),
    onSuccess: (preferences) => {
      queryClient.setQueryData(notificationPreferencesQueryKey, preferences);
    },
  });
}

export interface AccountDeletionRequest {
  id: string;
  status: string;
  requestedAt: string;
  scheduledFor: string;
}

const accountDeletionQueryKey = ['me', 'deletion-request'] as const;

export function usePendingAccountDeletion() {
  return useQuery({
    queryKey: accountDeletionQueryKey,
    queryFn: () => apiFetch<AccountDeletionRequest | null>('/me/deletion-request'),
  });
}

export function useRequestAccountDeletion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<AccountDeletionRequest>('/me/deletion-request', { method: 'POST' }),
    onSuccess: (request) => queryClient.setQueryData(accountDeletionQueryKey, request),
  });
}

export function useCancelAccountDeletion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch('/me/deletion-request', { method: 'DELETE' }),
    onSuccess: () => queryClient.setQueryData(accountDeletionQueryKey, null),
  });
}
