import { apiFetch } from '@/lib/apiClient';

import type { AuthResponse, LoginRequest, UserResponse } from './types';

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/auth/login', { method: 'POST', body: request });
}

/** Validates a stored token and re-hydrates the signed-in user on app restart. */
export function getMe(): Promise<UserResponse> {
  return apiFetch<UserResponse>('/me');
}
