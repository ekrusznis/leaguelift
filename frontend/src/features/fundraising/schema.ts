import { z } from "zod";

export const CAMPAIGN_TYPES = [
	"ORGANIZATION_GENERAL",
	"TEAM_GENERAL",
	"TRAVEL",
	"TOURNAMENT_FEES",
	"UNIFORMS",
	"EQUIPMENT",
	"FACILITY_IMPROVEMENTS",
	"SCHOLARSHIPS",
	"SPECIAL_EVENTS",
	"APPAREL_BASED",
	"SPONSOR_SUPPORTED",
] as const;

/**
 * Mirrors the backend's CreateCampaignRequest validation
 * (backend/src/main/kotlin/com/leaguelift/fundraising/web/CampaignDto.kt) and
 * docs/openapi.yaml. Client-side validation is for UX only — the backend remains
 * authoritative (DESIGN-DOC.md section 17.2).
 */
export const createCampaignSchema = z
	.object({
		teamId: z.string().optional().or(z.literal("")),
		name: z.string().trim().min(1, "Name is required.").max(120),
		slug: z
			.string()
			.trim()
			.min(1, "Slug is required.")
			.regex(/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/, "Use lowercase letters, numbers, and hyphens only."),
		description: z.string().trim().max(2000).optional().or(z.literal("")),
		campaignType: z.enum(CAMPAIGN_TYPES),
		goalAmountMinor: z.coerce.number().int("Amount must be a whole number of cents.").min(0, "Amount must be 0 or greater."),
		currency: z.string().length(3).default("USD"),
		startDate: z.string().optional().or(z.literal("")),
		endDate: z.string().optional().or(z.literal("")),
	})
	.refine((values) => !values.startDate || !values.endDate || values.endDate >= values.startDate, {
		message: "End date must be on or after the start date.",
		path: ["endDate"],
	});

export type CreateCampaignFormValues = z.infer<typeof createCampaignSchema>;
