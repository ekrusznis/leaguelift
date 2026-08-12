import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateInvitationFormValues, CreateOrganizationFormValues, UpdateOrganizationProfileFormValues } from "./schema";
import type { InvitationPage, OnboardingProgress, Organization, OrganizationPage, TimezoneSuggestion } from "./types";

const organizationsQueryKey = (page: number, size: number) => ["organizations", { page, size }] as const;
/** Exported so other feature modules (e.g. media) can invalidate the organization detail view after a related change. */
export const organizationQueryKey = (id: string) => ["organizations", id] as const;
const onboardingQueryKey = (id: string) => ["organizations", id, "onboarding"] as const;
const invitationsQueryKey = (organizationId: string) => ["organizations", organizationId, "invitations"] as const;
const timezoneSuggestionQueryKey = (id: string) => ["organizations", id, "timezone-suggestion"] as const;

export function useOrganizations(page = 0, size = 20, enabled = true) {
	return useQuery({
		queryKey: organizationsQueryKey(page, size),
		queryFn: () => apiFetch<OrganizationPage>(`/organizations?page=${page}&size=${size}`),
		enabled,
	});
}

export function useOrganization(organizationId: string, enabled = true) {
	return useQuery({
		queryKey: organizationQueryKey(organizationId),
		queryFn: () => apiFetch<Organization>(`/organizations/${organizationId}`),
		enabled: enabled && !!organizationId,
	});
}

export function useOnboardingProgress(organizationId: string) {
	return useQuery({
		queryKey: onboardingQueryKey(organizationId),
		queryFn: () => apiFetch<OnboardingProgress>(`/organizations/${organizationId}/onboarding`),
		enabled: !!organizationId,
	});
}

export function useCreateOrganization() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateOrganizationFormValues) =>
			apiFetch("/organizations", { method: "POST", body: values }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["organizations"] });
		},
	});
}

export function useUpdateOrganizationProfile(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: UpdateOrganizationProfileFormValues) =>
			apiFetch<Organization>(`/organizations/${organizationId}`, {
				method: "PATCH",
				body: {
					...values,
					contactPhone: values.contactPhone || null,
					addressLine1: values.addressLine1 || null,
					addressLine2: values.addressLine2 || null,
					addressCity: values.addressCity || null,
					addressState: values.addressState || null,
					addressPostalCode: values.addressPostalCode || null,
					addressCountry: values.addressCountry || null,
					timezone: values.timezone || null,
					zelleHandle: values.zelleHandle || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: organizationQueryKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: onboardingQueryKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: ["organizations"] });
		},
	});
}

/** Phase 24 slice 24.5 (ADR-071): a static country/state heuristic suggestion — the owner must still actively confirm it via form submit. */
export function useTimezoneSuggestion(organizationId: string, enabled = true) {
	return useQuery({
		queryKey: timezoneSuggestionQueryKey(organizationId),
		queryFn: () => apiFetch<TimezoneSuggestion>(`/organizations/${organizationId}/timezone-suggestion`),
		enabled: enabled && !!organizationId,
	});
}

export function useInvitations(organizationId: string) {
	return useQuery({
		queryKey: invitationsQueryKey(organizationId),
		queryFn: () => apiFetch<InvitationPage>(`/organizations/${organizationId}/invitations`),
		enabled: !!organizationId,
	});
}

export function useCreateInvitation(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateInvitationFormValues) =>
			apiFetch(`/organizations/${organizationId}/invitations`, { method: "POST", body: values }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: invitationsQueryKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: onboardingQueryKey(organizationId) });
		},
	});
}

export function useRevokeInvitation(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (invitationId: string) =>
			apiFetch(`/organizations/${organizationId}/invitations/${invitationId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: invitationsQueryKey(organizationId) });
		},
	});
}
