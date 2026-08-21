import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { MembershipPage } from "./types";

export interface MemberSearchParams {
	page?: number;
	size?: number;
	q?: string;
	role?: string;
	status?: string;
	sort?: "NAME_ASC" | "NAME_DESC" | "ROLE_ASC" | "NEWEST" | "OLDEST";
}

const baseKey = (organizationId: string) => ["organizations", organizationId, "members"] as const;

export function useMemberSearch(organizationId: string, params: MemberSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NAME_ASC",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.role) search.set("role", params.role);
	if (params.status) search.set("status", params.status);

	return useQuery({
		queryKey: [...baseKey(organizationId), "search", Object.fromEntries(search.entries())],
		queryFn: () => apiFetch<MembershipPage>(`/organizations/${organizationId}/members/search?${search.toString()}`),
		enabled: !!organizationId,
	});
}

export function useUpdateOrganizationMemberRole(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ memberId, role }: { memberId: string; role: string }) =>
			apiFetch(`/organizations/${organizationId}/members/${memberId}`, {
				method: "PATCH",
				body: { role },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: baseKey(organizationId) }),
	});
}

export function useDisableOrganizationMember(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (memberId: string) =>
			apiFetch(`/organizations/${organizationId}/members/${memberId}`, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: baseKey(organizationId) }),
	});
}

/** Direct transfer — the target must already be an active Administrator member. */
export function useTransferOwnership(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (newOwnerMembershipId: string) =>
			apiFetch(`/organizations/${organizationId}/members/ownership-transfer`, {
				method: "PATCH",
				body: { newOwnerMembershipId },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: baseKey(organizationId) }),
	});
}

export interface OwnershipTransferInvitation {
	id: string;
	organizationId: string;
	email: string;
	status: string;
	expiresAt: string;
	createdAt: string;
}

const ownershipInvitationKey = (organizationId: string) => ["organizations", organizationId, "ownership-transfer-invitation"] as const;

/** Owner-only backend endpoint — pass `enabled: false` for any viewer who isn't the current owner. */
export function usePendingOwnershipTransferInvitation(organizationId: string, enabled: boolean) {
	return useQuery({
		queryKey: ownershipInvitationKey(organizationId),
		queryFn: () => apiFetch<OwnershipTransferInvitation | null>(`/organizations/${organizationId}/ownership-transfer-invitations/pending`),
		enabled: !!organizationId && enabled,
	});
}

export function useInviteOwnershipTransfer(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (email: string) =>
			apiFetch<OwnershipTransferInvitation>(`/organizations/${organizationId}/ownership-transfer-invitations`, {
				method: "POST",
				body: { email },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ownershipInvitationKey(organizationId) }),
	});
}

export function useRevokeOwnershipTransferInvitation(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (invitationId: string) =>
			apiFetch(`/organizations/${organizationId}/ownership-transfer-invitations/${invitationId}`, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ownershipInvitationKey(organizationId) }),
	});
}

export interface OrganizationDeletionRequest {
	id: string;
	status: string;
	requestedAt: string;
	scheduledFor: string;
}

const organizationDeletionKey = (organizationId: string) => ["organizations", organizationId, "deletion-request"] as const;

/** Owner-only backend endpoint — pass `enabled: false` for any viewer who isn't the current owner. */
export function usePendingOrganizationDeletion(organizationId: string, enabled: boolean) {
	return useQuery({
		queryKey: organizationDeletionKey(organizationId),
		queryFn: () => apiFetch<OrganizationDeletionRequest | null>(`/organizations/${organizationId}/deletion-request`),
		enabled: !!organizationId && enabled,
	});
}

export function useRequestOrganizationDeletion(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<OrganizationDeletionRequest>(`/organizations/${organizationId}/deletion-request`, { method: "POST" }),
		onSuccess: (request) => queryClient.setQueryData(organizationDeletionKey(organizationId), request),
	});
}

export function useCancelOrganizationDeletion(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch(`/organizations/${organizationId}/deletion-request`, { method: "DELETE" }),
		onSuccess: () => queryClient.setQueryData(organizationDeletionKey(organizationId), null),
	});
}
