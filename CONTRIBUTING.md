# Contributing to Rally26

Rally26 is developed against `DESIGN-DOC.md`, which is the authoritative product and
engineering specification. Read it before making non-trivial changes.

## Ground rules

- Treat `DESIGN-DOC.md` as the source of truth. If a change conflicts with it, update the
  design doc and record an ADR in `docs/adr/` rather than silently diverging.
- Keep the backend a modular monolith (Kotlin / Spring Boot). Do not introduce
  microservices, Kubernetes, or an alternative backend language.
- Keep the frontend on React + TypeScript + Vite.
- Every schema change goes through a new Flyway migration in
  `backend/src/main/resources/db/migration/`. Never edit an already-applied migration.
- Every organization-owned query must enforce membership/authorization in the backend.
  React is never an authorization boundary.
- Money is always an integer minor-unit `bigint` plus an ISO-4217 currency code. Never
  use floating point for money.
- Add tests with every behavior change (unit tests at minimum; integration tests for
  persistence and API behavior).
- Update `docs/openapi.yaml` in the same change that adds or modifies an endpoint.

## Local development

See `README.md` for setup instructions.

## Commit style

Use short, descriptive commit messages in the imperative mood
(e.g. "Add organization membership isolation test").

## Architecture Decision Records

Create an ADR (`docs/adr/ADR-NNN-title.md`) for material architectural decisions:
new dependencies, changes to the data model philosophy, provider integration strategy,
or anything that would be expensive to reverse. Use the template in `docs/adr/README.md`.
