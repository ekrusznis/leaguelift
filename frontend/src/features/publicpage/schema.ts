import { z } from "zod";

export const createPublicPageSchema = z.object({
	pageType: z.enum(["ORGANIZATION", "TEAM", "TOURNAMENT"]),
	entityId: z.string().uuid("Invalid entity."),
	slug: z
		.string()
		.trim()
		.min(1, "Slug is required.")
		.regex(/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/, "Use lowercase letters, numbers, and hyphens only."),
	title: z.string().trim().min(1, "Title is required.").max(200),
	summary: z.string().trim().max(1000).optional().or(z.literal("")),
});

export type CreatePublicPageFormValues = z.infer<typeof createPublicPageSchema>;
