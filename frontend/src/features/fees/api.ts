import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateFeeAssignmentFormValues, CreateFeeTemplateFormValues } from "./schema";
import type { FeeAssignmentPage, FeeAssignmentStatus, FeeTemplatePage } from "./types";

const templatesKey = (orgId: string) => ["organizations", orgId, "fee-templates"] as const;
const assignmentsKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId, "fee-assignments"] as const;

export function useFeeTemplates(organizationId: string) {
	return useQuery({
		queryKey: templatesKey(organizationId),
		queryFn: () => apiFetch<FeeTemplatePage>(`/organizations/${organizationId}/fee-templates`),
		enabled: !!organizationId,
	});
}

export function useCreateFeeTemplate(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateFeeTemplateFormValues) =>
			apiFetch(`/organizations/${organizationId}/fee-templates`, {
				method: "POST",
				body: {
					...values,
					description: values.description || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: templatesKey(organizationId) });
		},
	});
}

export function useArchiveFeeTemplate(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (templateId: string) =>
			apiFetch(`/organizations/${organizationId}/fee-templates/${templateId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: templatesKey(organizationId) });
		},
	});
}

export function useFeeAssignments(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: assignmentsKey(organizationId, householdId),
		queryFn: () => apiFetch<FeeAssignmentPage>(`/organizations/${organizationId}/households/${householdId}/fee-assignments`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useCreateFeeAssignment(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateFeeAssignmentFormValues) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/fee-assignments`, {
				method: "POST",
				body: {
					...values,
					dueDate: values.dueDate || null,
					feeTemplateId: values.feeTemplateId || null,
					participantId: values.participantId || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
		},
	});
}

export function useUpdateFeeAssignmentStatus(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ assignmentId, status }: { assignmentId: string; status: FeeAssignmentStatus }) =>
			apiFetch(`/organizations/${organizationId}/fee-assignments/${assignmentId}/status`, {
				method: "PATCH",
				body: { status },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
		},
	});
}
