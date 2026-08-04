import { z } from "zod";
import { INVITABLE_ROLES, ORGANIZATION_TYPES } from "./types";

/**
 * Mirrors the backend's CreateOrganizationRequest validation
 * (backend/src/main/kotlin/com/rally26/organization/web/OrganizationDto.kt) and
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

export const COMMON_SPORTS = [
	"Soccer",
	"Baseball",
	"Softball",
	"Basketball",
	"Football",
	"Volleyball",
	"Lacrosse",
	"Hockey",
	"Swimming",
	"Track & Field",
	"Wrestling",
	"Cheer",
] as const;

export const updateOrganizationProfileSchema = z.object({
	name: z.string().trim().min(2, "Name must be at least 2 characters.").max(120),
	organizationType: z.enum(ORGANIZATION_TYPES),
	sports: z.array(z.string()).min(1, "Select at least one sport."),
	contactEmail: z.string().trim().email("Enter a valid email address."),
	contactPhone: z.string().trim().max(40).optional().or(z.literal("")),
});

export type UpdateOrganizationProfileFormValues = z.infer<typeof updateOrganizationProfileSchema>;

export const createInvitationSchema = z.object({
	email: z.string().trim().email("Enter a valid email address."),
	role: z.enum(INVITABLE_ROLES),
});

export type CreateInvitationFormValues = z.infer<typeof createInvitationSchema>;
