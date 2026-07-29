import type { ReactNode } from "react";
import { LoadingState } from "../components/states/LoadingState";
import { UnauthorizedState } from "../components/states/UnauthorizedState";
import { useContexts } from "./api";
import { hasCapability, type CapabilityQuery } from "./capabilities";

export interface RequireCapabilityProps extends CapabilityQuery {
	capability: string;
	children: ReactNode;
	message?: string;
}

/**
 * Route/page-level guard sibling to `Can` (DESIGN-DOC.md section 10.3): shows the
 * shared loading/unauthorized states instead of silently rendering nothing, for the
 * (rarer) case of gating a whole page/section rather than one UI fragment. Still not an
 * authorization boundary by itself — every underlying API call independently enforces
 * the same capability server-side.
 */
export function RequireCapability({ capability, contextType, resourceId, children, message }: RequireCapabilityProps) {
	const { data: contexts, isLoading, isError } = useContexts();

	if (isLoading) return <LoadingState label="Checking access…" />;
	if (isError) return <UnauthorizedState message="Could not verify your access." />;
	if (!hasCapability(contexts, capability, { contextType, resourceId })) {
		return <UnauthorizedState message={message} />;
	}
	return <>{children}</>;
}
