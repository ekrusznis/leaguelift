# Security

## Authentication

- Managed OIDC provider (seed design: Auth0). See ADR-002.
- React: Authorization Code Flow with PKCE.
- Backend: Spring Security OAuth2 Resource Server validates JWT signature, issuer,
  audience, and expiration on every request.
- Internal `app_user` records are keyed by the external subject (`sub`), not email.
- MFA is required for platform administrators at the identity-provider level.

### Local development bypass

The `local` Spring profile (`application-local.yml`) enables a fixed internal test
principal so the API can be exercised without a configured OIDC tenant. This is wired
through a `LocalDevAuthenticationFilter` that is `@Profile("local")`-gated at the Spring
bean level — it does not exist as a bean at all outside the `local` profile, so it
cannot be accidentally left "on" in staging/production via configuration alone. The
`prod` and `staging` profiles always require a valid provider-issued JWT.
The frontend mirrors this with `VITE_AUTH_DEV_MODE`, which must never be `true` in a
production build.

## Authorization

- Every protected backend operation checks organization membership and role before
  touching organization-owned data. This check lives in the application-service layer,
  not in controllers, and never in React.
- Platform-administrator access is a distinct permission on the internal `app_user` /
  role model — never inferred from email domain, frontend state, or convention.
- Prefer `404 Not Found` over `403 Forbidden` when revealing that a resource exists
  would itself leak information the caller isn't authorized to have.
- Financial adjustments (future phases) require an elevated permission and produce an
  immutable audit record.

## Public endpoints

Public (unauthenticated) endpoints — e.g. published public pages in later phases —
must still apply input validation, rate limiting, idempotency keys for submissions,
and must never enumerate private records or expose predictable-sequence private IDs.

## Secrets

Never commit: database passwords, OIDC client secrets, Stripe secret/webhook keys,
Printify tokens, email-provider keys, Sentry auth tokens, Cloudflare tokens. Secrets
live in environment variables / a secret manager per environment. `.env.example`
documents required variable names only — never real values.

`VITE_`-prefixed variables are bundled into the public frontend build and must never
contain secrets — only public configuration (API base URL, Auth0 domain/client ID,
feature-flag toggles).

## Webhooks (future phases)

Provider webhooks (Stripe, Printify, email) must validate provider signatures before
processing, store the raw event with a unique `(provider, external_event_id)`
constraint, return promptly, and process asynchronously via the outbox/worker pattern.
No webhook handler may process the same provider event twice.

## Security headers

Production configuration should set: Content-Security-Policy, Strict-Transport-
Security, `X-Content-Type-Options: nosniff`, frame-ancestors restriction, a
Referrer-Policy, a Permissions-Policy, and secure cookies where cookies are used.

## File uploads (future phases)

Use signed upload URLs, restrict MIME types and size, generate unique storage keys,
never trust client-provided filenames, and never allow public overwrite of an existing
object.

## Privacy

See `docs/privacy-data-inventory.md`. Never log access tokens, payment details, full
provider payloads containing personal data, sensitive form contents, child data, or
secrets.
