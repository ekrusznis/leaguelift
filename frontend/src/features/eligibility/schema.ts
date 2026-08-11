import { z } from "zod";

export const createEligibilityRequirementSchema = z
	.object({
		title: z.string().trim().min(1, "Title is required.").max(200),
		mode: z.enum(["GUARDIAN_ESIGN_ACKNOWLEDGMENT", "GUARDIAN_LEGAL_NAME_SIGNATURE", "FILE_UPLOAD", "STAFF_REVIEWED_EXTERNAL", "INFORMATIONAL"]),
		content: z.string().trim().min(1, "Content is required.").max(20_000),
		sport: z.string().trim().max(100).optional().or(z.literal("")),
		season: z.string().trim().max(100).optional().or(z.literal("")),
		teamId: z.string().optional().or(z.literal("")),
		effectiveStart: z.string().min(1, "Effective date is required."),
		expirationRule: z.enum(["NEVER", "SEASON_END", "FIXED_DAYS_FROM_ACCEPTANCE", "ORG_CONFIGURED"]),
		expirationDays: z.coerce.number().int().positive().optional(),
	})
	.refine((values) => values.expirationRule !== "FIXED_DAYS_FROM_ACCEPTANCE" || !!values.expirationDays, {
		message: "Expiration days is required for a fixed-days expiration rule.",
		path: ["expirationDays"],
	});

export type CreateEligibilityRequirementFormValues = z.infer<typeof createEligibilityRequirementSchema>;

export const createEligibilityRequirementVersionSchema = z.object({
	title: z.string().trim().min(1, "Title is required.").max(200),
	content: z.string().trim().min(1, "Content is required.").max(20_000),
	effectiveStart: z.string().min(1, "Effective date is required."),
});

export type CreateEligibilityRequirementVersionFormValues = z.infer<typeof createEligibilityRequirementVersionSchema>;

export const guardianLegalNameSchema = z.object({
	enteredLegalName: z.string().trim().min(1, "Type the guardian's full legal name.").max(200),
});

export type GuardianLegalNameFormValues = z.infer<typeof guardianLegalNameSchema>;
