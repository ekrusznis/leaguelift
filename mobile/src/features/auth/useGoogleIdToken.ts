import * as Crypto from 'expo-crypto';
import { makeRedirectUri, ResponseType, useAuthRequest, useAutoDiscovery } from 'expo-auth-session';
import { useMemo } from 'react';

import { env } from '@/lib/env';

/**
 * Phase 37 (ADR-111) — Google sign-in via expo-auth-session's generic AuthRequest
 * against Google's own real discovery document (not the deprecated
 * `expo-auth-session/providers/google` wrapper), requesting `response_type=id_token`
 * directly. The returned `id_token` is a real Google-signed JWT, verified server-side
 * by OAuthSignInService against Google's JWKS — this hook never trusts the token
 * itself, it only retrieves it.
 *
 * A random `nonce` is included and must be generated once per mount (not per prompt)
 * so `useAuthRequest`'s internal request memoization stays stable; Google echoes it
 * back inside the verified ID token's own `nonce` claim.
 */
export function useGoogleIdToken() {
  const discovery = useAutoDiscovery('https://accounts.google.com');
  const nonce = useMemo(() => Crypto.randomUUID(), []);

  const [request, response, promptAsync] = useAuthRequest(
    {
      clientId: env.googleOAuthClientId,
      redirectUri: makeRedirectUri(),
      responseType: ResponseType.IdToken,
      scopes: ['openid', 'email', 'profile'],
      usePKCE: false,
      extraParams: { nonce },
    },
    discovery,
  );

  return { request, response, promptAsync };
}
