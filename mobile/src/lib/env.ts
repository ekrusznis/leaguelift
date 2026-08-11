/**
 * EXPO_PUBLIC_-prefixed vars are inlined into the JS bundle at build time (Expo's
 * built-in .env support) — mirrors how frontend/src/lib/env.ts reads Vite's
 * import.meta.env.VITE_* vars. Per-build-profile values are set in eas.json's
 * "env" block for development/preview/production so a preview build never points
 * at production data and a production build is never accidentally built against
 * a developer's local backend.
 */
function required(name: string, value: string | undefined): string {
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}. Check eas.json / .env.local.`);
  }
  return value;
}

export const env = {
  apiBaseUrl: required('EXPO_PUBLIC_API_BASE_URL', process.env.EXPO_PUBLIC_API_BASE_URL),
  /** Base origin of frontend/ (matches backend's rally26.frontend.base-url) — WebView embeds (Swag Shop/Fundraising/Sponsorships, ADR-106) load real frontend/ pages under this origin. */
  frontendBaseUrl: required('EXPO_PUBLIC_FRONTEND_BASE_URL', process.env.EXPO_PUBLIC_FRONTEND_BASE_URL),
  environmentName: process.env.EXPO_PUBLIC_ENVIRONMENT_NAME ?? 'development',
  /**
   * Phase 37 (ADR-111) — Google/Apple sign-in. Deliberately optional, unlike the vars
   * above: Rally26 has not yet registered a real Google Cloud OAuth client, so this is
   * blank in every environment today. Blank means "not configured yet," not a build
   * error — login.tsx shows a "coming soon" toast instead of starting the native flow.
   * Must match the backend's own GOOGLE_OAUTH_CLIENT_ID (rally26.oauth.google.client-id)
   * so the ID token's audience passes OAuthSignInService's verification.
   */
  googleOAuthClientId: process.env.EXPO_PUBLIC_GOOGLE_OAUTH_CLIENT_ID ?? '',
};
