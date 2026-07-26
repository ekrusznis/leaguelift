/**
 * Central place for reading Vite environment configuration. `VITE_`-prefixed
 * variables are bundled into the public client build — never put secrets here
 * (DESIGN-DOC.md section 18.4).
 */
export const env = {
	apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1",
	authDevMode: (import.meta.env.VITE_AUTH_DEV_MODE ?? "true") === "true",
	authDomain: import.meta.env.VITE_AUTH_DOMAIN ?? "",
	authClientId: import.meta.env.VITE_AUTH_CLIENT_ID ?? "",
	authAudience: import.meta.env.VITE_AUTH_AUDIENCE ?? "",
} as const;
