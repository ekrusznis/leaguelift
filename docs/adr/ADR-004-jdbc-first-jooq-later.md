# ADR-004: JDBC First, jOOQ Before Finance/Reporting

## Status
Accepted

## Context

Early Phase 0/1 modules (identity, organizations, memberships) have simple,
low-cardinality queries. Later modules (fees, credits, ledger, reporting) require
precise, reviewable SQL with complex joins, aggregations, and money-safe typing, where
a full SQL builder pays for itself.

## Decision

Use Spring JDBC's `JdbcClient` directly for early modules (identity, organization,
membership, audit, outbox). Introduce jOOQ before implementing the financial ledger
and complex reporting modules (`DESIGN-DOC.md` sections 11.2 and 14.7), generating
typed query DSL from the Flyway-managed schema so ledger and reporting queries get
compile-time column/table checking without hiding the actual SQL being executed.

## Consequences

- No jOOQ code-generation build step is needed for the Phase 0 foundation, keeping the
  initial build simple.
- A deliberate, documented transition point exists (start of ledger/reporting work)
  rather than an ad hoc decision made under deadline pressure later.
- Two query styles will coexist in the codebase long-term (`JdbcClient` for simple
  CRUD-shaped modules, jOOQ for complex financial/reporting modules) — this is an
  accepted tradeoff, not an oversight.

## Alternatives Considered

- **Hibernate/JPA everywhere** — rejected; implicit lazy-loading and generated SQL are
  a poor fit for money-correctness requirements and reviewable query plans.
- **jOOQ from day one** — rejected for Phase 0; adds a code-generation build step and
  learning overhead before there is complex SQL that benefits from it.
- **Raw JDBC/`JdbcTemplate` everywhere including finance** — rejected; hand-written
  string SQL for ledger/reporting joins is more error-prone and harder to review than
  a typed DSL once query complexity grows.
