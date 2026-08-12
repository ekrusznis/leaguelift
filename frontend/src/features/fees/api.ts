import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	ApplyAdjustmentFormValues,
	CreateFeeAssignmentFormValues,
	CreateFeeTemplateFormValues,
	RecordPaymentFormValues,
	VoidFormValues,
} from "./schema";
import type {
	FeeAdjustment,
	FeeAssignment,
	FeeAssignmentPage,
	FeeAssignmentStatus,
	FeePayment,
	FeePaymentCheckout,
	FeeTemplatePage,
	PaymentMethodAvailability,
} from "./types";

const templatesKey = (orgId: string) => ["organizations", orgId, "fee-templates"] as const;

/** Phase 32 scaffold — Stripe card is always available; Venmo/Cash App/Affirm show `available: false` until real credentials are configured. Not manager-gated: carries no secrets, guardians need it too. */
export function usePaymentMethods(organizationId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "payment-methods"] as const,
		queryFn: () => apiFetch<PaymentMethodAvailability[]>(`/organizations/${organizationId}/payment-methods`),
		enabled: !!organizationId,
	});
}
const assignmentsKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId, "fee-assignments"] as const;
const paymentsKey = (orgId: string, assignmentId: string) => ["organizations", orgId, "fee-assignments", assignmentId, "payments"] as const;
const adjustmentsKey = (orgId: string, assignmentId: string) => ["organizations", orgId, "fee-assignments", assignmentId, "adjustments"] as const;

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

// --- Payments ---

export function useFeePayments(organizationId: string, assignmentId: string) {
	return useQuery({
		queryKey: paymentsKey(organizationId, assignmentId),
		queryFn: () => apiFetch<FeePayment[]>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payments`),
		enabled: !!organizationId && !!assignmentId,
	});
}

export function useRecordPayment(organizationId: string, householdId: string, assignmentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: RecordPaymentFormValues) =>
			apiFetch<FeeAssignment>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payments`, {
				method: "POST",
				body: { ...values, note: values.note || null },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: paymentsKey(organizationId, assignmentId) });
			queryClient.invalidateQueries({ queryKey: paymentPlanKey(organizationId, assignmentId) });
		},
	});
}

/** Guardian-initiated online payment — the counterpart to staff-only `useRecordPayment`. Confirmation happens via the Stripe webhook, not this call's response, so the caller redirects to `checkoutUrl` rather than treating success as confirmation. */
export function useCreateFeeCheckoutSession(organizationId: string, assignmentId: string) {
	return useMutation({
		mutationFn: (values: { amountMinor: number; successUrl: string; cancelUrl: string }) =>
			apiFetch<FeePaymentCheckout>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/checkout-sessions`, {
				method: "POST",
				body: values,
			}),
	});
}

/** Polled after redirect back from Stripe Checkout — mirrors `useContributionStatus`'s own PENDING-status poll. */
export function useFeePaymentStatus(organizationId: string, assignmentId: string, paymentId: string | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "fee-assignments", assignmentId, "payments", paymentId] as const,
		queryFn: () => apiFetch<FeePayment>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payments/${paymentId}`),
		enabled: !!organizationId && !!assignmentId && !!paymentId,
		refetchInterval: (query) => (query.state.data?.status === "PENDING_CHECKOUT" ? 2000 : false),
	});
}

export function useVoidPayment(organizationId: string, householdId: string, assignmentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ paymentId, reason }: { paymentId: string } & VoidFormValues) =>
			apiFetch<FeeAssignment>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payments/${paymentId}`, {
				method: "DELETE",
				body: { reason },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: paymentsKey(organizationId, assignmentId) });
			queryClient.invalidateQueries({ queryKey: paymentPlanKey(organizationId, assignmentId) });
		},
	});
}

// --- Adjustments (manual discounts/credits) ---

export function useFeeAdjustments(organizationId: string, assignmentId: string) {
	return useQuery({
		queryKey: adjustmentsKey(organizationId, assignmentId),
		queryFn: () => apiFetch<FeeAdjustment[]>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/adjustments`),
		enabled: !!organizationId && !!assignmentId,
	});
}

export function useApplyAdjustment(organizationId: string, householdId: string, assignmentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: ApplyAdjustmentFormValues) =>
			apiFetch<FeeAssignment>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/adjustments`, {
				method: "POST",
				body: { ...values, reason: values.reason || null },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: adjustmentsKey(organizationId, assignmentId) });
		},
	});
}

export function useVoidAdjustment(organizationId: string, householdId: string, assignmentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ adjustmentId, reason }: { adjustmentId: string } & VoidFormValues) =>
			apiFetch<FeeAssignment>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/adjustments/${adjustmentId}`, {
				method: "DELETE",
				body: { reason },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: adjustmentsKey(organizationId, assignmentId) });
		},
	});
}

const paymentPlanKey = (orgId: string, assignmentId: string) => ["organizations", orgId, "fee-assignments", assignmentId, "payment-plan"] as const;

export function useFeePaymentPlan(organizationId: string, assignmentId: string) {
	return useQuery({
		queryKey: paymentPlanKey(organizationId, assignmentId),
		queryFn: () => apiFetch<import("./types").FeePaymentPlan | undefined>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payment-plan`),
		enabled: !!organizationId && !!assignmentId,
	});
}

export function useCreateFeePaymentPlan(organizationId: string, householdId: string, assignmentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: import("./types").CreateFeePaymentPlanInput) =>
			apiFetch<import("./types").FeePaymentPlan>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payment-plan`, { method: "POST", body: input }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: paymentPlanKey(organizationId, assignmentId) });
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
		},
	});
}

export function useCancelFeePaymentPlan(organizationId: string, householdId: string, assignmentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (reason: string) =>
			apiFetch<import("./types").FeePaymentPlan>(`/organizations/${organizationId}/fee-assignments/${assignmentId}/payment-plan`, { method: "DELETE", body: { reason } }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: paymentPlanKey(organizationId, assignmentId) });
			queryClient.invalidateQueries({ queryKey: assignmentsKey(organizationId, householdId) });
		},
	});
}
