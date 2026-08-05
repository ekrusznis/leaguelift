import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Route-level gating is a UX convenience only — it is never the authorization
 * boundary. The backend enforces membership/role checks independently on every
 * request (DESIGN-DOC.md sections 7, 18.2), so a user who bypasses this component
 * still cannot read data they aren't authorized for.
 *
 * Used as a layout route (`<Route element={<ProtectedRoute />}>`) so any number of
 * nested child routes can share the same gate via <Outlet />.
 *
 * Redirects to sign-in (carrying the current path as `next`, same convention as
 * VerifyEmailPage/InvitationPage) rather than rendering a static "please sign in"
 * banner in place — found live-browser-testing 2026-08-05: since AuthContext holds no
 * persisted session (see its own doc comment; a reload always starts unauthenticated
 * until real refresh-token support exists), *every* reload/deep-link/sign-out hit this
 * gate, and the old banner had no link back into the app — a real dead end, not just an
 * edge case.
 */
export function ProtectedRoute() {
	const { status } = useAuth();
	const location = useLocation();

	if (status === "unauthenticated") {
		const next = `${location.pathname}${location.search}`;
		return <Navigate to={`/auth/sign-in?next=${encodeURIComponent(next)}`} replace />;
	}

	return <Outlet />;
}
