import { ApiError, apiFetch } from '@/lib/apiClient';

import type { AuthResponse, LoginRequest, OAuthSignInRequest, RegisterRequest, RegistrationAcceptedResponse, UserResponse } from './types';

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/auth/login', { method: 'POST', body: request });
}

/** Phase 37 (ADR-111). 503 with code GOOGLE_OAUTH_NOT_CONFIGURED until Rally26 registers a real Google Cloud OAuth client — see isOAuthNotConfiguredError. */
export function signInWithGoogle(request: OAuthSignInRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/auth/oauth/google', { method: 'POST', body: request });
}

/** Phase 37 (ADR-111). 503 with code APPLE_OAUTH_NOT_CONFIGURED until Rally26 registers Sign in with Apple in its Apple Developer account — see isOAuthNotConfiguredError. */
export function signInWithApple(request: OAuthSignInRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/auth/oauth/apple', { method: 'POST', body: request });
}

/** True only for the fail-closed "credentials not registered yet" case (OAuthSignInService), distinct from a real auth failure — callers show a "coming soon" toast for this, not an error message. */
export function isOAuthNotConfiguredError(error: unknown): boolean {
  return error instanceof ApiError && (error.body.code === 'GOOGLE_OAUTH_NOT_CONFIGURED' || error.body.code === 'APPLE_OAUTH_NOT_CONFIGURED');
}

/** Validates a stored token and re-hydrates the signed-in user on app restart. */
export function getMe(): Promise<UserResponse> {
  return apiFetch<UserResponse>('/me');
}

/** Creates a bare, unverified AppUser only — matches frontend/src/auth/authApi.ts::registerOwner exactly. Organization setup happens later, in-app, after sign-in. */
export function register(request: RegisterRequest): Promise<RegistrationAcceptedResponse> {
  return apiFetch<RegistrationAcceptedResponse>('/auth/register-owner', { method: 'POST', body: request });
}

export function resendVerificationEmail(email: string): Promise<void> {
  return apiFetch<void>('/auth/verify-email/resend', { method: 'POST', body: { email } });
}

/** Maps a failed login/register call to a message safe to show the user (mirrors frontend/src/auth/authApi.ts::messageForAuthError). */
export function messageForAuthError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.body.code === 'INVALID_CREDENTIALS') return 'Incorrect email or password.';
    if (error.body.code === 'EMAIL_NOT_VERIFIED') return 'Verify your email before signing in.';
    if (error.body.code === 'EMAIL_ALREADY_REGISTERED') return 'An account with that email already exists.';
    if (error.body.code === 'OAUTH_TOKEN_INVALID') return 'That sign-in could not be verified. Please try again.';
    if (error.body.code === 'OAUTH_EMAIL_REQUIRED') return 'That account has no email address to sign in with.';
    if (error.body.fieldErrors.length > 0) return error.body.fieldErrors[0].message;
    return error.body.message;
  }
  return 'Something went wrong. Please try again.';
}
