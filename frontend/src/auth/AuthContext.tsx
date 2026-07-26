import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { registerAccessTokenGetter } from "../lib/apiClient";
import { env } from "../lib/env";
import type { AuthState, AuthUser } from "./types";

const AuthContext = createContext<AuthState | undefined>(undefined);

const DEV_USER: AuthUser = {
	displayName: "Local Dev User",
	email: "dev@leaguelift.local",
};

/**
 * Provides authentication state to the app. Two implementations live behind this one
 * provider component:
 *
 * - Dev mode (`VITE_AUTH_DEV_MODE=true`, the default outside a real deployment):
 *   an in-memory mock session mirroring the backend's `local`/`test` profile
 *   authentication bypass (see docs/security.md). No network call, no token.
 * - Production mode (`VITE_AUTH_DEV_MODE=false`): intended to wrap the OIDC
 *   provider's SDK (seed design: Auth0 — DESIGN-DOC.md section 11.5) using
 *   Authorization Code Flow with PKCE. That SDK integration is intentionally not
 *   wired up yet because no OIDC tenant is configured (open question, DESIGN-DOC.md
 *   section 33, item 2) — DESIGN-DOC.md section 27.2 says not to add dependencies
 *   before they're needed. Wire it here when a tenant exists, keeping this
 *   component's public shape (`useAuth()` returning `AuthState`) unchanged so the
 *   rest of the app does not need to change.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
	const [status, setStatus] = useState<AuthState["status"]>("loading");
	const [user, setUser] = useState<AuthUser | null>(null);

	useEffect(() => {
		if (env.authDevMode) {
			registerAccessTokenGetter(async () => "dev-mode-no-token-required");
			setUser(DEV_USER);
			setStatus("authenticated");
			return;
		}

		// Production path placeholder: no OIDC tenant is configured yet, so there is
		// nothing to redirect to. Fail safe to "unauthenticated" rather than
		// pretending a session exists.
		registerAccessTokenGetter(async () => null);
		setStatus("unauthenticated");
	}, []);

	const value = useMemo<AuthState>(
		() => ({
			status,
			user,
			login: () => {
				if (env.authDevMode) return;
				throw new Error(
					"Production authentication is not configured yet. Set VITE_AUTH_DEV_MODE=true " +
						"for local development, or configure the OIDC provider (see auth/AuthContext.tsx).",
				);
			},
			logout: () => {
				setUser(null);
				setStatus("unauthenticated");
			},
		}),
		[status, user],
	);

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
	const context = useContext(AuthContext);
	if (!context) {
		throw new Error("useAuth must be used within an AuthProvider");
	}
	return context;
}
