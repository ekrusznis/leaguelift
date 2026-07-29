# Architecture Decision Records

Format:

```markdown
# ADR-NNN: Title

## Status
Proposed | Accepted | Superseded

## Context

## Decision

## Consequences

## Alternatives Considered
```

Numbers reserved by `DESIGN-DOC.md` section 22:

- ADR-001: Modular monolith
- ADR-002: Managed OIDC authentication
- ADR-003: PostgreSQL and Flyway
- ADR-004: JDBC first, jOOQ before finance/reporting
- ADR-005: Stripe Connect charge model
- ADR-006: Transactional outbox
- ADR-007: Immutable ledger and credit events
- ADR-008: DigitalOcean deployment
- ADR-009: Adult-controlled household accounts
- ADR-010: Family credits as non-withdrawable fee credits
- ADR-011: Public-page content model
- ADR-012: File storage and upload security

ADR-001 through ADR-004 are written (Accepted) as part of the Phase 0 foundation.
ADR-012 is written (Accepted) as part of Phase 1's file-upload/branding slice
(2026-07-28, organization logo/cover only — see the ADR itself).
ADR-005 is written (Accepted) as part of Phase 2's remainder slice (2026-07-28,
separate-charges-and-transfers; Connect Express onboarding only, no live charge
routing yet — see the ADR itself).
ADR-006 through ADR-011 are proposed later in `DESIGN-DOC.md` (fundraising, commerce,
ledger, and infra phases) and should be written when those milestones begin — they are
intentionally not pre-written here to avoid locking in unresolved product questions
(see `DESIGN-DOC.md` section 19.3).

Additional, unreserved decisions:

- ADR-013: Java 17 instead of Java 21 baseline (environment-driven, see the ADR itself)
- ADR-014: Traditional email/password authentication, superseding ADR-002 (managed
  OIDC/Auth0 was never actually configured against a real tenant)
- ADR-015: Inbound webhook consumption (the `webhook_event` table and a real
  Stripe webhook receiver) pulled forward from Phase 5, scoped narrowly to
  confirming campaign contributions via `checkout.session.completed` — written
  as part of Phase 3's contribution-recording slice (2026-07-29)
- ADR-016: Printify integration scope for Phase 4 slice 1 — vendor selection is
  a US-location filter, not a price comparison (Printify's catalog exposes no
  cost/price); a variant's real cost is learned once via product creation, not
  guessed; fulfillment submission is draft-only (no send-to-production); no
  auto-design/personalization (2026-07-29)
- ADR-017: Phase 5 financial model — 5% flat platform fee (configurable,
  starting value not final), payout transfers gated by a configurable holding
  period (default 7 days) with manual-trigger-only firing (no automatic
  scheduler yet), org-admin-initiated refunds within a 14-day window,
  negative balances deducted from an org's next payout (2026-07-29)
- ADR-018: Sponsorship scope and charge model for Phase 6 slice 1 — reuses the
  existing separate-charges-and-transfers model with no separate sponsorship
  payment account (resolves §19.3 open question #19 for this slice), reuses
  CONTRIBUTION-shaped ledger entries with a new SPONSORSHIP source type
  rather than a new entry type, sponsor logo assignment is an org-admin
  action after confirmation rather than a public self-service upload (the
  media pipeline has no anonymous-upload path today), no refunds this slice
  (2026-07-29)
- ADR-019: Sponsorship approval workflow, refunds, renewal reminders,
  QR/link sharing, invoices, and sponsor-CRM widening (Phase 6 remainder) —
  a new `review_status` column gates public directory visibility separately
  from payment status; rejecting a sponsorship atomically refunds it via the
  same 14-day-window Stripe+ledger refund flow now built for sponsorships;
  a `@Scheduled` renewal-reminder job (deliberately outside the still-
  unconsumed outbox pattern) backed by a new minimal logging-only
  `EmailProvider`; ZXing-generated QR codes with no persistence/tracking;
  computed (not stored/numbered) invoices; `sponsor` widened with
  phone/company_name/notes (2026-07-29)
- ADR-020: Phase 7 capability-based authorization model — additive
  `role_assignment`/`guardian_relationship` tables alongside
  `organization_membership` (not a replacement); a new `AuthorizationService`
  with deny-by-default resource-scoped checks and org owner/admin
  team/tournament inheritance; `TEAM_ADMINISTRATOR`/`TOURNAMENT_ADMINISTRATOR`
  org roles now grant zero implicit team/tournament access (closing the
  "not actually scoped to just Varsity Soccer" gap); a guardian-authorized
  athlete self-link formalizing the existing seeded "controlled test account"
  pattern (not general athlete login); real `platformAdministrator` resolution
  (previously hardcoded false); Coach/Athlete/Tournament/Platform Admin
  dashboards wired to live data; most existing `MembershipService` call sites
  deliberately not migrated this phase (2026-07-29)
