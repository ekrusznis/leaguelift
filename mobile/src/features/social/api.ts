import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type {
  AuthorizationStart,
  SocialCatalogItem,
  SocialDraftSourceType,
  SocialPostDraft,
  SocialProvider,
  SocialPublishingHistory,
} from './types';

const catalogKey = ['me', 'integrations', 'catalog'] as const;

/** GET /me/integrations/catalog, filtered to the 3 social providers — same generic personal-integrations endpoint every other provider (Google Calendar, etc.) uses. */
export function useSocialCatalog(enabled: boolean) {
  return useQuery({
    queryKey: catalogKey,
    queryFn: async ({ signal }) => {
      const items = await apiFetch<SocialCatalogItem[]>('/me/integrations/catalog', { signal });
      return items.filter((item) => item.provider === 'INSTAGRAM' || item.provider === 'FACEBOOK' || item.provider === 'X');
    },
    enabled,
  });
}

export function useStartSocialAuthorization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (provider: SocialProvider) =>
      apiFetch<AuthorizationStart>(`/me/integrations/${provider.toLowerCase()}/oauth/start`, { method: 'POST' }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: catalogKey });
    },
  });
}

export function useDisconnectSocialConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (connectionId: string) => apiFetch(`/me/integration-connections/${connectionId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: catalogKey });
    },
  });
}

export function useRefreshSocialConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (connectionId: string) => apiFetch(`/me/integration-connections/${connectionId}/health-check`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: catalogKey });
    },
  });
}

/** POST /social/drafts — only SocialDraftSourceType.FUNDRAISER generates real content today; other source types reject with a clear "not available yet" error surfaced via the mutation's `error`. */
export function useCreateSocialDraft() {
  return useMutation({
    mutationFn: (params: { organizationId: string; sourceType: SocialDraftSourceType; sourceId: string }) =>
      apiFetch<SocialPostDraft>('/social/drafts', { method: 'POST', body: params }),
  });
}

export function useUpdateSocialDraftCaption() {
  return useMutation({
    mutationFn: (params: { draftId: string; caption: string }) =>
      apiFetch<SocialPostDraft>(`/social/drafts/${params.draftId}`, { method: 'PATCH', body: { caption: params.caption } }),
  });
}

export function usePublishSocialDraft() {
  return useMutation({
    mutationFn: (params: { draftId: string; provider: SocialProvider }) =>
      apiFetch<SocialPublishingHistory>(`/social/drafts/${params.draftId}/publish`, {
        method: 'POST',
        body: { provider: params.provider },
      }),
  });
}

export function useSocialPublishingHistory(enabled: boolean) {
  return useQuery({
    queryKey: ['social', 'publishing-history'] as const,
    queryFn: ({ signal }) => apiFetch<SocialPublishingHistory[]>('/social/publishing-history', { signal }),
    enabled,
  });
}
