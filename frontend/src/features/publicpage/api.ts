import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreatePublicPageFormValues } from "./schema";
import type { PublicPage, PublicPagePage } from "./types";

const pagesQueryKey = (organizationId: string) => ["organizations", organizationId, "pages"] as const;

export function usePages(organizationId: string) {
	return useQuery({
		queryKey: pagesQueryKey(organizationId),
		queryFn: () => apiFetch<PublicPagePage>(`/organizations/${organizationId}/pages?size=50`),
		enabled: !!organizationId,
	});
}

export function usePublicPage(slug: string) {
	return useQuery({
		queryKey: ["public", "pages", slug] as const,
		queryFn: () => apiFetch<PublicPage>(`/public/pages/${slug}`),
		enabled: !!slug,
		retry: false,
	});
}

export function useCreatePage(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreatePublicPageFormValues) =>
			apiFetch<PublicPage>(`/organizations/${organizationId}/pages`, {
				method: "POST",
				body: { ...values, summary: values.summary || null },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: pagesQueryKey(organizationId) });
		},
	});
}

export function usePublishPage(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (pageId: string) =>
			apiFetch<PublicPage>(`/organizations/${organizationId}/pages/${pageId}/publish`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: pagesQueryKey(organizationId) });
		},
	});
}

export function useUnpublishPage(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (pageId: string) =>
			apiFetch<PublicPage>(`/organizations/${organizationId}/pages/${pageId}/unpublish`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: pagesQueryKey(organizationId) });
		},
	});
}
