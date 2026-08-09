import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { UserAppearanceSync } from "../features/settings/UserAppearanceSync";

/**
 * Route-level gating is a UX convenience only — it is never the authorization
 * boundary. The backend enforces membership/role checks independently on every
 * request.
 */
export function ProtectedRoute() {
	const { status } = useAuth();
	const location = useLocation();
	if (status === "unauthenticated") {
		const next = `${location.pathname}${location.search}`;
		return <Navigate to={`/auth/sign-in?next=${encodeURIComponent(next)}`} replace />;
	}

	return (
		<>
			<UserAppearanceSync />
			<Outlet />
		</>
	);
}
