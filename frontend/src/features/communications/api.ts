import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { Announcement, AnnouncementAudience, AnnouncementScopeType, AnnouncementStatus, MyAnnouncement, PageResponse, ReminderResourceType } from "./types";

export function useMyAnnouncements() {
	return useQuery({
		queryKey: ["me", "announcements"],
		queryFn: () => apiFetch<PageResponse<MyAnnouncement>>("/me/announcements?page=0&size=50"),
		refetchInterval: 60_000,
	});
}

export function useMarkAnnouncementRead() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (announcementId: string) => apiFetch<void>(`/me/announcements/${announcementId}/read`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["me", "announcements"] }),
	});
}

export function useManagedAnnouncements(
	organizationId: string | undefined,
	scopeType: AnnouncementScopeType | undefined,
	scopeId: string | undefined,
	status: AnnouncementStatus | "" = "",
) {
	const params = new URLSearchParams({ page: "0", size: "50" });
	if (scopeType) params.set("scopeType", scopeType);
	if (scopeId) params.set("scopeId", scopeId);
	if (status) params.set("status", status);
	return useQuery({
		queryKey: ["announcements", "managed", organizationId, scopeType, scopeId, status],
		queryFn: () => apiFetch<PageResponse<Announcement>>(`/organizations/${organizationId}/announcements?${params.toString()}`),
		enabled: !!organizationId && !!scopeType && !!scopeId,
	});
}

export interface SaveAnnouncementInput {
	organizationId: string;
	scopeType: AnnouncementScopeType;
	scopeId: string;
	idempotencyKey: string;
	title: string;
	body: string;
	audience: AnnouncementAudience;
	emailEnabled: boolean;
	smsEnabled: boolean;
}

export function useCreateAnnouncement() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, ...body }: SaveAnnouncementInput) => apiFetch<Announcement>(`/organizations/${organizationId}/announcements`, { method: "POST", body }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["announcements", "managed"] }),
	});
}

export interface UpdateAnnouncementInput {
	organizationId: string;
	announcementId: string;
	title: string;
	body: string;
	audience: AnnouncementAudience;
	emailEnabled: boolean;
	smsEnabled: boolean;
}

export function useUpdateAnnouncement() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, announcementId, ...body }: UpdateAnnouncementInput) => apiFetch<Announcement>(`/organizations/${organizationId}/announcements/${announcementId}`, { method: "PATCH", body }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["announcements", "managed"] }),
	});
}

function lifecycleMutation(action: "publish" | "archive") {
	return function useAnnouncementLifecycle() {
		const queryClient = useQueryClient();
		return useMutation({
			mutationFn: ({ organizationId, announcementId }: { organizationId: string; announcementId: string }) =>
				apiFetch<Announcement>(`/organizations/${organizationId}/announcements/${announcementId}/${action}`, { method: "POST" }),
			onSuccess: () => {
				queryClient.invalidateQueries({ queryKey: ["announcements", "managed"] });
				queryClient.invalidateQueries({ queryKey: ["me", "announcements"] });
			},
		});
	};
}

export const usePublishAnnouncement = lifecycleMutation("publish");
export const useArchiveAnnouncement = lifecycleMutation("archive");

export interface SendReminderInput {
	organizationId: string;
	resourceType: ReminderResourceType;
	resourceId: string;
	idempotencyKey: string;
	emailEnabled: boolean;
	smsEnabled: boolean;
}

export function useSendReminder() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ organizationId, ...body }: SendReminderInput) => apiFetch<Announcement>(`/organizations/${organizationId}/reminders`, { method: "POST", body }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["announcements", "managed"] });
			queryClient.invalidateQueries({ queryKey: ["me", "announcements"] });
		},
	});
}
