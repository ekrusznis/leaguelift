import { z } from "zod";

/**
 * Zod v4's `.uuid()` requires an RFC-9562-compliant version/variant nibble (with only
 * the literal all-zeros nil and all-Fs max UUIDs special-cased) -- it rejects the
 * predictable `00000000-...-000000000001`-style IDs this codebase's own dev seed data
 * uses throughout (organizations, teams, tournaments), which have a version nibble of
 * `0`. Real backend-generated IDs (UUID.randomUUID() / Postgres gen_random_uuid()) are
 * always version 4 and pass either way, so this only ever broke against seed/demo
 * data -- but entity existence is authoritatively re-checked server-side regardless, so
 * loosen this to "UUID-shaped" rather than "version-1-8-compliant."
 */
const uuidShape = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const createPublicPageSchema = z.object({
	pageType: z.enum(["ORGANIZATION", "TEAM", "TOURNAMENT"]),
	entityId: z.string().regex(uuidShape, "Invalid entity."),
	slug: z
		.string()
		.trim()
		.min(1, "Slug is required.")
		.regex(/^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/, "Use lowercase letters, numbers, and hyphens only."),
	title: z.string().trim().min(1, "Title is required.").max(200),
	summary: z.string().trim().max(1000).optional().or(z.literal("")),
});

export type CreatePublicPageFormValues = z.infer<typeof createPublicPageSchema>;
