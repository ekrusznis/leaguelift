import type { ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";
import { LoadingState } from "../components/states/LoadingState";
import { UnauthorizedState } from "../components/states/UnauthorizedState";

/**
 * Route-level gating is a UX convenience only — it is never the authorization
 * boundary. The backend enforces membership/role checks independently on every
 * request (DESIGN-DOC.md sections 7, 18.2), so a user who bypasses this component
 * still cannot read data they aren't authorized for.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
	const { status } = useAuth();

	if (status === "loading") {
		return <LoadingState label="Checking your session…" />;
	}

	if (status === "unauthenticated") {
		return <UnauthorizedState message="Please sign in to continue." />;
	}

	return <>{children}</>;
}
