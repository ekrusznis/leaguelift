export interface AuthUser {
	displayName: string;
	email: string;
}

export interface AuthState {
	status: "loading" | "authenticated" | "unauthenticated";
	user: AuthUser | null;
	login: () => void;
	logout: () => void;
}
