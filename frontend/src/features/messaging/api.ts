import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { BroadcastMessage, ConversationContact, MessageAudience, MessageModerationEvent, MessageSafetyReport, MessageSafetyReportReason, MessageSafetyReportStatus, MessageScopeType, MessageThread, MessageThreadMember, MessageThreadStatus, MyBroadcastMessage, MyMessageThread, PageResponse } from "./types";

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

export function useMyThreadMembers(threadId: string | undefined, enabled: boolean) {
	return useQuery({
		queryKey: ["me", "message-threads", threadId, "members"],
		queryFn: () => apiFetch<MessageThreadMember[]>(`/me/message-threads/${threadId}/members`),
		enabled: !!threadId && enabled,
	});
}

export function useMarkMessageRead() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (messageId: string) => apiFetch<void>(`/me/messages/${messageId}/read`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] }),
	});
}

export function useReplyToConversation() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ threadId, body }: { threadId: string; body: string }) =>
			apiFetch<BroadcastMessage>(`/me/message-threads/${threadId}/messages`, {
				method: "POST",
				body: { idempotencyKey: crypto.randomUUID(), body },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] }),
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
	audience: Exclude<MessageAudience, "SELECTED">;
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

export function useConversationContacts(organizationId: string | undefined, teamId: string | undefined) {
	return useQuery({
		queryKey: ["message-threads", "contacts", organizationId, teamId],
		queryFn: () => apiFetch<ConversationContact[]>(`/organizations/${organizationId}/message-threads/contacts?teamId=${teamId}`),
		enabled: !!organizationId && !!teamId,
	});
}

export interface CreateConversationInput {
	organizationId: string;
	teamId: string;
	title: string;
	targetUserIds: string[];
	emailEnabled: boolean;
	smsEnabled: boolean;
	initialMessage: string;
}

export function useCreateConversation() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, ...input }: CreateConversationInput) =>
			apiFetch<MessageThread>(`/organizations/${organizationId}/message-threads/conversations`, {
				method: "POST",
				body: {
					...input,
					idempotencyKey: crypto.randomUUID(),
					initialMessageIdempotencyKey: crypto.randomUUID(),
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["message-threads", "managed"] });
			queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] });
		},
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
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["message-threads", "managed"] });
			queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] });
		},
	});
}


export function useReportMessage() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ messageId, reason, details }: { messageId: string; reason: MessageSafetyReportReason; details?: string }) =>
			apiFetch<MessageSafetyReport>(`/me/messages/${messageId}/reports`, { method: "POST", body: { reason, details: details?.trim() || null } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["me", "message-reports"] }),
	});
}

export function useMyMessageReports() {
	return useQuery({
		queryKey: ["me", "message-reports"],
		queryFn: () => apiFetch<PageResponse<MessageSafetyReport>>("/me/message-reports?page=0&size=25"),
	});
}

export function useManagedMessageReports(
	organizationId: string | undefined,
	scopeType: MessageScopeType | undefined,
	scopeId: string | undefined,
	status: MessageSafetyReportStatus | "" = "",
) {
	const params = new URLSearchParams({ page: "0", size: "50" });
	if (scopeType) params.set("scopeType", scopeType);
	if (scopeId) params.set("scopeId", scopeId);
	if (status) params.set("status", status);
	return useQuery({
		queryKey: ["message-reports", "managed", organizationId, scopeType, scopeId, status],
		queryFn: () => apiFetch<PageResponse<MessageSafetyReport>>(`/organizations/${organizationId}/message-reports?${params.toString()}`),
		enabled: !!organizationId && !!scopeType && !!scopeId,
	});
}

export function useModerationEvents(organizationId: string | undefined, reportId: string | undefined) {
	return useQuery({
		queryKey: ["message-reports", organizationId, reportId, "events"],
		queryFn: () => apiFetch<MessageModerationEvent[]>(`/organizations/${organizationId}/message-reports/${reportId}/events`),
		enabled: !!organizationId && !!reportId,
	});
}

export function useReviewMessageReport() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, reportId, status, note }: { organizationId: string; reportId: string; status: Exclude<MessageSafetyReportStatus, "OPEN">; note?: string }) =>
			apiFetch<MessageSafetyReport>(`/organizations/${organizationId}/message-reports/${reportId}`, { method: "PATCH", body: { status, note: note?.trim() || null } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["message-reports"] }),
	});
}

export function useSafetyLockMessageThread() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, threadId, reason, reportId }: { organizationId: string; threadId: string; reason: string; reportId?: string }) =>
			apiFetch<MessageThread>(`/organizations/${organizationId}/message-threads/${threadId}/safety-lock`, { method: "POST", body: { reason, reportId: reportId ?? null } }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["message-threads"] });
			queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] });
		},
	});
}

export function useSafetyUnlockMessageThread() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, threadId, note, reportId }: { organizationId: string; threadId: string; note: string; reportId?: string }) =>
			apiFetch<MessageThread>(`/organizations/${organizationId}/message-threads/${threadId}/safety-unlock`, { method: "POST", body: { note, reportId: reportId ?? null } }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["message-threads"] });
			queryClient.invalidateQueries({ queryKey: ["me", "message-threads"] });
		},
	});
}
