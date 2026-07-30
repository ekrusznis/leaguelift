# ADR-036: Phase 13 Slice 1 — Security Hardening

## Status
Accepted

## Context

ADR-035 scoped Phase 13 Slice 1 to three concrete, code-level items: missing
HTTP security headers, finalizing the secrets audit a founder request during
Phase 8 explicitly deferred to "a later phase," and a 404-vs-403
information-leak review. `SecurityConfig.kt` was confirmed to set no security
headers at all prior to this slice — no CSP, HSTS, X-Content-Type-Options,
frame restriction, Referrer-Policy, or Permissions-Policy.

## Decision

**1. Security headers added to `SecurityConfig.filterChain` via Spring
Security's `.headers { }` DSL:**
- `X-Content-Type-Options: nosniff` (`contentTypeOptions`)
- `X-Frame-Options: DENY` (`frameOptions`) — this API never needs to be
  framed by anything, including its own frontend
- `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`
  (`httpStrictTransportSecurity`) — only ever sent by Spring Security over an
  already-HTTPS request, so this is a no-op locally and active in
  staging/prod behind TLS termination
- `Referrer-Policy: strict-origin-when-cross-origin` (`referrerPolicy`)
- `Permissions-Policy: geolocation=(), camera=(), microphone=(), payment=(),
  usb=()` (`permissionsPolicyHeader` — note: `HeadersConfigurer.
  permissionsPolicy(...)` returns `PermissionsPolicyConfig`, not
  `HeadersConfigurer`, so it can't be chained further; `permissionsPolicyHeader`
  is the chainable equivalent and is what's actually used here)
- `Content-Security-Policy: default-src 'self'; script-src 'self'
  'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;
  connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src
  'none'` (`contentSecurityPolicy`)

The CSP keeps `'unsafe-inline'` for script/style because springdoc's bundled
Swagger UI (`/swagger-ui/**`, permitAll) is the only HTML this service ever
renders and needs its own inline bootstrap script — every other response is
JSON, which browsers never execute as script/style regardless of this header.
`default-src 'self'` plus `frame-ancestors 'none'`/`object-src 'none'` still
blocks any third-party script, frame, or plugin injection on that one page,
which is the header's real defense-in-depth value here.

**2. Secrets audit finalized — `.env.example` brought to full parity with
actual `${VAR}` references across `application-{local,staging,prod}.yml`.**
Cross-checking every `${...}` placeholder in the yml files against
`.env.example` found 14 real config variables that existed and worked
(Phase 5's platform economics, Phase 6's sponsorship renewal reminder, Phase 8
slice 1's outbox worker, Phase 8 slice 2's fee payment reminder) but were
never documented in `.env.example`: `PLATFORM_FEE_BASIS_POINTS`,
`PAYOUT_HOLDING_PERIOD_DAYS`, `SPONSORSHIP_RENEWAL_REMINDER_{ENABLED,
DAYS_BEFORE,CRON}`, `FEE_PAYMENT_REMINDER_{ENABLED,DAYS_BEFORE,CRON}`, and
`OUTBOX_WORKER_{ENABLED,POLL_INTERVAL_MS,BATCH_SIZE,MAX_ATTEMPTS,
BACKOFF_BASE_SECONDS,BACKOFF_CAP_SECONDS}`. All 14 are non-secret operational
tunables (safe numeric/boolean/cron defaults, not credentials), added to
`.env.example` with the same commented style as the rest of the file. No
plaintext secrets were found anywhere in the repo (checked for Stripe live-mode
keys, AWS access-key patterns, PEM private keys, and Twilio-style tokens —
none present); `gitleaks` already runs this same class of check in CI on every
PR (`.github/workflows/security.yml`). `application-prod.yml`/
`application-staging.yml` require every real secret via `${VAR}` with no
default — startup fails closed if unset, rather than silently falling back to
a blank or dev value.

**3. 404-vs-403 review — audited, no code changes needed.** Spot-checked
every `ForbiddenException` throw site across the application-service layer
(18 sites: `AuthorizationService`, `CoachDashboardService`,
`ParentDashboardService`, `DocumentService`, `EventRsvpService`,
`EventService`, `InvitationService`, `MembershipService`, `SearchService`).
The codebase already follows a consistent, correct posture:
- Resource-scoped lookups (`eventRepository.findById(id, organizationId)`,
  `householdRepository.findById(...)`, etc.) are always filtered by
  `organizationId` in the query itself, so a resource ID that belongs to a
  *different* organization simply isn't found — a 404, indistinguishable
  from an ID that doesn't exist at all. `ForbiddenException` (403) is only
  reached after that lookup succeeds, meaning it only ever fires for a
  resource the caller has already confirmed exists in an org context they're
  already engaging with.
- Organization-level membership (`MembershipService.requireActiveMembership`)
  returns the *same* 403 (`ORGANIZATION_ACCESS_DENIED`) whether the
  `organizationId` in the URL doesn't exist at all or exists but the caller
  isn't a member — never a 404 for one case and 403 for the other, so
  organization existence itself isn't leaked either.
- `GlobalExceptionHandler` never surfaces exception class names, messages, or
  stack traces for unmapped exceptions (`handleUnexpected`) or malformed
  request bodies (`handleMalformedBody`) — those go to logs keyed by
  `requestId` only.

No inconsistent ordering (capability check before existence check in a way
that would leak cross-org existence) was found. This item closes as a
verification pass with no defects, not a no-op — the pattern is real and
deliberate but wasn't previously written down as a checked invariant.

## Consequences

- Every response now carries the six headers above; Swagger UI at
  `/swagger-ui/**` was manually confirmed to still render and execute after
  the CSP change (springdoc's bundled inline script is same-origin and
  `'unsafe-inline'`-permitted, so nothing broke).
- `.env.example` is now a complete reference for every environment variable
  the backend actually reads — a new contributor setting up local dev no
  longer has to grep `application-local.yml` to discover tunables like the
  outbox worker's poll interval or the fee reminder's lead time.
- The 404-vs-403 posture is now an explicitly documented, verified invariant
  rather than an implicit accident of how `findById(id, organizationId)` calls
  happened to be written — future PRs touching authorization can be checked
  against this ADR instead of re-deriving the reasoning.

## Alternatives Considered

- **Stricter CSP with no `'unsafe-inline'`, using a nonce or hash for
  Swagger UI's inline script**: rejected for this slice — springdoc's
  bundled `index.html` isn't controlled by this codebase, so a nonce would
  require either forking/templating springdoc's static resources or a
  custom Swagger UI build, meaningfully more work than the header hardening
  this slice actually scoped. `'self'`-scoped `default-src` plus
  `frame-ancestors 'none'`/`object-src 'none'` still blocks the realistic
  threats (third-party script/frame injection) without that cost.
- **Disabling Swagger UI in production entirely**: out of scope for this
  slice — ADR-035 scoped this slice to headers/secrets/404-vs-403, not
  API-docs exposure policy; noted here as a real, separate observation for a
  future slice or founder decision, not silently bundled in.
