# Architecture

## Overview

LeagueLift is a modular monolith: one Kotlin/Spring Boot backend, one React frontend,
one PostgreSQL database per environment. See ADR-001.

```text
                 ┌───────────────────────────┐
   Browser  ───▶ │  frontend (React + Vite)  │
                 └────────────┬──────────────┘
                              │ HTTPS (JWT bearer)
                              ▼
                 ┌───────────────────────────┐
                 │  backend (Spring Boot)    │
                 │  ┌─────────────────────┐  │
                 │  │ Spring Security      │  │  validates JWT (issuer, audience, exp)
                 │  │ (OAuth2 Resource     │  │
                 │  │  Server)             │  │
                 │  └─────────────────────┘  │
                 │  ┌─────────────────────┐  │
                 │  │ identity module      │  │  provisions app_user from `sub`
                 │  │ organization module   │  │  organizations
                 │  │ membership module     │  │  organization_membership + roles
                 │  │ audit module          │  │  audit_event
                 │  │ outbox module         │  │  outbox_event (async work)
                 │  └─────────────────────┘  │
                 └────────────┬──────────────┘
                              │ JDBC
                              ▼
                 ┌───────────────────────────┐
                 │  PostgreSQL (Flyway-      │
                 │  migrated schema)         │
                 └───────────────────────────┘
```

## Request flow

1. React obtains an access token from the OIDC provider (or uses the in-memory dev
   session when `VITE_AUTH_DEV_MODE=true`).
2. Every API request carries `Authorization: Bearer <token>`.
3. Spring Security validates the token (signature, issuer, audience, expiration)
   before any controller code runs. In the `local` profile only, a fixed internal test
   principal is used instead (see `docs/security.md`).
4. A servlet filter assigns/propagates a request ID (`X-Request-Id`), included in
   every response and every error body.
5. Controllers are thin: they map DTOs and delegate to application services.
6. Application services own transaction boundaries, enforce organization-membership
   authorization, and write audit events / outbox events in the same transaction as
   the state change they describe.
7. Repositories (`JdbcClient` in Phase 0) perform SQL scoped by `organization_id`.

## Module layering

Within a domain module (`DESIGN-DOC.md` section 13.2):

```text
domain/        Domain models and business rules
application/   Use cases and transaction boundaries
persistence/   Repositories and SQL
web/           Controllers and API DTOs
integration/   Provider-specific adapters (when relevant)
```

Small modules (e.g. `audit`, `outbox` in Phase 0) may use a flatter structure until
complexity justifies the full layering.

## Cross-module communication

Modules avoid reaching into each other's persistence layer directly. Where one
module's action needs to trigger work in another (e.g. "organization created" should
eventually trigger a welcome email), the writing module records an `outbox_event` in
the same transaction as its state change; a background worker (introduced when the
first async consumer is needed) claims and processes outbox events idempotently.

## Data isolation

Every organization-owned table includes `organization_id`. Every organization-scoped
query enforces the caller's membership in that organization at the application-service
layer — this is a backend guarantee, not a frontend one. See
`docs/security.md` and test scenarios in `DESIGN-DOC.md` section 22.3.

## Foundation schema (Phase 0)

See `backend/src/main/resources/db/migration/V1__foundation.sql`:
`app_user`, `organization`, `organization_membership`, `audit_event`, `outbox_event`.
