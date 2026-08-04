# ADR-014: Traditional Password Authentication (Supersedes ADR-002)

## Status
Accepted

## Context

ADR-002 chose a managed OIDC provider (seed design: Auth0) to avoid building and
securing password storage. In practice, no Auth0 tenant was ever created — the
frontend/backend code to call one was written speculatively (`frontend/src/auth/`,
`SecurityConfig`'s OAuth2 resource-server branch), but nothing was ever tested
against a real tenant. Before investing further in that path, the decision was made
to switch to traditional email/password authentication checked against our own
database instead.

## Decision

- Passwords are hashed with bcrypt (`PasswordEncoder` bean, `SecurityConfig.kt`) and
  stored on `app_user.password_hash` (`V8__password_authentication.sql`). Plaintext
  passwords are never logged or persisted.
- `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
  (`identity/web/AuthController.kt`) are the only public, unauthenticated endpoints
  besides `/api/v1/public/**`.
- On success, the backend issues a self-issued JWT (HS256, `JwtConfig.kt` /
  `TokenService.kt`) signed with a shared secret (`rally26.jwt.secret`, from
  `JWT_SECRET` in staging/prod — no default, so a missing secret fails startup
  rather than signing with a guessable key).
- Spring Security's OAuth2 resource-server support still validates every subsequent
  request's JWT (signature, issuer, expiration) — only the decoder's key source
  changed, from an external JWKS/issuer-uri to our own `NimbusJwtDecoder.withSecretKey`.
  `JwtCurrentUserConverter` looks up the `app_user` by the token's `sub` claim (our
  internal user ID) directly — no provisioning-on-first-sight, since the account
  already exists by the time a token can exist.
- `app_user.external_subject` (the OIDC subject-identifier column) was dropped
  entirely, along with `UserProvisioningService` — there is no external identity to
  key off anymore.
- **Update (same rollout):** the `local`/`test` dev-bypass filter
  (`LocalDevAuthenticationFilter`, `AuthProperties`) and the frontend's mirroring
  `VITE_AUTH_DEV_MODE` mock were both removed entirely, not kept alongside real auth.
  Every environment — including local and the test suite — now authenticates the same
  way: a real `POST /api/v1/auth/register` or `/login`. Local/test convenience is
  provided by the seeded dashboard-role accounts
  (`db/seed/V9000__dev_seed_dashboard_role_users.sql`, shared password
  `DevPassword123!`) rather than a parallel fake-identity code path. This was a
  deliberate simplification: two auth paths (real + bypass) is more surface area to
  keep in sync and more risk of the bypass leaking into a real environment than one
  real path plus known test credentials.

## Consequences

- We now own password storage, hashing, and reset flows — the exact liability ADR-002
  was written to avoid. Mitigated by using a well-reviewed hashing algorithm (bcrypt)
  and never inventing a from-scratch crypto scheme.
- No social login, no MFA, no managed breach-detection — all deferred until/unless
  actually needed.
- No password-reset-by-email flow exists yet: this codebase has no outbound email
  sending infrastructure at all (see `DESIGN-DOC.md` section 7.1), so
  `ForgotPasswordPage` remains an honest "not implemented yet" placeholder on the
  frontend rather than a half-built endpoint that can't actually deliver an email.
  Email *verification* was dropped rather than stubbed — there's no verification
  email to check for, so `VerifyEmailPage`/`/auth/verify-email` were removed.
- No external per-environment tenant configuration is needed anymore — only a
  `JWT_SECRET` env var per environment (staging and prod must use different values).
- Removes the `IdentityProvider` abstraction concern from ADR-002 as moot: there is no
  provider to abstract away from anymore.

## Alternatives Considered

- **Keep pursuing Auth0** — rejected for now. Real tenant setup was still pending, and
  newer Auth0 tenants restrict the "Password" grant type needed to keep this app's own
  custom sign-in form (rather than Auth0's hosted Universal Login), adding real risk to
  that path. Revisit if social login, MFA, or reduced in-house security-engineering
  burden become priorities.
- **Session cookies instead of JWTs** — rejected to keep the existing stateless
  (`SessionCreationPolicy.STATELESS`) REST API shape and reuse Spring's already-wired
  OAuth2 resource-server JWT validation machinery with minimal diff.
