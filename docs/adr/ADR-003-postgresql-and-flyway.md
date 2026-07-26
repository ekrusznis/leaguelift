# ADR-003: PostgreSQL and Flyway

## Status
Accepted

## Context

LeagueLift's core value is trustworthy financial and organizational data (fees,
credits, ledger entries, memberships). This requires strong relational integrity,
transactional guarantees, and an auditable, forward-only schema history — one
database per environment, managed by one engineer.

## Decision

Use PostgreSQL as the only datastore for application data, with Flyway managing every
schema change as a versioned, forward-only migration under
`backend/src/main/resources/db/migration/`. Conventions (`DESIGN-DOC.md` section 14):
UUID primary keys, `timestamptz` for all timestamps, explicit foreign keys, check and
unique constraints, `bigint` minor-unit integers with ISO-4217 currency codes for
money (never floating point), and `jsonb` reserved for flexible metadata rather than
core relational structure. Already-applied migrations are never edited; corrections
are new migrations.

## Consequences

- Every schema change is reviewable, versioned, and reproducible from a clean database
  (required by acceptance criteria in `DESIGN-DOC.md` section 29.1 and test scenario
  14 in section 22.3).
- No destructive-rollback tooling is assumed; forward-fix migrations are the recovery
  path for a bad migration in a shared environment.
- Testcontainers-backed PostgreSQL is used for repository/integration tests so tests
  run against the real database engine rather than an in-memory substitute.

## Alternatives Considered

- **MySQL** — rejected; team familiarity and `jsonb` support favor Postgres, and the
  design doc specifies Postgres directly.
- **NoSQL document store** — rejected; the domain is fundamentally relational
  (organizations, memberships, fees, ledger entries with strong referential integrity
  and transactional requirements).
- **ORM-managed schema (e.g. Hibernate `ddl-auto`)** — rejected; implicit,
  non-reviewable schema generation is incompatible with an auditable financial system.
