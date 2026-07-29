import type { ReactNode } from "react";
import { useContexts } from "./api";
import { hasCapability, type CapabilityQuery } from "./capabilities";

export interface CanProps extends CapabilityQuery {
	capability: string;
	children: ReactNode;
	fallback?: ReactNode;
}

/**
 * Renders `children` only when the signed-in user holds `capability` in the given
 * context (DESIGN-DOC.md section 10.3). Fails closed: while `/me/contexts` is loading,
 * or on any error, nothing renders (React is never an authorization boundary — the
 * backend still enforces every write independently — but a UI element for a capability
 * the caller doesn't have yet is confusing at best).
 */
export function Can({ capability, contextType, resourceId, children, fallback = null }: CanProps) {
	const { data: contexts } = useContexts();
	return hasCapability(contexts, capability, { contextType, resourceId }) ? <>{children}</> : <>{fallback}</>;
}
