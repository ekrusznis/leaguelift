# Launch Checklist

Gates are defined in `DESIGN-DOC.md` section 31. This file tracks status against them.

## Base-Layer Internal Launch (section 31.1)

- [ ] CI green (workflows added in this commit; not yet run against a real PR)
- [x] Local setup documented (`README.md`)
- [ ] Staging environment working
- [ ] Authentication verified (Auth0 tenant not yet configured — local bypass only)
- [ ] Organization isolation verified (unit tests written; Testcontainers integration
      tests not yet run in this environment — see "Known limitations" below)
- [ ] Backups configured
- [ ] Error monitoring configured (Sentry placeholders only)
- [ ] Support email working

## Pilot Organization Launch (section 31.2)

In progress. Organization onboarding (profile, sports/contact info, administrator
invitations, member role management, onboarding checklist) is implemented per
`DESIGN-DOC.md` section 35 #1. Still missing before this gate: public pages, file
uploads/branding (slice #2), team/tournament creation (slices #3–4), privacy policy
and terms review, mobile testing.

## Live Payments Launch (section 31.3)

Not started — depends on Phase 5 and an accepted ADR-005 (Stripe Connect charge
model).

## Live Fulfillment Launch (section 31.4)

Not started — depends on Phase 4.

## Known limitations

- The Phase 0 foundation backend was confirmed building and syncing successfully in
  IntelliJ IDEA on Java 17 (see ADR-013 — downgraded from the originally documented
  Java 21, which wasn't reliably resolvable even with the Foojay auto-download
  resolver).
- The organization-onboarding slice (invitation module, extended organization/
  membership modules, `V2__organization_onboarding.sql`) was written using the same
  patterns and conventions as the already-verified Phase 0 code, but has **not** been
  separately build- or test-run yet — run `./gradlew build test` and re-sync Gradle in
  IntelliJ to confirm before trusting it. Watch specifically for the new
  `com.fasterxml.jackson.databind.ObjectMapper` dependency injected into
  `OrganizationRepository` (used to serialize `sports` to/from `jsonb`) resolving
  correctly.
- The frontend **was** built, linted, and tested (typecheck, 15 Vitest/RTL tests,
  production build) in the sandbox this slice was authored in (Node 22, npm registry
  reachable) — not yet run against the real backend end-to-end.
- No Auth0 tenant is configured yet; both backend and frontend currently only exercise
  their local development-bypass authentication paths.
- Repository/integration tests requiring Testcontainers + Docker are written but not
  executed in this sandbox — run them locally with Docker running.
- CI workflows are written but have not yet run against a real GitHub Actions
  environment attached to this repository.
- Invitation emails are not actually sent yet — `InvitationService` writes an
  `outbox_event` (`membership.invited`) but no worker consumes it. The invite flow
  currently only works by manually sharing the token/link from the create-invitation
  API response.
