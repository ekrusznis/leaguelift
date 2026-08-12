# ADR-111 — Phase 37: mobile Google/Apple sign-in, backend half

**Status:** Accepted
**Date:** 2026-08-11

## Context

The founder asked for Google/Apple sign-in on mobile so users aren't forced through password registration. Scoped via explicit question: Google + Apple only (no Microsoft), mobile only for now — web's existing `SocialAuthButton`/`featureFlags.socialAuthProviders` stays feature-flagged off and untouched.

This is **not** a reversal of ADR-014 (traditional password authentication). It's an additional way to *reach* the same session Rally26 already issues. The mobile app's native Google/Apple SDK authenticates the user on-device and hands the client a provider-signed ID token; there is no redirect flow and no external session — this backend only has to verify that token and then issue Rally26's own JWT via the existing `TokenService`, exactly like password login does.

## Decision

**Verification, not delegation.** `spring-boot-starter-oauth2-resource-server` (already a dependency, already used for our own HS256 tokens — see `JwtConfig`) provides `NimbusJwtDecoder.withJwkSetUri(...)` for free. `OAuthJwtDecoderConfig` adds two more `JwtDecoder` beans pointed at each provider's own public JWKS (Google: `https://www.googleapis.com/oauth2/v3/certs`, issuer `https://accounts.google.com`; Apple: `https://appleid.apple.com/auth/keys`, issuer `https://appleid.apple.com`). `OAuthSignInService` decodes the client-supplied token against the matching decoder, then independently checks audience against our own configured client id — the decoder only proves the signature and issuer, not that the token was minted for *us*.

**`@Primary` on the original JWT decoder.** Adding two more `JwtDecoder` beans broke `SecurityConfig`'s `oauth2ResourceServer { oauth2.jwt { ... } }`, which resolves its decoder by type with no qualifier — the full backend test suite (38 integration tests) failed on boot with `NoUniqueBeanDefinitionException` until `JwtConfig.jwtDecoder()` was marked `@Primary`. This is the decoder that authenticates every real request via our own bearer token, so it stays the implicit default; the two new provider decoders are only ever reached explicitly, via `@Qualifier` in `OAuthSignInService`.

**Account-linking is deliberately conservative (pre-hijacking prevention).** A provider sign-in never blindly attaches to any account sharing its verified email:
- No existing account with that email → create a brand-new `ACTIVE` account, no password hash.
- Existing account is `PENDING_EMAIL_VERIFICATION` (password set, never verified) → the provider "claims" it: `AppUserRepository.claimViaProvider` invalidates the old password hash and activates the account. The provider just proved real ownership of the email more strongly than an unverified password ever did, so the old (possibly attacker-set) password can't be trusted to survive.
- Existing account is already `ACTIVE` → `linkProvider` adds the provider as a second sign-in method; password and status untouched.
- `SUSPENDED` accounts are never reachable via a newly-linked provider (`requireSignable`).

A returning provider identity (already linked) skips the email-lookup path entirely and signs in directly by `(provider, provider_subject)`.

**Fail-closed on missing credentials, matching the existing integration pattern.** `GoogleOAuthProperties`/`AppleOAuthProperties` default their client id to `""` (same blank-default pattern as `TwilioProperties`/`ResendProperties`); the app always starts. `OAuthSignInService.signIn` throws `ServiceUnavailableException` (`GOOGLE_OAUTH_NOT_CONFIGURED`/`APPLE_OAUTH_NOT_CONFIGURED`) if the relevant client id is blank at call time — same shape as `TEAMSNAP_CLIENT_NOT_ACTIVATED`/`GOOGLE_CALENDAR_CLIENT_NOT_ACTIVATED`. Rally26 does not yet have a real Google Cloud OAuth client or Apple Developer "Sign in with Apple" registration; both `GOOGLE_OAUTH_CLIENT_ID`/`APPLE_OAUTH_CLIENT_ID` are blank in every environment today, so both endpoints currently 503. This is a real, load-bearing gap, not a stub — see DESIGN-DOC.md's credentials tracking.

**New endpoints:** `POST /auth/oauth/google`, `POST /auth/oauth/apple`, both public (`/api/v1/auth/**` already `permitAll` in `SecurityConfig`), both accepting `{ idToken }` and returning the same `AuthResponse` shape password login does.

**Migration `V78__oauth_sign_in.sql`** adds `provider`/`provider_subject` to `app_user` (nullable, checked to be both-or-neither, unique together when present).

## Consequences

- Backend is code-complete, ktlint-clean, full suite green (927 tests). Mobile-side work (native `expo-auth-session`/`expo-apple-authentication` deps, login screen buttons, `AuthContext` wiring, "coming soon" gating when unconfigured) is a separate, not-yet-started slice.
- Until Rally26 registers real Google Cloud/Apple Developer credentials, both endpoints are live but fail closed with a clear 503 — mobile's UI must treat that as "coming soon," not a bug, per the founder's own established gating pattern this phase.
- Web's OAuth buttons remain intentionally untouched; this ADR does not reactivate them.
