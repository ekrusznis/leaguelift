import { apiFetch } from "../lib/apiClient";
import { ApiError } from "../lib/apiError";

/** Matches `identity.web.AuthResponse` (docs/openapi.yaml). */
export interface AuthApiResponse {
	accessToken: string;
	tokenType: string;
	expiresIn: number;
	user: {
		id: string;
		email: string;
		displayName: string;
		status: string;
	};
}

export function login(email: string, password: string): Promise<AuthApiResponse> {
	return apiFetch<AuthApiResponse>("/auth/login", { method: "POST", body: { email, password } });
}

export interface RegisterParams {
	firstName: string;
	lastName: string;
	email: string;
	password: string;
}

export function register(params: RegisterParams): Promise<AuthApiResponse> {
	return apiFetch<AuthApiResponse>("/auth/register", { method: "POST", body: params });
}

/** Maps a failed login/register call to a message safe to show the user. */
export function messageForAuthError(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.code === "INVALID_CREDENTIALS") return "Incorrect email or password.";
		if (error.code === "EMAIL_ALREADY_REGISTERED") return "An account with that email already exists.";
		if (error.fieldErrors.length > 0) return error.fieldErrors[0].message;
		return error.message;
	}
	return "Something went wrong. Please try again.";
}
