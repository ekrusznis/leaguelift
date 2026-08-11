/** Matches backend/src/main/kotlin/com/rally26/identity/web/AuthDto.kt exactly (ADR-102). */

export interface LoginRequest {
  email: string;
  password: string;
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
