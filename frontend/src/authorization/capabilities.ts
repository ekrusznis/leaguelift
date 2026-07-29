import type { AuthorizationContext, ContextType } from "./types";

export interface CapabilityQuery {
	contextType?: ContextType;
	resourceId?: string | null;
}

/**
 * The one place frontend code asks "can the signed-in user do X" — always a capability
 * string lookup against real `/me/contexts` data, never a `user.role === "X"` comparison
 * (DESIGN-DOC.md section 4.2). Used directly by `Can`/`RequireCapability` and by
 * registries filtering nav/widget items.
 */
export function hasCapability(contexts: AuthorizationContext[] | undefined, capability: string, query: CapabilityQuery = {}): boolean {
	if (!contexts) return false;
	return contexts.some((context) => {
		if (query.contextType && context.contextType !== query.contextType) return false;
		if (query.resourceId !== undefined && context.resourceId !== query.resourceId) return false;
		return context.capabilities.includes(capability);
	});
}

/** Every capability the caller holds across all contexts, optionally narrowed by contextType — used by registries. */
export function capabilitiesFor(contexts: AuthorizationContext[] | undefined, contextType?: ContextType, resourceId?: string | null): Set<string> {
	if (!contexts) return new Set();
	const matches = contexts.filter((context) => {
		if (contextType && context.contextType !== contextType) return false;
		if (resourceId !== undefined && context.resourceId !== resourceId) return false;
		return true;
	});
	return new Set(matches.flatMap((context) => context.capabilities));
}
