# DigitalOcean Deployment

Target infrastructure per ADR-008 (supersedes the App-Platform-oriented plan
originally in `DESIGN-DOC.md` section 11.6):

- A single Ubuntu 24.04 droplet running the whole stack via Docker Compose
  (`docker-compose.prod.yml`): backend, frontend, self-hosted Postgres, Caddy
  (reverse proxy + automatic TLS), and a nightly backup job.
- DigitalOcean Container Registry for backend/frontend images — built and pushed by
  CI, only ever pulled on the droplet.
- DigitalOcean DNS for `rally26.com` (`@`, `www`, `api` A records).
- The existing DigitalOcean Spaces bucket (`ll-allbuckets-1785762425006`) for
  logos/artwork/media and nightly database backups.

## Status

Prod droplet provisioned (`45.55.68.239`). Registry/DNS/firewall are provisioned by
running the `infra-bootstrap` GitHub Actions workflow (`workflow_dispatch`, one-time,
idempotent). Application deploys run automatically via the `deploy` workflow on every
push to `main`. Staging is explicitly deferred — a second droplet, to be added later
following the same pattern.

## Files

```text
infra/digitalocean/
├── bootstrap-droplet.sh       # One-time droplet OS setup (Docker, firewall, deploy user) — run manually once
├── docker-compose.prod.yml    # The actual running stack on the droplet
├── Caddyfile                  # Reverse proxy + TLS config
├── backup/                    # Nightly pg_dump -> Spaces
│   ├── Dockerfile
│   └── backup.sh
├── .env.prod.example          # Documents required env vars — never committed with real values
└── README.md
```

## First-time setup (already done for prod; repeat for a future staging droplet)

1. Provision a droplet in the DigitalOcean dashboard (Ubuntu 24.04 LTS).
2. Run `bootstrap-droplet.sh` on it once (via the DO web console or SSH).
3. Add the GitHub Actions deploy SSH public key to the droplet's
   `~/.ssh/authorized_keys` (root and/or the `rally26` deploy user created by the
   bootstrap script).
4. Set the `DO_DROPLET_HOST` / `DO_DROPLET_USER` repository variables and the
   `DO_DROPLET_SSH_KEY` repository secret in GitHub.
5. Run the `infra-bootstrap` workflow by hand once (creates the registry + DNS
   records + firewall).
6. Point `rally26.com`'s registrar nameservers at DigitalOcean's
   (`ns1/ns2/ns3.digitalocean.com`) — manual, done at wherever the domain is
   registered, not automatable via this repo.
7. Push to `main` (or run `deploy` by hand) to ship the first real deploy.

See `docs/adr/ADR-008-digitalocean-deployment.md` for the full reasoning, including
what was explicitly deferred (Managed Postgres, Cloudflare, staging) and why.
