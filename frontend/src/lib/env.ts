/**
 * Central place for reading Vite environment configuration. `VITE_`-prefixed
 * variables are bundled into the public client build — never put secrets here
 * (DESIGN-DOC.md section 18.4).
 */
export const env = {
	apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1",
} as const;
