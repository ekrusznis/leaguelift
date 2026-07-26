# Operations Runbook

This runbook covers Phase 0 operational surface area. Expand it as later phases add
webhooks, payments, and fulfillment.

## Health checks

- `GET /actuator/health` — overall status
- `GET /actuator/health/liveness` — process is up
- `GET /actuator/health/readiness` — dependencies (database) are reachable

## Common incidents (Phase 0 scope)

### API returning 5xx

1. Check `/actuator/health/readiness` — if the database is unreachable, check managed
   Postgres status/connection limits before anything else.
2. Check structured logs for the affected `requestId` (returned in every error
   response and response header).
3. Confirm no bad deploy: check the most recent image tag against the last known-good
   tag; roll back the container image if needed (see `.github/workflows/deploy.yml`).

### Migration fails on deploy

1. Do not attempt a destructive rollback — migrations are forward-only
   (`DESIGN-DOC.md` section 25.2).
2. Write a new forward-fix migration; never edit an already-applied one.
3. If the failure is data-dependent (not purely schema), coordinate a fix with a
   database backup/point-in-time-recovery option available as a fallback.

### Suspected security incident

1. Rotate the affected secret(s) immediately (OIDC client secret, Stripe keys, DO
   Spaces keys, etc. as applicable) in the environment's secret store.
2. Check `audit_event` for the affected organization/user for unexpected actions.
3. Since Phase 0 has no live payments yet, financial-incident procedures in section
   31.3 of `DESIGN-DOC.md` are not yet applicable — expand this section before Phase 5.

## Backups

- Rely on managed PostgreSQL automated backups and point-in-time recovery once a
  managed provider is provisioned (DigitalOcean per ADR-008, once written).
- A documented, tested restoration procedure is required before the Live Payments
  Launch gate (section 31.3) — not yet performed.

## Environments

Local, test, staging, and production each need separate: database, OIDC application
configuration, Stripe mode/keys (later), email provider configuration, storage
namespace, Sentry environment, and secrets. Production must never use the local
authentication bypass (enforced structurally — see `docs/security.md`).
