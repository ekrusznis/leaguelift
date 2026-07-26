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

Numbers reserved by `DESIGN-DOC.md` section 28:

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
ADR-005 through ADR-012 are proposed later in `DESIGN-DOC.md` (fundraising, commerce,
ledger, and infra phases) and should be written when those milestones begin — they are
intentionally not pre-written here to avoid locking in unresolved product questions
(see `DESIGN-DOC.md` section 33).
