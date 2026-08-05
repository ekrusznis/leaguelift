import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	PageResponse,
	SupportArticle,
	SupportArticleStatus,
	SupportAudience,
	SupportCase,
	SupportCaseCategory,
	SupportCasePriority,
	SupportCaseStatus,
} from "./types";

function helpQuery(query?: string, category?: string) {
	const params = new URLSearchParams();
	if (query?.trim()) params.set("query", query.trim());
	if (category) params.set("category", category);
	const value = params.toString();
	return value ? `?${value}` : "";
}

export function useHelpArticles(authenticated: boolean, query?: string, category?: string) {
	return useQuery({
		queryKey: ["help", authenticated ? "authenticated" : "public", query ?? "", category ?? ""],
		queryFn: () => apiFetch<SupportArticle[]>(`${authenticated ? "/help/articles" : "/public/help/articles"}${helpQuery(query, category)}`),
	});
}

export function useHelpArticle(authenticated: boolean, slug: string | undefined) {
	return useQuery({
		queryKey: ["help", authenticated ? "authenticated" : "public", "article", slug],
		queryFn: () => apiFetch<SupportArticle>(`${authenticated ? "/help/articles" : "/public/help/articles"}/${slug}`),
		enabled: !!slug,
	});
}

export interface CreateSupportCaseInput {
	idempotencyKey: string;
	organizationId?: string | null;
	requesterName?: string;
	requesterEmail?: string;
	category: SupportCaseCategory;
	subject: string;
	description: string;
}

export function useCreateSupportCase(authenticated: boolean) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: CreateSupportCaseInput) => apiFetch<SupportCase>(authenticated ? "/support-cases" : "/public/support-cases", {
			method: "POST",
			body: input,
		}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["support-cases", "mine"] }),
	});
}

export function useMySupportCases(enabled: boolean) {
	return useQuery({
		queryKey: ["support-cases", "mine"],
		queryFn: () => apiFetch<PageResponse<SupportCase>>("/support-cases?page=0&size=50"),
		enabled,
	});
}

export interface PlatformHelpFilters {
	query?: string;
	status?: SupportArticleStatus | "";
	audience?: SupportAudience | "";
	category?: string;
	page?: number;
	size?: number;
}

function platformHelpQuery(filters: PlatformHelpFilters) {
	const params = new URLSearchParams();
	if (filters.query?.trim()) params.set("query", filters.query.trim());
	if (filters.status) params.set("status", filters.status);
	if (filters.audience) params.set("audience", filters.audience);
	if (filters.category) params.set("category", filters.category);
	params.set("page", String(filters.page ?? 0));
	params.set("size", String(filters.size ?? 50));
	return params.toString();
}

export function usePlatformHelpArticles(filters: PlatformHelpFilters) {
	return useQuery({
		queryKey: ["platform", "help", filters],
		queryFn: () => apiFetch<PageResponse<SupportArticle>>(`/platform/admin/help/articles?${platformHelpQuery(filters)}`),
	});
}

export type SaveSupportArticleInput = Pick<SupportArticle, "slug" | "title" | "summary" | "bodyMarkdown" | "category" | "audience" | "sortOrder">;

export function useSaveSupportArticle(articleId?: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: SaveSupportArticleInput) => apiFetch<SupportArticle>(articleId ? `/platform/admin/help/articles/${articleId}` : "/platform/admin/help/articles", {
			method: articleId ? "PUT" : "POST",
			body: input,
		}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["platform", "help"] }),
	});
}

export function usePublishSupportArticle() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (articleId: string) => apiFetch<SupportArticle>(`/platform/admin/help/articles/${articleId}/publish`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["platform", "help"] }),
	});
}

export function useArchiveSupportArticle() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (articleId: string) => apiFetch<SupportArticle>(`/platform/admin/help/articles/${articleId}/archive`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["platform", "help"] }),
	});
}

export interface PlatformSupportCaseFilters {
	query?: string;
	status?: SupportCaseStatus | "";
	priority?: SupportCasePriority | "";
	category?: SupportCaseCategory | "";
	organizationId?: string;
	page?: number;
	size?: number;
}

function platformCaseQuery(filters: PlatformSupportCaseFilters) {
	const params = new URLSearchParams();
	if (filters.query?.trim()) params.set("query", filters.query.trim());
	if (filters.status) params.set("status", filters.status);
	if (filters.priority) params.set("priority", filters.priority);
	if (filters.category) params.set("category", filters.category);
	if (filters.organizationId) params.set("organizationId", filters.organizationId);
	params.set("page", String(filters.page ?? 0));
	params.set("size", String(filters.size ?? 50));
	return params.toString();
}

export function usePlatformSupportCases(filters: PlatformSupportCaseFilters) {
	return useQuery({
		queryKey: ["platform", "support-cases", filters],
		queryFn: () => apiFetch<PageResponse<SupportCase>>(`/platform/admin/support-cases?${platformCaseQuery(filters)}`),
	});
}

export interface UpdateSupportCaseInput {
	caseId: string;
	status: SupportCaseStatus;
	priority: SupportCasePriority;
	assignedPlatformUserId: string | null;
	resolution: string | null;
}

export function useUpdateSupportCase() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ caseId, ...body }: UpdateSupportCaseInput) => apiFetch<SupportCase>(`/platform/admin/support-cases/${caseId}`, { method: "PATCH", body }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["platform", "support-cases"] }),
	});
}

export interface SendSupportCaseEmailInput {
	caseId: string;
	subject: string;
	body: string;
}

/** One-way, ad-hoc email to the requester -- independent of a status change, no reply thread (see backend SupportCaseService.sendPlatformEmail doc comment). */
export function useSendSupportCaseEmail() {
	return useMutation({
		mutationFn: ({ caseId, ...body }: SendSupportCaseEmailInput) => apiFetch<SupportCase>(`/platform/admin/support-cases/${caseId}/send-email`, { method: "POST", body }),
	});
}
