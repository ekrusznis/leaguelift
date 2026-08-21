import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { AddAdultFormValues, CreateHouseholdFormValues, CreateParticipantFormValues } from "./schema";
import type { Household, HouseholdAdult, HouseholdPage, Participant, ParticipantTeamAssignment } from "./types";

const householdsKey = (orgId: string) => ["organizations", orgId, "households"] as const;
const householdKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId] as const;
const adultsKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId, "adults"] as const;
const participantsKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId, "participants"] as const;
const teamsKey = (orgId: string, participantId: string) => ["organizations", orgId, "participants", participantId, "teams"] as const;

export function useHouseholds(organizationId: string) {
	return useQuery({
		queryKey: householdsKey(organizationId),
		queryFn: () => apiFetch<HouseholdPage>(`/organizations/${organizationId}/households`),
		enabled: !!organizationId,
	});
}

export function useHousehold(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: householdKey(organizationId, householdId),
		queryFn: () => apiFetch<Household>(`/organizations/${organizationId}/households/${householdId}`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useCreateHousehold(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateHouseholdFormValues) =>
			apiFetch(`/organizations/${organizationId}/households`, {
				method: "POST",
				body: {
					...values,
					contactEmail: values.contactEmail || null,
					contactPhone: values.contactPhone || null,
					notes: values.notes || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: householdsKey(organizationId) });
		},
	});
}

export function useAdults(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: adultsKey(organizationId, householdId),
		queryFn: () => apiFetch<HouseholdAdult[]>(`/organizations/${organizationId}/households/${householdId}/adults`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useAddAdult(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: AddAdultFormValues) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/adults`, {
				method: "POST",
				body: {
					...values,
					email: values.email || null,
					phone: values.phone || null,
					relationship: values.relationship || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: adultsKey(organizationId, householdId) });
		},
	});
}

export function useRemoveAdult(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (adultId: string) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/adults/${adultId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: adultsKey(organizationId, householdId) });
		},
	});
}

export function useParticipants(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: participantsKey(organizationId, householdId),
		queryFn: () => apiFetch<Participant[]>(`/organizations/${organizationId}/households/${householdId}/participants`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useCreateParticipant(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateParticipantFormValues) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/participants`, {
				method: "POST",
				body: {
					...values,
					dateOfBirth: values.dateOfBirth || null,
					notes: values.notes || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: participantsKey(organizationId, householdId) });
		},
	});
}

export function useParticipantTeams(organizationId: string, participantId: string) {
	return useQuery({
		queryKey: teamsKey(organizationId, participantId),
		queryFn: () => apiFetch<ParticipantTeamAssignment[]>(`/organizations/${organizationId}/participants/${participantId}/teams`),
		enabled: !!organizationId && !!participantId,
	});
}

export function useAssignTeam(organizationId: string, participantId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (teamId: string) =>
			apiFetch(`/organizations/${organizationId}/participants/${participantId}/teams`, {
				method: "POST",
				body: { teamId },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamsKey(organizationId, participantId) });
		},
	});
}

export function useRemoveFromTeam(organizationId: string, participantId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (teamId: string) =>
			apiFetch(`/organizations/${organizationId}/participants/${participantId}/teams/${teamId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamsKey(organizationId, participantId) });
		},
	});
}

// --- Guardian / athlete self-service invitations ---

const householdInvitationsKey = (orgId: string, householdId: string) =>
	["organizations", orgId, "households", householdId, "invitations"] as const;

export interface InviteGuardianValues {
	firstName: string;
	lastName: string;
	email: string;
	relationship?: string;
}

/** Athlete comes first: this always targets a real, existing participant, and finds-or-creates the household_adult contact record internally. */
export function useInviteGuardian(organizationId: string, householdId: string, participantId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: InviteGuardianValues) =>
			apiFetch(`/organizations/${organizationId}/participants/${participantId}/guardian-invitations`, {
				method: "POST",
				body: { ...values, relationship: values.relationship || null },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: adultsKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: householdInvitationsKey(organizationId, householdId) });
		},
	});
}

/** Only callable by an active guardian of the athlete's own household — see HouseholdInvitationService.inviteAthlete. */
export function useInviteAthlete(organizationId: string, householdId: string, participantId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (email: string) =>
			apiFetch(`/organizations/${organizationId}/participants/${participantId}/athlete-invitations`, {
				method: "POST",
				body: { email },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: householdInvitationsKey(organizationId, householdId) });
		},
	});
}
