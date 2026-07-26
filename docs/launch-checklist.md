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

Not started — depends on Phase 1 (organization onboarding, public pages).

## Live Payments Launch (section 31.3)

Not started — depends on Phase 5 and an accepted ADR-005 (Stripe Connect charge
model).

## Live Fulfillment Launch (section 31.4)

Not started — depends on Phase 4.

## Known limitations of this initial commit

- The backend was scaffolded in a sandboxed environment with Java 11, no Gradle
  installation, and no network access to Maven Central, so `backend/` has **not**
  been compiled, and its tests have **not** been run. Run `./gradlew build` locally
  with JDK 21 before trusting it.
- The frontend **was** built and tested in-sandbox (Node 22, npm registry reachable) —
  see the verification notes in the PR/commit this checklist ships with.
- No Auth0 tenant is configured yet; both backend and frontend currently only exercise
  their local development-bypass authentication paths.
- Repository/integration tests requiring Testcontainers + Docker are written but not
  executed here.
- CI workflows are written but have not yet run against a real GitHub Actions
  environment attached to this repository.
