# ADR-109 — Mobile owner registration/onboarding entry point

**Status:** Accepted
**Date:** 2026-08-11

## Context

The mobile app's sign-in screen had no path to create a new account — it went straight to `/login` with no sign-up link, dead or otherwise. The founder asked for this to be added before the next round of mobile role-parity verification and APK generation.

## Decision

**Mirrored web's exact registration contract rather than inventing a new one.** `frontend/src/auth/authApi.ts::registerOwner` calls `POST /auth/register-owner`, which — confirmed directly against `backend/src/main/kotlin/com/rally26/identity/web/AuthDto.kt`'s `RegisterRequest` — only ever creates a bare, unverified `AppUser` (`firstName`, `lastName`, `email`, `password`, `agreeToTerms`, `confirmAdult`). It never creates an organization or establishes a session; that happens later, in-app, after sign-in and email verification. Mobile's new `mobile/src/features/auth/authApi.ts::register` calls the identical endpoint with the identical field set and validation (password minimum 8 characters, both boolean fields required true).

**Email verification stays link-based, matching web.** There is no in-app verification-code entry anywhere on web to mirror — verification is a link emailed to the user. Mobile's new `register.tsx` screen shows a "Check your email" confirmation step after successful registration (with a resend action via `POST /auth/verify-email/resend`) and a "Go to Sign In" button, rather than attempting an in-app code-entry flow or a deep-linked verification handoff that doesn't exist on web either.

**`AuthContext`'s `register` method follows mobile's existing throw/catch convention**, not web's `{success, error}` return-value convention — mobile's `login` already throws on failure and lets the caller catch it (`login.tsx`'s `onSubmit`); `register` was added the same way rather than introducing a second error-handling convention into the same file.

**Built:** `mobile/src/app/register.tsx` (new screen — name/email/password/confirm-password fields, two plain-checkbox affordances built inline since no shared Checkbox component exists yet, matching mobile's existing "no react-hook-form/zod" convention — that library pair is web-only in this codebase), a "Don't have an account? Create one" link added to `login.tsx`, and `register`/`resendVerificationEmail`/`messageForAuthError` added to `mobile/src/features/auth/authApi.ts` and `AuthContext.tsx`.

**Verification:** typecheck/lint clean.

## Consequences

- Committed directly to `main`, not a feature branch — consistent with every other mobile persona/UI commit in this effort; unrelated in scope from the concurrent Phase 31 work on `feature/eligibility_waivers`.
- Organization setup after a new owner registers and verifies still only exists on web (`/app/organizations`) — mobile does not yet have an in-app "create your organization" flow. Registering a new owner account on mobile still requires finishing setup on the web app before the account is useful, a real, acknowledged gap worth a future slice if the founder wants a fully mobile-only onboarding path.
