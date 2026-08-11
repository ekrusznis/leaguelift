# ADR-112 — Phase 37: mobile Google/Apple sign-in, client half

**Status:** Accepted
**Date:** 2026-08-11

## Context

ADR-111 built the backend half — real Google/Apple ID token verification, conservative account-linking, both endpoints live but fail-closed (503) until Rally26 registers real credentials. This closes the loop on mobile: real buttons on `login.tsx` that retrieve a real provider-signed ID token on-device and hand it to those endpoints.

## Decision

**Google via `expo-auth-session`'s generic `AuthRequest`, not the deprecated `providers/google` wrapper.** `useGoogleIdToken.ts` calls `useAuthRequest` directly against Google's own real discovery document (`useAutoDiscovery('https://accounts.google.com')`), requesting `response_type=id_token` with a per-mount random `nonce` (`expo-crypto`'s `randomUUID()`, generated once via `useMemo` so `useAuthRequest`'s own request memoization stays stable across re-renders). This is a browser-based (`expo-web-browser`) flow, not a native SDK — it works without a custom dev-client rebuild, unlike `@react-native-google-signin/google-signin` would have required. `WebBrowser.maybeCompleteAuthSession()` is called once at module scope in `login.tsx`, required so the browser tab auto-dismisses after Google redirects back into the app via the `rally26://` scheme (`makeRedirectUri()`).

**Apple via the real native `expo-apple-authentication` module**, added to `app.config.ts`'s `plugins` array (applies the "Sign In with Apple" iOS entitlement — a no-op without it, the capability silently fails on a real device). The button only renders when `Platform.OS === 'ios'` AND `AppleAuthentication.isAvailableAsync()` resolves true, matching Apple's own guidance for when the button may appear. Uses the real `AppleAuthenticationButton` component (Apple's own styled button, required by App Store review guideline 4.8) rather than the app's generic `Button`.

**Google is gated client-side; Apple is gated server-side — both surface identically.** Google's OAuth client id (`EXPO_PUBLIC_GOOGLE_OAUTH_CLIENT_ID`) is a real per-app credential that doesn't exist yet, so `onGooglePress` checks `env.googleOAuthClientId` before ever opening the browser and shows a "coming soon" toast (`useToast`, already existing app-wide) if blank — starting a real OAuth flow with an empty client id would either throw or produce a confusing provider-side error. Apple has no equivalent client-side credential (the ID token's audience is the app's own static bundle identifier, `com.rally26.mobile`) — the native flow always runs for real, but the backend still 503s with `APPLE_OAUTH_NOT_CONFIGURED` until Sign in with Apple is registered in Rally26's Apple Developer account, caught via `authApi.isOAuthNotConfiguredError` and shown as the same "coming soon" toast rather than a raw error.

**`AuthContext` gained a shared `establishSession` helper**, extracted from `login`'s existing token/user-write logic, reused by new `loginWithGoogle`/`loginWithApple` methods — all three now write the same SecureStore-backed session shape, so nothing downstream (WebView embed bootstrap, `getMe()` re-hydration on relaunch) needed to change.

**New dependencies** (`npx expo install`, SDK 57-compatible versions resolved automatically): `expo-auth-session@~57.0.6`, `expo-apple-authentication@~57.0.1`, `expo-crypto@~57.0.1`.

**Verification:** `tsc --noEmit`, `expo lint`, and `expo-doctor` (18/18 checks) all clean — same bar as every prior mobile ADR. Not verified on a real device/simulator this pass (no EAS build was triggered) — the "coming soon" gating means neither path can complete an actual sign-in until real credentials exist regardless, so the highest-value verification is Google's fully wired API up to the 503 boundary and Apple's identical fail-closed integration, both of which are exercised by existing OAuthSignInServiceTest coverage on the backend side (ADR-111); a live device pass over the full round-trip is worth doing once real credentials are registered.

## Consequences

- Phase 37's Google/Apple sign-in slice is now fully code-complete end-to-end (backend ADR-111 + client, this ADR) but not yet *usable* — both providers 503 until Rally26 completes real Google Cloud OAuth client registration and Apple Developer "Sign in with Apple" capability/key setup. This is tracked as a real, load-bearing gap, not a stub.
- Once those credentials exist: set `GOOGLE_OAUTH_CLIENT_ID`/`APPLE_OAUTH_CLIENT_ID` on the backend (already wired, ADR-111) and `EXPO_PUBLIC_GOOGLE_OAUTH_CLIENT_ID` in `eas.json`'s preview/production `env` blocks (currently blank placeholders) — no code changes required on either side.
- Web's `SocialAuthButton`/`featureFlags.socialAuthProviders` remain untouched, per the founder's explicit "mobile only, for now" scoping.
