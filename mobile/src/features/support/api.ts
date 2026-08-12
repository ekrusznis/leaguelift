import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { PageResponse, SupportArticle, SupportCase, SupportCaseCategory } from './types';

function helpQuery(query?: string, category?: string) {
  const params = new URLSearchParams();
  if (query?.trim()) params.set('query', query.trim());
  if (category) params.set('category', category);
  const value = params.toString();
  return value ? `?${value}` : '';
}

/** Always the authenticated /help/articles path — mobile has no unauthenticated Help Center surface (that's the marketing site). */
export function useHelpArticles(query?: string, category?: string) {
  return useQuery({
    queryKey: ['help', 'articles', query ?? '', category ?? ''],
    queryFn: ({ signal }) => apiFetch<SupportArticle[]>(`/help/articles${helpQuery(query, category)}`, { signal }),
  });
}

export function useHelpArticle(slug: string | undefined) {
  return useQuery({
    queryKey: ['help', 'articles', 'article', slug],
    queryFn: ({ signal }) => apiFetch<SupportArticle>(`/help/articles/${slug}`, { signal }),
    enabled: !!slug,
  });
}

export interface CreateSupportCaseInput {
  idempotencyKey: string;
  organizationId?: string | null;
  category: SupportCaseCategory;
  subject: string;
  description: string;
}

export function useCreateSupportCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateSupportCaseInput) => apiFetch<SupportCase>('/support-cases', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['support-cases', 'mine'] }),
  });
}

export function useMySupportCases(enabled: boolean) {
  return useQuery({
    queryKey: ['support-cases', 'mine'],
    queryFn: ({ signal }) => apiFetch<PageResponse<SupportCase>>('/support-cases?page=0&size=50', { signal }),
    enabled,
  });
}
