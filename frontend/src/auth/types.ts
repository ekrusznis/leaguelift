export interface AuthUser {
	displayName: string;
	email: string;
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
}

export interface AuthState {
	status: "authenticated" | "unauthenticated";
	user: AuthUser | null;
	login: (email: string, password: string) => Promise<AuthResult>;
	register: (params: RegisterParams) => Promise<AuthResult>;
	logout: () => void;
}
