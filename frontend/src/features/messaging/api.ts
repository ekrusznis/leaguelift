import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { BroadcastMessage, MessageAudience, MessageScopeType, MessageThread, MessageThreadStatus, MyBroadcastMessage, MyMessageThread, PageResponse } from "./types";

export function useMyMessageThreads() {
	return useQuery({
		queryKey: ["me", "message-threads"],
		queryFn: () => apiFetch<PageResponse<MyMessageThread>>("/me/message-threads?page=0&size=50"),
		refetchInterval: 60_000,
	});
}

export function useMyThreadMessages(threadId: string | undefined) {
	return useQuery({
		queryKey: ["me", "message-threads", threadId, "messages"],
		queryFn: () => apiFetch<PageResponse<MyBroadcastMessage>>(`/me/message-threads/${threadId}/messages?page=0&size=100`),
		enabled: !!threadId,
	});
}

export function useMarkMessageRead() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (messageId: string) => apiFetch<void>(`/me/messages/${messageId}/read`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] });
		},
	});
}

export function useManagedMessageThreads(
	organizationId: string | undefined,
	scopeType: MessageScopeType | undefined,
	scopeId: string | undefined,
	status: MessageThreadStatus | "" = "",
) {
	const params = new URLSearchParams({ page: "0", size: "50" });
	if (scopeType) params.set("scopeType", scopeType);
	if (scopeId) params.set("scopeId", scopeId);
	if (status) params.set("status", status);
	return useQuery({
		queryKey: ["message-threads", "managed", organizationId, scopeType, scopeId, status],
		queryFn: () => apiFetch<PageResponse<MessageThread>>(`/organizations/${organizationId}/message-threads?${params.toString()}`),
		enabled: !!organizationId && !!scopeType && !!scopeId,
	});
}

export function useManagedThreadMessages(organizationId: string | undefined, threadId: string | undefined) {
	return useQuery({
		queryKey: ["message-threads", "managed", organizationId, threadId, "messages"],
		queryFn: () => apiFetch<PageResponse<BroadcastMessage>>(`/organizations/${organizationId}/message-threads/${threadId}/messages?page=0&size=100`),
		enabled: !!organizationId && !!threadId,
	});
}

export interface CreateMessageThreadInput {
	organizationId: string;
	scopeType: MessageScopeType;
	scopeId: string;
	idempotencyKey: string;
	title: string;
	audience: MessageAudience;
	emailEnabled: boolean;
	smsEnabled: boolean;
}

export function useCreateMessageThread() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, ...body }: CreateMessageThreadInput) =>
			apiFetch<MessageThread>(`/organizations/${organizationId}/message-threads`, { method: "POST", body }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["message-threads", "managed"] }),
	});
}

export function useSendBroadcastMessage() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, threadId, body }: { organizationId: string; threadId: string; body: string }) =>
			apiFetch<BroadcastMessage>(`/organizations/${organizationId}/message-threads/${threadId}/messages`, {
				method: "POST",
				body: { idempotencyKey: crypto.randomUUID(), body },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["message-threads", "managed"] });
			queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] });
		},
	});
}

export function useArchiveMessageThread() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, threadId }: { organizationId: string; threadId: string }) =>
			apiFetch<MessageThread>(`/organizations/${organizationId}/message-threads/${threadId}/archive`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["message-threads", "managed"] }),
	});
}
