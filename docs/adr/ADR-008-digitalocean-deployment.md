# ADR-008: DigitalOcean Deployment — Single Droplet, Self-Hosted Postgres, DO DNS

## Status
Accepted

## Context

`DESIGN-DOC.md` section 11.6 and `infra/digitalocean/README.md`'s original placeholder
targeted DigitalOcean App Platform (managed container hosting) with DO Managed
PostgreSQL, and `infra/cloudflare/README.md` targeted Cloudflare for DNS/edge
protection in front of `leaguelift.com`. Neither had been provisioned; this ADR was
never accepted until now, when Phase 21 (DESIGN-DOC.md section 14, staging/prod
launch) actually started.

By the time this decision was made, the founder had already provisioned a single
Ubuntu 24.04 droplet (`45.55.68.239`) directly, rather than an App Platform app, and
had registered `leaguelift.io` — not `.com`, resolving that pre-existing doc
inconsistency in favor of `.io` (also the domain `application-prod.yml`'s
`support.inbox-email` default already assumed). A second (staging) droplet is
explicitly deferred — prod-only for now.

Two further decisions were made explicitly, not defaulted to the original plan:

1. **Postgres: self-hosted on the droplet (Docker container), not DO Managed
   PostgreSQL.** Managed Postgres would have cost roughly $15-60+/mo and included
   automated backups/PITR for free; self-hosting is free (uses the droplet already
   paid for) but pushes backup/PITR responsibility onto this codebase. Chosen anyway,
   on cost grounds, with the explicit understanding that the mitigation below is not
   optional.
2. **DNS: DigitalOcean's own DNS, not Cloudflare.** Simpler (one account, one
   dashboard, provisionable via the same `doctl`/API token already in hand) at the
   cost of no edge-level WAF/rate-limiting/bot-mitigation in front of the public
   checkout and webhook endpoints. Cloudflare remains available as a later addition —
   nothing about this ADR forecloses putting Cloudflare in front of DO DNS
   subsequently; it just isn't in the critical path for a first prod deploy.

A third constraint shaped the tooling, not the target: the sandbox environment this
project has been developed in has an allowlisted network egress and cannot reach
`api.digitalocean.com`, `api.github.com`, or `registry.digitalocean.com` directly (only
plain `github.com`, for git push/pull, is reachable). GitHub Actions runners have
unrestricted internet. Consequently, all actual DigitalOcean provisioning and
deployment calls are implemented as GitHub Actions workflows
(`.github/workflows/infra-bootstrap.yml`, `.github/workflows/deploy.yml`), not as
commands run directly from that sandbox — this is a durable architectural choice
(CI-as-execution-environment for infra operations), not merely a workaround, since it
also means no DigitalOcean credential ever needs to exist on a developer's own
machine.

## Decision

- **Compute:** one Ubuntu 24.04 droplet, Docker Compose-orchestrated
  (`infra/digitalocean/docker-compose.prod.yml`): `postgres`, `backend`, `frontend`,
  `caddy` (reverse proxy + automatic Let's Encrypt TLS), `backup` (nightly `pg_dump` to
  Spaces). No App Platform, no Kubernetes.
- **Registry:** DigitalOcean Container Registry
  (`registry.digitalocean.com/leaguelift`). CI builds and pushes both images; the
  droplet only ever pulls, never builds.
- **Database:** self-hosted Postgres 16 in a named Docker volume, no host port
  published. Mitigation for the lack of managed backups: `infra/digitalocean/backup/`
  runs a nightly `pg_dump | gzip` uploaded to the existing DO Spaces bucket
  (`ll-allbuckets-1785762425006`) with a 14-day retention prune. This proves backups
  are *taken*; it does **not** by itself satisfy DESIGN-DOC.md section 18.3's real
  quarterly restore-rehearsal launch gate, which remains open.
- **Object storage:** reuse the existing Spaces bucket, unchanged from the design
  doc's plan.
- **DNS:** DigitalOcean DNS for `leaguelift.io` — `@`, `www`, and `api` A records all
  pointed at the single droplet's IP, managed by `infra-bootstrap.yml`. Requires the
  domain registrar's nameservers to be pointed at DO's (`ns1/ns2/ns3.digitalocean.com`)
  — a one-time manual step at the registrar, outside any tool this codebase controls.
- **TLS:** Caddy on the droplet handles ACME/Let's Encrypt issuance and renewal
  itself — no separate certbot setup.
- **Secrets:** GitHub Actions Secrets are the source of truth; the deploy workflow
  writes them into a droplet-local `.env` file (mode 600, never committed) on every
  deploy. No DigitalOcean or application secret is ever stored in this sandbox's
  memory or committed to the repository.
- **CI/CD:** `deploy.yml` triggers on push to `main` (i.e., every merged PR once branch
  protection is enabled) — build both images, push to the registry, SCP the compose
  files to the droplet, SSH in to write `.env` and run `docker compose up -d`, then
  poll `/actuator/health` before declaring success.

## Consequences

- No infra spend beyond the droplet + Spaces (both already provisioned) until Managed
  Postgres or a second droplet is deliberately added later.
- The droplet is a single point of failure for compute *and* data — there is no
  managed failover. Acceptable for a pre-launch/pilot posture; revisit before a real
  paying-customer launch beyond a small pilot.
- Restore testing is now a real, tracked launch-gate obligation, not a theoretical one
  — `pg_dump` backups exist starting with the first deploy, but nobody has proven one
  restores cleanly yet.
- Adding a staging droplet later is a small, mechanical repeat of this same ADR's
  pattern (second droplet, second DNS subdomain set, second GitHub Environment) — the
  workflows are already parameterized by `vars.DO_DROPLET_HOST`/`vars.DO_DROPLET_USER`
  rather than hardcoded, specifically to make that addition low-friction.
- Moving to DO App Platform or DO Managed PostgreSQL later does not require reversing
  this ADR wholesale — the container images and application config are identical
  either way; only the deploy workflow's target and the database connection string
  would change.
- No edge WAF/rate-limiting exists in front of public checkout/webhook endpoints today.
  The application layer's own rate-limiting/abuse controls (if any — see
  DESIGN-DOC.md section 18) are the only protection until Cloudflare or an equivalent
  is added.

## Alternatives Considered

- **DigitalOcean App Platform** (originally documented) — rejected for now: the
  founder had already provisioned a droplet directly, and a droplet gives full control
  over the Postgres self-hosting decision below, which App Platform's managed-service
  model doesn't accommodate as cheaply.
- **DO Managed PostgreSQL** — rejected on cost grounds for this stage; explicitly
  flagged as the safer default and a straightforward later upgrade once real customer
  data/payments volume justifies the cost.
- **Cloudflare DNS/edge** — rejected for now to minimize the number of accounts/tools
  needed for a first deploy; remains a clean later addition, not foreclosed.
- **Kubernetes (DOKS)** — never seriously considered; far more operational overhead
  than a single-droplet Docker Compose deployment justifies at this stage.
