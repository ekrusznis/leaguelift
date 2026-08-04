# ADR-001: Modular Monolith

## Status
Accepted

## Context

Rally26's initial objective is a sellable pilot supportable by one founder/engineer.
The long-term product spans many domains (organizations, teams, tournaments, public
pages, households, fees, fundraising, credits, commerce, payments, sponsorships), but
none of them yet have independent scaling, deployment, or team-ownership needs.

## Decision

Build Rally26 as a single Kotlin/Spring Boot backend deployment ("modular monolith")
with clearly separated domain modules (`identity`, `organization`, `membership`,
`publicpage`, `team`, `tournament`, `household`, `participant`, `fees`, `fundraising`,
`credits`, `catalog`, `store`, `order`, `payment`, `fulfillment`, `ledger`,
`sponsorship`, `notification`, `integration`, `reporting`, `admin`, `audit`,
`outbox`), each owning its own domain models, application services, persistence, and
web layer. Modules communicate in-process; cross-module side effects flow through the
transactional outbox rather than direct synchronous calls where practical.

Only create a module's folder when its milestone begins (see `DESIGN-DOC.md` section
12) — no speculative empty packages.

## Consequences

- Single deployable unit, single database, simple operational model for a solo
  engineer.
- Module boundaries are enforced by convention and code review, not by process or
  network boundaries — requires discipline to avoid modules reaching into each other's
  persistence layer.
- Splitting a module into its own service later remains possible because module
  boundaries already exist in the code.

## Alternatives Considered

- **Microservices from day one** — rejected. Adds operational overhead (multiple
  deployments, service discovery, distributed transactions) with no current scaling
  or team-ownership justification. Explicitly prohibited by `DESIGN-DOC.md` section
  27.4 until operational evidence proves a need.
- **Single undifferentiated Spring Boot app with no module boundaries** — rejected.
  Would produce a "big ball of mud" that is harder to reason about and harder to
  eventually extract services from if that becomes necessary.
