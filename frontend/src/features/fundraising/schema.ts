import { z } from "zod";

export const CAMPAIGN_TYPES = [
	"ORGANIZATION_GENERAL", "TEAM_GENERAL", "TRAVEL", "TOURNAMENT_FEES", "UNIFORMS", "EQUIPMENT",
	"FACILITY_IMPROVEMENTS", "SCHOLARSHIPS", "SPECIAL_EVENTS", "APPAREL_BASED", "SPONSOR_SUPPORTED",
] as const;

const dateFields = {
	startDate: z.string().optional().or(z.literal("")),
	endDate: z.string().optional().or(z.literal("")),
};

const commonEditable = {
	name: z.string().trim().min(1, "Name is required.").max(120),
	description: z.string().trim().max(2000).optional().or(z.literal("")),
	goalAmountDollars: z.coerce.number().min(0, "Goal must be 0 or greater.").max(50_000_000, "Goal is too large."),
	...dateFields,
	eventLocationName: z.string().trim().max(160).optional().or(z.literal("")),
	eventAddress: z.string().trim().max(500).optional().or(z.literal("")),
};

export const createCampaignSchema = z.object({
	teamId: z.string().optional().or(z.literal("")),
	...commonEditable,
	slug: z.string().trim().min(1, "Slug is required.").regex(/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/, "Use lowercase letters, numbers, and hyphens only."),
	campaignType: z.enum(CAMPAIGN_TYPES),
	currency: z.string().length(3).default("USD"),
}).refine((values) => !values.startDate || !values.endDate || values.endDate >= values.startDate, {
	message: "End date must be on or after the start date.", path: ["endDate"],
});
export type CreateCampaignFormValues = z.infer<typeof createCampaignSchema>;

export const updateCampaignSchema = z.object(commonEditable).refine(
	(values) => !values.startDate || !values.endDate || values.endDate >= values.startDate,
	{ message: "End date must be on or after the start date.", path: ["endDate"] },
);
export type UpdateCampaignFormValues = z.infer<typeof updateCampaignSchema>;

export const createContributionSchema = z.object({
	amountDollars: z.coerce.number().min(1, "Minimum contribution is $1.00.").max(50_000, "Maximum contribution is $50,000.00."),
	supporterName: z.string().trim().max(120).optional().or(z.literal("")),
	isAnonymous: z.boolean(),
	supporterEmail: z.string().trim().email("Enter a valid email address.").max(254).optional().or(z.literal("")),
});
export type CreateContributionFormValues = z.infer<typeof createContributionSchema>;
