# ADR-035: Phase 13 Production Readiness Review — Scope

## Status
Accepted

## Context

DESIGN-DOC.md's Phase 13 row names a wide range of stabilization work: security
architecture and authorization review, secrets/dependency/webhook/payment
hardening, privacy/data-retention review, UX and accessibility review across
every persona and device class, full regression and integration QA, performance/
load testing, backup/restore and incident-response rehearsal, legal-content
verification, operational runbooks, user-acceptance testing, defect triage, and
formal production go/no-go sign-off. Section 19.1 confirms only local and test
environments actually exist today — no staging/prod deployment, no provisioned
managed database, no real users. Section 20.2 already states "Phase 13 is a
stabilization gate: do not add non-blocking features during production-
readiness review."

Not every item in that list is executable inside this session. Fabricating a
load-test result against infrastructure that doesn't exist, or claiming a
backup-restore rehearsal happened against a database that was never
provisioned, would be exactly the kind of dishonest "claim it's live before
it's verified" this codebase has consistently refused to do elsewhere (Printify
vendor selection, ADR-016; connector verification, ADR-031; every "logging
stopgap vs. real vendor" provider distinction since Phase 8).

## Decision

**Phase 13 splits into what's genuinely verifiable now (code-level, this
session) and what genuinely isn't (needs real infrastructure or people this
environment doesn't have):**

**Buildable now, four slices:**
1. **Security hardening** — security headers (CSP/HSTS/X-Content-Type-Options/
   frame restrictions/Referrer-Policy/Permissions-Policy), none of which
   `SecurityConfig` currently sets; finalizing the secrets audit a founder
   request during Phase 8 explicitly deferred to "a later phase" (confirming
   `.env.example` completeness and no plaintext secrets — `gitleaks` and
   `dependency-review-action` already run in CI, so this is largely
   verification, not new tooling); a 404-vs-403 information-leak review.
2. **Test-coverage gap review** — section 18.1 names an explicit list of
   "critical scenarios to always cover." This slice checks that list against
   what's actually tested today and writes real tests only for genuine gaps
   found — not padding the suite with tests for scenarios already covered.
3. **Accessibility code-level audit** — the same methodology ADR-030
   established for the mobile audit (code-level markup review, not live-
   browser testing, since the sandboxed browser tool's viewport control still
   doesn't work): semantic HTML, ARIA, keyboard navigation, color contrast.
4. **Operational runbook documentation** — writing the actual incident-
   response and backup/restore procedures section 18.3 describes as "design
   target," labeled explicitly as *written* procedure, not yet *rehearsed*
   against real infrastructure.

**Explicitly flagged as pending, not attempted this session:**
- Performance/load testing at pilot scale — no staging/prod environment
  exists to load-test against; testing the local dev server would measure
  nothing representative.
- A real backup/restore rehearsal — no managed PostgreSQL provider is
  provisioned yet (section 19.1); this needs a real database to restore.
- A live incident-response rehearsal — a tabletop exercise needs real people
  and (ideally) a real deployed system to react to.
- User-acceptance testing — needs real users, which requires the pilot this
  entire roadmap has been building toward, not yet reached.
- Legal-content verification — the legal pages already carry an explicit
  "draft, not reviewed by counsel" banner (section 12); actual sign-off needs
  a real attorney, not a coding agent.
- Formal production go/no-go sign-off — a business decision for the founder
  to make once every gate above is actually closed, not something to simulate.

## Consequences

- Phase 13 will not be marked "Shipped" the way prior feature phases were —
  it will show four slices shipped and a documented, honest list of what
  remains blocked on infrastructure/stakeholders not yet in place. This is a
  different completion shape than every phase before it, and that's
  intentional, not a shortfall.
- The flagged items remain visible in DESIGN-DOC.md's roadmap and don't get
  quietly dropped — future phase planning should treat them as real
  prerequisites to production launch, not forgotten scope.

## Alternatives Considered

- **Simulating load-test/backup-restore results against the local dev
  environment to "complete" the phase**: rejected — a local single-instance
  Postgres under no real load proves nothing about pilot-scale behavior, and
  presenting that as a load test would be actively misleading.
- **Skipping Phase 13 entirely and moving to Phase 14**: rejected — see the
  Phase 13/14 review conversation preceding this ADR; the founder explicitly
  placed the Platform Admin console after stabilization work, and section
  20.2 already treats Phase 13 as a hard gate before further feature work.
