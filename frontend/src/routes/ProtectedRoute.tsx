import { Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { UnauthorizedState } from "../components/states/UnauthorizedState";

/**
 * Route-level gating is a UX convenience only — it is never the authorization
 * boundary. The backend enforces membership/role checks independently on every
 * request (DESIGN-DOC.md sections 7, 18.2), so a user who bypasses this component
 * still cannot read data they aren't authorized for.
 *
 * Used as a layout route (`<Route element={<ProtectedRoute />}>`) so any number of
 * nested child routes can share the same gate via <Outlet />.
 */
export function ProtectedRoute() {
	const { status } = useAuth();

	if (status === "unauthenticated") {
		return <UnauthorizedState message="Please sign in to continue." />;
	}

	return <Outlet />;
}
