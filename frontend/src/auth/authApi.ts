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
		avatarUrl: string | null;
		avatarSeed: string;
		avatarStyle: string;
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
	/** Set when registration was reached from an invitation-accept link, so the verification email can redirect back to it. */
	invitationToken?: string;
	agreeToTerms: boolean;
	confirmAdult: boolean;
	/** Set when registration was reached from a founding-organization join link (`/founding-organizations/join?code=`). */
	foundingPromoCode?: string;
}

export interface RegistrationAcceptedResponse {
	email: string;
}

export interface InvitationAcceptanceResponse {
	id: string;
	organizationId: string;
	email: string;
	role: string;
	status: string;
	expiresAt: string;
	createdAt: string;
}

export function registerOwner(params: RegisterParams): Promise<RegistrationAcceptedResponse> {
	return apiFetch<RegistrationAcceptedResponse>("/auth/register-owner", { method: "POST", body: params });
}

export function verifyEmail(token: string): Promise<void> {
	return apiFetch<void>("/auth/verify-email", { method: "POST", body: { token } });
}

export function resendVerificationEmail(email: string): Promise<void> {
	return apiFetch<void>("/auth/verify-email/resend", { method: "POST", body: { email } });
}

export function requestPasswordReset(email: string): Promise<void> {
	return apiFetch<void>("/auth/password-reset/request", { method: "POST", body: { email } });
}

export function completePasswordReset(token: string, password: string): Promise<void> {
	return apiFetch<void>("/auth/password-reset/complete", { method: "POST", body: { token, password } });
}

export function acceptInvitation(token: string): Promise<InvitationAcceptanceResponse> {
	return apiFetch<InvitationAcceptanceResponse>(`/invitations/${token}/accept`, { method: "POST" });
}

export interface HouseholdInvitationAcceptanceResponse {
	id: string;
	organizationId: string;
	householdId: string;
	kind: "GUARDIAN" | "ATHLETE";
	participantId: string;
	email: string;
	status: string;
	expiresAt: string;
	createdAt: string;
}

export function acceptHouseholdInvitation(token: string): Promise<HouseholdInvitationAcceptanceResponse> {
	return apiFetch<HouseholdInvitationAcceptanceResponse>(`/household-invitations/${token}/accept`, { method: "POST" });
}

export interface OwnershipTransferInvitationAcceptanceResponse {
	id: string;
	organizationId: string;
	email: string;
	status: string;
	expiresAt: string;
	createdAt: string;
}

export function acceptOwnershipTransferInvitation(token: string): Promise<OwnershipTransferInvitationAcceptanceResponse> {
	return apiFetch<OwnershipTransferInvitationAcceptanceResponse>(`/ownership-transfer-invitations/${token}/accept`, { method: "POST" });
}

/** Maps a failed login/register call to a message safe to show the user. */
export function messageForAuthError(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.code === "INVALID_CREDENTIALS") return "Incorrect email or password.";
		if (error.code === "EMAIL_NOT_VERIFIED") return "Verify your email before signing in.";
		if (error.code === "EMAIL_ALREADY_REGISTERED") return "An account with that email already exists.";
		if (error.fieldErrors.length > 0) return error.fieldErrors[0].message;
		return error.message;
	}
	return "Something went wrong. Please try again.";
}

export function messageForInvitationError(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.status === 401) return "Sign in first to accept this invitation.";
		if (error.code === "INVITATION_EMAIL_MISMATCH") return "This invitation was sent to a different email address.";
		if (error.code === "INVITATION_NOT_FOUND") return "This invitation link is invalid or has already been rotated.";
		return error.message;
	}
	return "Could not accept this invitation. Please try again.";
}

export function messageForHouseholdInvitationError(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.status === 401) return "Sign in first to accept this invitation.";
		if (error.code === "HOUSEHOLD_INVITATION_EMAIL_MISMATCH") return "This invitation was sent to a different email address.";
		if (error.code === "HOUSEHOLD_INVITATION_NOT_FOUND") return "This invitation link is invalid.";
		return error.message;
	}
	return "Could not accept this invitation. Please try again.";
}

export function messageForOwnershipTransferInvitationError(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.status === 401) return "Sign in first to accept this invitation.";
		if (error.code === "OWNERSHIP_TRANSFER_INVITATION_EMAIL_MISMATCH") return "This invitation was sent to a different email address.";
		if (error.code === "OWNERSHIP_TRANSFER_INVITATION_NOT_FOUND") return "This invitation link is invalid.";
		return error.message;
	}
	return "Could not accept this invitation. Please try again.";
}

export function messageForPasswordResetError(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.code === "PASSWORD_RESET_TOKEN_INVALID") return "This reset link is invalid or has expired.";
		if (error.fieldErrors.length > 0) return error.fieldErrors[0].message;
		return error.message;
	}
	return "Could not reset your password. Please try again.";
}

