export type ContextType = "PERSONAL" | "ATHLETE" | "HOUSEHOLD" | "TEAM" | "ORGANIZATION" | "TOURNAMENT" | "PLATFORM_ADMIN";

/**
 * Mirrors the backend's `AuthorizationContext` (DESIGN-DOC.md section 4.2, ADR-020).
 * `capabilities` is always the fully-resolved set for `role` in this context — the
 * frontend never re-derives capabilities from a role name, only ever checks membership
 * in this array (see `Can`/`RequireCapability`/`hasCapability`).
 */
export interface AuthorizationContext {
	contextType: ContextType;
	resourceId: string | null;
	organizationId: string | null;
	label: string;
	role: string;
	capabilities: string[];
}
