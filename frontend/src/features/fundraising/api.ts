import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateCampaignFormValues, CreateContributionFormValues, UpdateCampaignFormValues } from "./schema";
import type {
	Campaign,
	CampaignPage,
	CampaignShareLink,
	CampaignStatus,
	Contribution,
	ContributionCheckout,
	ContributionPage,
	ContributionStatusResult,
	FundraiserTemplateKey,
	FundraisingSettings,
	PublicCampaign,
} from "./types";

const campaignsKey = (organizationId: string) => ["organizations", organizationId, "campaigns"] as const;
const fundraisingSettingsKey = (organizationId: string) => ["organizations", organizationId, "fundraising", "settings"] as const;

function invalidateCampaigns(queryClient: ReturnType<typeof useQueryClient>, organizationId: string) {
	return queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
}

export function useCampaigns(organizationId: string) {
	return useQuery({
		queryKey: campaignsKey(organizationId),
		queryFn: () => apiFetch<CampaignPage>(`/organizations/${organizationId}/campaigns`),
		enabled: !!organizationId,
	});
}

export function useCampaign(organizationId: string, campaignId: string) {
	return useQuery({
		queryKey: [...campaignsKey(organizationId), campaignId],
		queryFn: () => apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}`),
		enabled: !!organizationId && !!campaignId,
	});
}

export function useCreateCampaign(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateCampaignFormValues & { templateKey?: FundraiserTemplateKey | null }) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns`, {
				method: "POST",
				body: {
					teamId: values.teamId || null,
					name: values.name,
					slug: values.slug,
					description: values.description || null,
					campaignType: values.campaignType,
					goalAmountMinor: Math.round(values.goalAmountDollars * 100),
					currency: values.currency,
					startDate: values.startDate || null,
					endDate: values.endDate || null,
					eventLocationName: values.eventLocationName || null,
					eventAddress: values.eventAddress || null,
					templateKey: values.templateKey ?? null,
				},
			}),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

export function useUpdateCampaign(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ campaignId, values }: { campaignId: string; values: UpdateCampaignFormValues }) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}`, {
				method: "PATCH",
				body: {
					name: values.name,
					description: values.description || null,
					goalAmountMinor: Math.round(values.goalAmountDollars * 100),
					startDate: values.startDate || null,
					endDate: values.endDate || null,
					eventLocationName: values.eventLocationName || null,
					eventAddress: values.eventAddress || null,
				},
			}),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

export function useRequestCampaignActivation(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/request-activation`, { method: "POST" }),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

export function useApproveCampaign(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/approve`, { method: "POST" }),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

export function useRejectCampaignApproval(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/reject-approval`, { method: "POST" }),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

/** Kept for older call sites; useRequestCampaignActivation is the preferred action. */
export function usePublishCampaign(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/publish`, { method: "POST" }),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

export function useUpdateCampaignStatus(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ campaignId, status }: { campaignId: string; status: CampaignStatus }) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/status`, {
				method: "PATCH",
				body: { status },
			}),
		onSuccess: () => invalidateCampaigns(queryClient, organizationId),
	});
}

export function useFundraisingSettings(organizationId: string) {
	return useQuery({
		queryKey: fundraisingSettingsKey(organizationId),
		queryFn: () => apiFetch<FundraisingSettings>(`/organizations/${organizationId}/fundraising/settings`),
		enabled: !!organizationId,
	});
}

export function useUpdateFundraisingSettings(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (requireOwnerApproval: boolean) =>
			apiFetch<FundraisingSettings>(`/organizations/${organizationId}/fundraising/settings`, {
				method: "PATCH",
				body: { requireOwnerApproval },
			}),
		onSuccess: async () => {
			await queryClient.invalidateQueries({ queryKey: fundraisingSettingsKey(organizationId) });
			await invalidateCampaigns(queryClient, organizationId);
		},
	});
}

/** Reuses the same ZXing-generated QR seam Sponsorship/Swag Shop already have. */
export function useCampaignShareLink(organizationId: string) {
	return useMutation({
		mutationFn: (url: string) =>
			apiFetch<CampaignShareLink>(`/organizations/${organizationId}/campaigns/qr-code?url=${encodeURIComponent(url)}`),
	});
}

export function usePublicCampaign(slug: string) {
	return useQuery({
		queryKey: ["public", "campaigns", slug] as const,
		queryFn: () => apiFetch<PublicCampaign>(`/public/campaigns/${slug}`),
		enabled: !!slug,
		retry: false,
	});
}

export function useCreateContributionCheckout(slug: string) {
	return useMutation({
		mutationFn: (values: CreateContributionFormValues & { successUrl: string; cancelUrl: string; attributionCode?: string | null }) =>
			apiFetch<ContributionCheckout>(`/public/campaigns/${slug}/contributions`, {
				method: "POST",
				body: {
					amountMinor: Math.round(values.amountDollars * 100),
					supporterName: values.isAnonymous ? null : values.supporterName || null,
					isAnonymous: values.isAnonymous,
					supporterEmail: values.supporterEmail || null,
					successUrl: values.successUrl,
					cancelUrl: values.cancelUrl,
					attributionCode: values.attributionCode || null,
				},
			}),
	});
}

export function useContributionStatus(slug: string, contributionId: string | null) {
	return useQuery({
		queryKey: ["public", "campaigns", slug, "contributions", contributionId] as const,
		queryFn: () => apiFetch<ContributionStatusResult>(`/public/campaigns/${slug}/contributions/${contributionId}`),
		enabled: !!slug && !!contributionId,
		refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 2000 : false),
	});
}

const orgContributionsKey = (organizationId: string, campaignId: string) =>
	["organizations", organizationId, "campaigns", campaignId, "contributions"] as const;

export function useOrgContributions(organizationId: string, campaignId: string) {
	return useQuery({
		queryKey: orgContributionsKey(organizationId, campaignId),
		queryFn: () => apiFetch<ContributionPage>(`/organizations/${organizationId}/campaigns/${campaignId}/contributions`),
		enabled: !!organizationId && !!campaignId,
	});
}

export function useRefundContribution(organizationId: string, campaignId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (contributionId: string) =>
			apiFetch<Contribution>(`/organizations/${organizationId}/campaigns/${campaignId}/contributions/${contributionId}/refund`, {
				method: "POST",
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: orgContributionsKey(organizationId, campaignId) }),
	});
}
