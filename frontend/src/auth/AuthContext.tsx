import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { registerAccessTokenGetter } from "../lib/apiClient";
import * as authApi from "./authApi";
import type { AuthResult, AuthState, AuthUser, RegisterParams } from "./types";

const AuthContext = createContext<AuthState | undefined>(undefined);

interface Session {
	accessToken: string;
	expiresAt: number;
}

/**
 * Provides authentication state to the app. Always calls our own backend's
 * `POST /api/v1/auth/login` / `POST /api/v1/auth/register` (`./authApi`) —
 * traditional email/password authentication against our own database (ADR-014).
 * There is no mock/bypass mode on the frontend: local development authenticates the
 * same way production does, using either a real registration or one of the seeded
 * dashboard-role accounts (`db/seed/V9000__dev_seed_dashboard_role_users.sql`).
 *
 * Session lifetime is the access token's `expiresIn` — there is no persisted session
 * across a page reload and no refresh-token support yet, so a reload requires signing
 * in again.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
	const [status, setStatus] = useState<AuthState["status"]>("unauthenticated");
	const [user, setUser] = useState<AuthUser | null>(null);
	const sessionRef = useRef<Session | null>(null);

	useEffect(() => {
		registerAccessTokenGetter(async () => {
			const session = sessionRef.current;
			if (!session || Date.now() >= session.expiresAt) return null;
			return session.accessToken;
		});
	}, []);

	const establishSession = (response: authApi.AuthApiResponse): AuthResult => {
		sessionRef.current = {
			accessToken: response.accessToken,
			expiresAt: Date.now() + response.expiresIn * 1000,
		};
		setUser({ displayName: response.user.displayName, email: response.user.email });
		setStatus("authenticated");
		return { success: true };
	};

	const value = useMemo<AuthState>(
		() => ({
			status,
			user,
			login: async (email: string, password: string): Promise<AuthResult> => {
				try {
					const response = await authApi.login(email, password);
					return establishSession(response);
				} catch (error) {
					return { success: false, error: authApi.messageForAuthError(error) };
				}
			},
			register: async (params: RegisterParams): Promise<AuthResult> => {
				try {
					const response = await authApi.register(params);
					return establishSession(response);
				} catch (error) {
					return { success: false, error: authApi.messageForAuthError(error) };
				}
			},
			logout: () => {
				sessionRef.current = null;
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
