import * as SecureStore from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';

import { registerAccessTokenGetter, registerUnauthorizedHandler } from '@/lib/apiClient';

import {
  getMe,
  login as loginRequest,
  register as registerRequest,
  signInWithApple as signInWithAppleRequest,
  signInWithGoogle as signInWithGoogleRequest,
} from './authApi';
import type { AuthResponse, RegisterRequest, RegistrationAcceptedResponse, UserResponse } from './types';

const TOKEN_KEY = 'rally26.accessToken';
const EXPIRES_AT_KEY = 'rally26.expiresAt';
const USER_KEY = 'rally26.user';

/**
 * There is no refresh-token endpoint anywhere in the backend (ADR-102) — a session is
 * exactly the access token's expiresIn (~60 min by default), same as the web app
 * (frontend/src/auth/AuthContext.tsx). No silent refresh; expiry or any 401 forces
 * back to /login. Uses expo-secure-store (Keychain/Keystore-backed), not AsyncStorage
 * — this is a credential, not app preference data.
 */
interface AuthState {
  user: UserResponse | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  /** Phase 37 (ADR-111) — verifies a Google-issued ID token server-side and establishes a session exactly like login(). Throws ApiError (including GOOGLE_OAUTH_NOT_CONFIGURED — see authApi.isOAuthNotConfiguredError) on failure. */
  loginWithGoogle: (idToken: string) => Promise<void>;
  /** Same as loginWithGoogle, for an Apple-issued ID token (APPLE_OAUTH_NOT_CONFIGURED when not yet registered). */
  loginWithApple: (idToken: string) => Promise<void>;
  /** Creates a bare, unverified AppUser only — no session is established, matching web's register() contract. Throws ApiError on failure, same convention as login(). */
  register: (request: RegisterRequest) => Promise<RegistrationAcceptedResponse>;
  logout: () => Promise<void>;
  /**
   * Returns the current session in the exact JSON shape
   * frontend/src/auth/AuthContext.tsx reads from sessionStorage (key
   * "rally26.session": {accessToken, expiresAt, user: {displayName, email}}) — used to
   * bootstrap an authenticated WebView embed (ADR-106) via
   * injectedJavaScriptBeforeContentLoaded, with no new backend endpoint. Web's
   * apiClient sends no cookie/CSRF token, so the bearer token is the only thing that
   * gates access — verified directly against frontend/src/auth/AuthContext.tsx and
   * frontend/src/lib/apiClient.ts before relying on this.
   */
  getWebSession: () => { accessToken: string; expiresAt: number; user: { displayName: string; email: string } } | null;
}

const AuthContext = createContext<AuthState | null>(null);

async function readStoredSession(): Promise<{ token: string; expiresAt: number; user: UserResponse } | null> {
  const [token, expiresAtRaw, userRaw] = await Promise.all([
    SecureStore.getItemAsync(TOKEN_KEY),
    SecureStore.getItemAsync(EXPIRES_AT_KEY),
    SecureStore.getItemAsync(USER_KEY),
  ]);
  if (!token || !expiresAtRaw || !userRaw) return null;
  const expiresAt = Number(expiresAtRaw);
  if (!Number.isFinite(expiresAt) || Date.now() >= expiresAt) return null;
  try {
    return { token, expiresAt, user: JSON.parse(userRaw) as UserResponse };
  } catch {
    return null;
  }
}

async function writeStoredSession(token: string, expiresAt: number, user: UserResponse): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(TOKEN_KEY, token),
    SecureStore.setItemAsync(EXPIRES_AT_KEY, String(expiresAt)),
    SecureStore.setItemAsync(USER_KEY, JSON.stringify(user)),
  ]);
}

async function clearStoredSession(): Promise<void> {
  await Promise.all([
    SecureStore.deleteItemAsync(TOKEN_KEY),
    SecureStore.deleteItemAsync(EXPIRES_AT_KEY),
    SecureStore.deleteItemAsync(USER_KEY),
  ]);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const tokenRef = useRef<{ token: string; expiresAt: number } | null>(null);

  const logout = useCallback(async () => {
    tokenRef.current = null;
    await clearStoredSession();
    setUser(null);
  }, []);

  function getWebSession() {
    const current = tokenRef.current;
    if (!current || !user || Date.now() >= current.expiresAt) return null;
    return { accessToken: current.token, expiresAt: current.expiresAt, user: { displayName: user.displayName, email: user.email } };
  }

  useEffect(() => {
    registerAccessTokenGetter(async () => {
      const current = tokenRef.current;
      if (!current || Date.now() >= current.expiresAt) return null;
      return current.token;
    });
    registerUnauthorizedHandler(() => {
      void logout();
    });
  }, [logout]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const stored = await readStoredSession();
      if (cancelled) return;
      if (stored) {
        tokenRef.current = { token: stored.token, expiresAt: stored.expiresAt };
        setUser(stored.user);
        // Validate the token is still accepted server-side (e.g. account status
        // changed since last launch) rather than trusting the local clock alone.
        try {
          const freshUser = await getMe();
          if (!cancelled) setUser(freshUser);
        } catch {
          if (!cancelled) await logout();
        }
      }
      if (!cancelled) setIsLoading(false);
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const establishSession = useCallback(async (response: AuthResponse) => {
    const expiresAt = Date.now() + response.expiresIn * 1000;
    tokenRef.current = { token: response.accessToken, expiresAt };
    await writeStoredSession(response.accessToken, expiresAt, response.user);
    setUser(response.user);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      await establishSession(await loginRequest({ email, password }));
    },
    [establishSession],
  );

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      await establishSession(await signInWithGoogleRequest({ idToken }));
    },
    [establishSession],
  );

  const loginWithApple = useCallback(
    async (idToken: string) => {
      await establishSession(await signInWithAppleRequest({ idToken }));
    },
    [establishSession],
  );

  const register = useCallback((request: RegisterRequest) => registerRequest(request), []);

  return (
    <AuthContext.Provider value={{ user, isLoading, login, loginWithGoogle, loginWithApple, register, logout, getWebSession }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider.');
  return context;
}
