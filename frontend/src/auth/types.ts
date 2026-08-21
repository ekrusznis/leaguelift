export interface AuthUser {
	displayName: string;
	email: string;
	/** Freshly-signed GET URL for an uploaded photo, or null if the account uses the generated fallback avatar. Short-lived (~15 min) — re-fetched via `useMyAvatar()` rather than cached long-term. */
	avatarUrl: string | null;
	/** Stable identity for the generated fallback avatar (defaults to the account's own id server-side, but can be re-rolled). */
	avatarSeed: string;
	avatarStyle: string;
}

export interface AuthResult {
	success: boolean;
	error?: string;
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

export interface AuthState {
	status: "authenticated" | "unauthenticated";
	user: AuthUser | null;
	login: (email: string, password: string) => Promise<AuthResult>;
	register: (params: RegisterParams) => Promise<AuthResult>;
	logout: () => void;
	/** Patches the signed-in user's avatar fields in local/session state after a successful upload/randomize/remove, so the nav bar updates without a full re-login. */
	updateAvatar: (avatar: Pick<AuthUser, "avatarUrl" | "avatarSeed" | "avatarStyle">) => void;
}
