/** Matches backend/src/main/kotlin/com/rally26/identity/web/AuthDto.kt exactly (ADR-102). */

export interface LoginRequest {
  email: string;
  password: string;
}

/** Matches backend's OAuthSignInRequest (ADR-111) — idToken is the Google/Apple-issued ID token from the native sign-in SDK, verified server-side against that provider's own JWKS. */
export interface OAuthSignInRequest {
  idToken: string;
}

export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  status: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserResponse;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  agreeToTerms: boolean;
  confirmAdult: boolean;
}

/** POST /auth/register-owner only creates a bare, unverified AppUser — no session, no organization (matches frontend/src/auth/authApi.ts). */
export interface RegistrationAcceptedResponse {
  email: string;
}
