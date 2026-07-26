import { z } from "zod";
import { ORGANIZATION_TYPES } from "./types";

/**
 * Mirrors the backend's CreateOrganizationRequest validation
 * (backend/src/main/kotlin/com/leaguelift/organization/web/OrganizationDto.kt) and
 * docs/openapi.yaml. Client-side validation is for UX only — the backend remains
 * authoritative (DESIGN-DOC.md section 17.2).
 */
export const createOrganizationSchema = z.object({
	name: z.string().trim().min(2, "Name must be at least 2 characters.").max(120),
	slug: z
		.string()
		.trim()
		.min(1, "Slug is required.")
		.regex(/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/, "Use lowercase letters, numbers, and hyphens only."),
	organizationType: z.enum(ORGANIZATION_TYPES),
});

export type CreateOrganizationFormValues = z.infer<typeof createOrganizationSchema>;
