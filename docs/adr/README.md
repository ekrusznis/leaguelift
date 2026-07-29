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
