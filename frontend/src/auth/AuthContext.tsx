import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { registerAccessTokenGetter } from "../lib/apiClient";
import * as authApi from "./authApi";
import type { AuthResult, AuthState, AuthUser, RegisterParams } from "./types";

const AuthContext = createContext<AuthState | undefined>(undefined);

interface Session {
	accessToken: string;
	expiresAt: number;
	user: AuthUser;
}

const SESSION_STORAGE_KEY = "rally26.session";

/**
 * Persisted only for the current browser tab/window's lifetime (sessionStorage, not
 * localStorage) — a still-valid access token survives a page reload or a link opened
 * in the same tab, but signing out of the browser entirely still requires signing back
 * in, and the token is never sent anywhere it wasn't already going as a bearer header.
 */
function readStoredSession(): Session | null {
	try {
		const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
		if (!raw) return null;
		const parsed = JSON.parse(raw) as Session;
		if (!parsed.accessToken || !parsed.expiresAt || Date.now() >= parsed.expiresAt) {
			window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
			return null;
		}
		return parsed;
	} catch {
		window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
		return null;
	}
}

function writeStoredSession(session: Session) {
	window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
}

function clearStoredSession() {
	window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
}

/**
 * Provides authentication state to the app. Always calls our own backend's
 * `POST /api/v1/auth/login` / `POST /api/v1/auth/register-owner` (`./authApi`) —
 * traditional email/password authentication against our own database (ADR-014).
 * There is no mock/bypass mode on the frontend: local development authenticates the
 * same way production does, using either a real registration or one of the seeded
 * dashboard-role accounts (`db/seed/V9000__dev_seed_dashboard_role_users.sql`).
 *
 * Session lifetime is the access token's `expiresIn`. The token is cached in
 * `sessionStorage` so a page reload or a link opened in the same tab restores an
 * already-valid session instead of forcing sign-in again; an expired or missing token
 * still requires signing in, and there is no refresh-token support yet.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
	const initialSession = useRef(readStoredSession()).current;
	const [status, setStatus] = useState<AuthState["status"]>(initialSession ? "authenticated" : "unauthenticated");
	const [user, setUser] = useState<AuthUser | null>(initialSession?.user ?? null);
	const sessionRef = useRef<Session | null>(initialSession);

	useEffect(() => {
		registerAccessTokenGetter(async () => {
			const session = sessionRef.current;
			if (!session || Date.now() >= session.expiresAt) return null;
			return session.accessToken;
		});
	}, []);

	const establishSession = (response: authApi.AuthApiResponse): AuthResult => {
		const authUser: AuthUser = {
			displayName: response.user.displayName,
			email: response.user.email,
			avatarUrl: response.user.avatarUrl,
			avatarSeed: response.user.avatarSeed,
			avatarStyle: response.user.avatarStyle,
		};
		const session: Session = {
			accessToken: response.accessToken,
			expiresAt: Date.now() + response.expiresIn * 1000,
			user: authUser,
		};
		sessionRef.current = session;
		writeStoredSession(session);
		setUser(authUser);
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
					await authApi.registerOwner(params);
					return { success: true };
				} catch (error) {
					return { success: false, error: authApi.messageForAuthError(error) };
				}
			},
			logout: () => {
				sessionRef.current = null;
				clearStoredSession();
				setUser(null);
				setStatus("unauthenticated");
			},
			updateAvatar: (avatar) => {
				setUser((current) => {
					if (!current) return current;
					const next = { ...current, ...avatar };
					const session = sessionRef.current;
					if (session) {
						const updatedSession = { ...session, user: next };
						sessionRef.current = updatedSession;
						writeStoredSession(updatedSession);
					}
					return next;
				});
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
