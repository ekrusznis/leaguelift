import { env } from './env';

/**
 * Mirrors frontend/src/lib/apiClient.ts's shape deliberately — same registered-token-
 * getter indirection, same thin-transport-only boundary (DESIGN-DOC.md §17.2: never
 * invent endpoints here beyond docs/openapi.yaml).
 */
let accessTokenGetter: () => Promise<string | null> = async () => null;

export function registerAccessTokenGetter(getter: () => Promise<string | null>) {
  accessTokenGetter = getter;
}

/**
 * There is no refresh-token endpoint anywhere in the backend (confirmed — see ADR-102).
 * A 401 always means the session is over; AuthContext registers this to force logout
 * and route back to /login, matching the web app's forced-relogin-on-expiry behavior.
 */
let unauthorizedHandler: () => void = () => {};

export function registerUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  requestId: string;
  fieldErrors: FieldError[];
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.message);
    this.name = 'ApiError';
  }
}

const UNKNOWN_ERROR_BODY: ApiErrorBody = {
  code: 'UNKNOWN_ERROR',
  message: 'An unexpected error occurred.',
  requestId: 'unknown',
  fieldErrors: [],
};

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = await accessTokenGetter();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });

  if (response.status === 401) {
    unauthorizedHandler();
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  let payload: unknown = null;
  if (contentType.includes('application/json')) {
    try {
      payload = await response.json();
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, (payload as ApiErrorBody | null) ?? UNKNOWN_ERROR_BODY);
  }

  return payload as T;
}
