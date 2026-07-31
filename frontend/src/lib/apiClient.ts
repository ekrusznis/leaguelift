import { readStoredSupportAccess } from "../features/platformAdmin/supportAccessStorage";
import { ApiError, type ApiErrorBody } from "./apiError";
import { env } from "./env";

/**
 * Registered by the active auth provider (dev-mode or real OIDC) so the API client
 * can attach a bearer token without importing React/auth concerns directly. Kept as
 * a module-level indirection specifically so this file never needs to know which
 * auth implementation is active (DESIGN-DOC.md section 17.2 — keep API calls in a
 * shared client layer).
 */
let accessTokenGetter: () => Promise<string | null> = async () => null;

const ORGANIZATION_PATH = /^\/organizations\/([0-9a-f-]{36})(?:\/|$)/i;
const RESOURCE_SCOPED_PATH = /^\/(?:teams|tournaments|households|participants|events)\//i;

/**
 * Platform support sessions are sent only to customer-data endpoints. Organization-
 * first routes are matched to the active session directly. Resource-first event and
 * schedule routes carry organizationId in the query string and are validated again
 * by the backend interceptor before any service is called.
 */
function attachSupportAccessHeader(path: string, headers: Record<string, string>) {
	const supportAccess = readStoredSupportAccess();
	if (!supportAccess) return;
	const organizationMatch = ORGANIZATION_PATH.exec(path);
	if (organizationMatch) {
		if (organizationMatch[1]?.toLowerCase() === supportAccess.organizationId.toLowerCase()) {
			headers["X-LeagueLift-Support-Access"] = supportAccess.id;
		}
		return;
	}
	if (!RESOURCE_SCOPED_PATH.test(path)) return;
	const queryOrganizationId = new URL(path, "https://leaguelift.invalid").searchParams.get("organizationId");
	if (queryOrganizationId?.toLowerCase() === supportAccess.organizationId.toLowerCase()) {
		headers["X-LeagueLift-Support-Access"] = supportAccess.id;
	}
}

export function registerAccessTokenGetter(getter: () => Promise<string | null>) {
	accessTokenGetter = getter;
}

export interface RequestOptions {
	method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
	body?: unknown;
	signal?: AbortSignal;
}

/**
 * Thin fetch wrapper. Never invent endpoints here beyond what docs/openapi.yaml
 * defines (DESIGN-DOC.md section 17.2) — this file is transport only, not a place to
 * encode business rules.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
	const token = await accessTokenGetter();
	const headers: Record<string, string> = {
		"Content-Type": "application/json",
	};
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	attachSupportAccessHeader(path, headers);

	const response = await fetch(`${env.apiBaseUrl}${path}`, {
		method: options.method ?? "GET",
		headers,
		body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
		signal: options.signal,
	});

	if (response.status === 204) {
		return undefined as T;
	}

	const contentType = response.headers.get("content-type") ?? "";
	const payload = contentType.includes("application/json") ? await response.json() : undefined;

	if (!response.ok) {
		const errorBody: ApiErrorBody = payload ?? {
			code: "UNKNOWN_ERROR",
			message: "An unexpected error occurred.",
			requestId: "unknown",
			fieldErrors: [],
		};
		throw new ApiError(response.status, errorBody);
	}

	return payload as T;
}

/**
 * Blob-aware sibling of apiFetch, for endpoints that return a file (e.g. CSV export)
 * rather than JSON — still hits our own authenticated API, unlike media's presigned-URL
 * uploads, so this reuses the registered bearer-token getter rather than bypassing it.
 */
export async function apiFetchBlob(path: string, signal?: AbortSignal): Promise<{ blob: Blob; filename: string }> {
	const token = await accessTokenGetter();
	const headers: Record<string, string> = {};
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	attachSupportAccessHeader(path, headers);

	const response = await fetch(`${env.apiBaseUrl}${path}`, { headers, signal });

	if (!response.ok) {
		const contentType = response.headers.get("content-type") ?? "";
		const payload = contentType.includes("application/json") ? await response.json() : undefined;
		const errorBody: ApiErrorBody = payload ?? {
			code: "UNKNOWN_ERROR",
			message: "An unexpected error occurred.",
			requestId: "unknown",
			fieldErrors: [],
		};
		throw new ApiError(response.status, errorBody);
	}

	const disposition = response.headers.get("content-disposition") ?? "";
	const filenameMatch = /filename="?([^";]+)"?/.exec(disposition);
	const filename = filenameMatch?.[1] ?? "export.csv";

	return { blob: await response.blob(), filename };
}
