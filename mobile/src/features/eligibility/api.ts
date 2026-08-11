import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { EligibilityClearance, ParticipantRequirement, SubmitGuardianEvidenceRequest } from './types';

const participantRequirementsKey = (organizationId: string | null, participantId: string | null) => [
  'organizations',
  organizationId,
  'eligibility',
  'participants',
  participantId,
  'requirements',
];

/** GET .../eligibility/participants/{id}/requirements — guardian/athlete-self facing (Phase 31 slice 31.2/31.4). */
export function useParticipantEligibilityRequirements(organizationId: string | null, participantId: string | null) {
  return useQuery({
    queryKey: participantRequirementsKey(organizationId, participantId),
    queryFn: ({ signal }) =>
      apiFetch<ParticipantRequirement[]>(
        `/organizations/${organizationId}/eligibility/participants/${participantId}/requirements`,
        { signal },
      ),
    enabled: !!organizationId && !!participantId,
  });
}

export function useSubmitGuardianEvidence(organizationId: string | null, participantId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requirementId, ...body }: SubmitGuardianEvidenceRequest & { requirementId: string }) =>
      apiFetch(`/organizations/${organizationId}/eligibility/participants/${participantId}/requirements/${requirementId}/evidence`, {
        method: 'POST',
        body,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: participantRequirementsKey(organizationId, participantId) });
    },
  });
}

/** GET .../teams/{teamId}/eligibility/clearance?status= — coach roster clearance view (Phase 31 slice 31.4), optionally filtered to one status (e.g. INELIGIBLE). */
export function useTeamEligibilityClearance(organizationId: string | null, teamId: string | null, statusFilter?: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'teams', teamId, 'eligibility', 'clearance', statusFilter ?? null],
    queryFn: ({ signal }) =>
      apiFetch<EligibilityClearance[]>(
        `/organizations/${organizationId}/teams/${teamId}/eligibility/clearance${statusFilter ? `?status=${statusFilter}` : ''}`,
        { signal },
      ),
    enabled: !!organizationId && !!teamId,
  });
}
