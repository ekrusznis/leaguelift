import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	NotificationPreferences,
	NotificationTopic,
	UpdateDefaultMediaVisibilityRequest,
	UpdateNotificationTopicRequest,
	UpdateSmsConsentRequest,
	UpdateUserPreferencesRequest,
	UserPreferences,
} from "./types";

export const userPreferencesQueryKey = ["me", "preferences"] as const;
export const notificationPreferencesQueryKey = ["me", "notification-preferences"] as const;

export function useUserPreferences() {
	return useQuery({
		queryKey: userPreferencesQueryKey,
		queryFn: ({ signal }) => apiFetch<UserPreferences>("/me/preferences", { signal }),
	});
}

export function useUpdateUserPreferences() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: UpdateUserPreferencesRequest) =>
			apiFetch<UserPreferences>("/me/preferences", { method: "PATCH", body: request }),
		onSuccess: (preferences) => {
			queryClient.setQueryData(userPreferencesQueryKey, preferences);
		},
	});
}

export function useUpdateDefaultMediaVisibility() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: UpdateDefaultMediaVisibilityRequest) =>
			apiFetch<UserPreferences>("/me/preferences/media-visibility", { method: "PATCH", body: request }),
		onSuccess: (preferences) => {
			queryClient.setQueryData(userPreferencesQueryKey, preferences);
		},
	});
}

export function useNotificationPreferences() {
	return useQuery({
		queryKey: notificationPreferencesQueryKey,
		queryFn: ({ signal }) => apiFetch<NotificationPreferences>("/me/notification-preferences", { signal }),
	});
}

export function useUpdateNotificationTopic() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ topic, request }: { topic: NotificationTopic; request: UpdateNotificationTopicRequest }) =>
			apiFetch<NotificationPreferences>(`/me/notification-preferences/${topic}`, { method: "PATCH", body: request }),
		onSuccess: (preferences) => {
			queryClient.setQueryData(notificationPreferencesQueryKey, preferences);
		},
	});
}

export function useUpdateSmsConsent() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: UpdateSmsConsentRequest) =>
			apiFetch<NotificationPreferences>("/me/sms-consent", { method: "PATCH", body: request }),
		onSuccess: (preferences) => {
			queryClient.setQueryData(notificationPreferencesQueryKey, preferences);
		},
	});
}

export interface AvatarResponse {
	avatarUrl: string | null;
	avatarSeed: string;
	avatarStyle: string;
}

interface AvatarUploadUrlResponse {
	uploadUrl: string;
	objectKey: string;
	expiresAt: string;
}

/** Presigns an upload, PUTs the file bytes directly to storage, then confirms — same three-step shape as the org media pipeline (`features/media/api.ts`), just against the org-independent `/me/avatar/*` routes. */
export function useUploadAvatar() {
	return useMutation({
		mutationFn: async (file: File) => {
			const { uploadUrl, objectKey } = await apiFetch<AvatarUploadUrlResponse>("/me/avatar/upload-url", {
				method: "POST",
				body: { contentType: file.type, fileSizeBytes: file.size },
			});
			const putResponse = await fetch(uploadUrl, { method: "PUT", headers: { "Content-Type": file.type }, body: file });
			if (!putResponse.ok) throw new Error("The photo could not be uploaded. Please try again.");
			return apiFetch<AvatarResponse>("/me/avatar/confirm", { method: "POST", body: { objectKey } });
		},
	});
}

export function useRemoveAvatar() {
	return useMutation({
		mutationFn: () => apiFetch<AvatarResponse>("/me/avatar", { method: "DELETE" }),
	});
}

export interface AccountDeletionRequest {
	id: string;
	status: string;
	requestedAt: string;
	scheduledFor: string;
}

const accountDeletionQueryKey = ["me", "deletion-request"] as const;

export function usePendingAccountDeletion() {
	return useQuery({
		queryKey: accountDeletionQueryKey,
		queryFn: () => apiFetch<AccountDeletionRequest | null>("/me/deletion-request"),
	});
}

export function useRequestAccountDeletion() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<AccountDeletionRequest>("/me/deletion-request", { method: "POST" }),
		onSuccess: (request) => queryClient.setQueryData(accountDeletionQueryKey, request),
	});
}

export function useCancelAccountDeletion() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch("/me/deletion-request", { method: "DELETE" }),
		onSuccess: () => queryClient.setQueryData(accountDeletionQueryKey, null),
	});
}
