# ADR-002: Managed OIDC Authentication

## Status
Superseded by ADR-014 (traditional email/password authentication). Preserved
unedited below as the historical record of the original decision.

## Context

Rally26 needs authentication for platform administrators, organization
administrators, and adult parent/guardian accounts, with strong security guarantees
(MFA for admins, no child accounts, no password-storage liability) and minimal
in-house security engineering, matching the one-founder operating constraint.

## Decision

Use a managed OIDC provider (seed design: Auth0). The React frontend obtains an access
token via Authorization Code Flow with PKCE. The Spring Boot backend acts as an OAuth2
Resource Server, validating JWT signature, issuer, audience, and expiration on every
request — the backend never trusts frontend-asserted identity or roles. Internal
`app_user` records are provisioned from the external subject identifier (`sub` claim),
not email, because email is not a stable identity key. All authorization (organization
membership, role) is resolved from internal database records, never inferred from the
token or from frontend routing.

A `local` Spring profile provides a development-only authentication bypass (fixed
internal test user) so the API and frontend can be exercised without a configured
OIDC tenant. This bypass must be structurally impossible to enable outside the
`local` profile (see `docs/security.md`).

The identity-provider integration is written behind an internal abstraction
(`IdentityProvider` adapter, per `DESIGN-DOC.md` section 20) so a future provider
change does not require rewriting authorization logic throughout the codebase.

## Consequences

- No first-party password storage or credential-reset flows to build or secure.
- MFA, breach detection, and session security are inherited from the managed provider.
- Adds an external dependency and per-tenant configuration to each environment
  (local/test/staging/production each need separate Auth0 application configuration).
- Provider change is possible later but not free — the adapter reduces, not
  eliminates, that cost.

## Alternatives Considered

- **Roll our own authentication** — rejected. Higher security risk and engineering
  cost for a solo-founder-operated pilot; no product requirement justifies it.
- **Firebase Auth / Clerk / other managed providers** — plausible alternatives;
  Auth0 was selected as the seed design per `DESIGN-DOC.md` section 11.5. Final
  provider selection before production remains an open question (`DESIGN-DOC.md`
  section 33, item 2) and can change without violating this ADR as long as the
  `IdentityProvider` abstraction is respected.
