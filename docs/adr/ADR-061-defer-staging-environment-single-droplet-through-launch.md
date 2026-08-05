# ADR-061: Defer a Dedicated Staging Environment — Single Droplet Through Initial Launch

## Status
Accepted

## Context

`DESIGN-DOC.md`'s Phase 21 (§14.1F), Environments (§19.1), and CI/CD (§19.2) sections
specified a dual-environment target from early in the project: independent staging and
production DigitalOcean stacks, each with its own Managed PostgreSQL database, a
`stage`->`main` two-step branch promotion flow, and separate `stage.rally26.com`/
`rally26.com` domains.

ADR-008 (2026-08-03) already superseded part of this when real infrastructure was
actually provisioned: a single Ubuntu droplet with self-hosted Postgres (not DO Managed
PostgreSQL), `main`-only branch protection, and an explicit note that "a second
(staging) droplet is explicitly deferred — prod-only for now." However, `DESIGN-DOC.md`
was never updated to match — Phase 21's roadmap row, §14.1F, §19.1, and §19.2 still
described the original two-environment architecture as the near-term plan, contradicting
ADR-008's own already-accepted decision. Asked directly during a roadmap-planning
conversation, the founder confirmed the reconciliation this ADR records: skip a
dedicated staging environment for now, use the existing production droplet as the
practical environment through initial launch, and add a real staging environment (its
own droplet, its own self-hosted Postgres) as a later phase after release.

## Decision

`DESIGN-DOC.md` is reconciled to describe what ADR-008 already decided, plus this
session's confirmed sequencing for staging:

- **Phase 21 (§14.1 roadmap row, §14.1F) is single-environment scope.** Branch
  protection, immutable-image builds, migrations, and health-check deployment already
  run for real against `main` and the existing droplet (ADR-008's `deploy.yml`) — this
  is not a design target to build, it's already live. What remains open and still
  belongs to Phase 21: a documented rollback procedure, monitoring/alerting, a rehearsed
  backup restore (backups are taken nightly per ADR-008, restoration has never been
  proven), an incident-response rehearsal, legal review, UAT, and founder go/no-go.
- **§19.1/§19.2 are rewritten to describe the real, current single-environment setup**
  rather than the aspirational dual-stack one — one droplet, self-hosted Postgres, one
  GitHub `production` Environment, `main`-only promotion.
- **A dedicated staging environment is deferred, not cancelled.** It becomes a distinct
  phase sequenced *after* initial launch: a second droplet, its own self-hosted Postgres
  (mirroring production's own cost/ops choice, not a switch to Managed PostgreSQL for
  just one environment), its own Spaces namespace, its own JWT secret and test-mode
  provider credentials, `stage.rally26.com`/`api-stage.rally26.com` DNS, and a second
  GitHub `staging` Environment. At that point branch promotion becomes
  `feature/* -> stage -> main`, exactly as originally specified — that target
  architecture wasn't wrong, it was just sequenced too early relative to what a
  pre-launch, pre-pilot-evidence product actually needs.
- **§14.4's launch gates are updated** to stop requiring a staging environment as a
  prerequisite for the Production-Ready Release Gate or the Base-Layer Internal
  Launch — both now correctly reflect that real production deployment already exists
  and is exercised, while flagging what's genuinely still missing (rollback procedure,
  monitoring, backup-restore rehearsal) rather than conflating that with "no environment
  exists at all," which was never accurate even before this ADR.

## Consequences

- Nothing about the actual deployed infrastructure changes — this ADR is a
  documentation reconciliation, not an infrastructure change. `deploy.yml`,
  `docker-compose.prod.yml`, and ADR-008 are unmodified.
- Before real pilot families are onboarded, any risky verification work (role
  navigation, season rollover, bulk onboarding) has to happen against real production
  directly, since there's no isolated staging environment to rehearse against yet
  (§14.4's Pilot Organization Launch gate). This raises the bar on taking a fresh
  backup immediately before such verification, and on the backup-restore rehearsal
  becoming a real priority rather than a deferred nice-to-have.
- The future staging-environment phase has no number or detailed scope yet beyond what
  ADR-008's own consequences already sketch (`vars.DO_DROPLET_HOST`/`vars.DO_DROPLET_USER`
  are deliberately parameterized to make a second droplet low-friction to add later).
  Assigning it a real phase number and acceptance criteria is future work, not decided
  here.
- `DESIGN-DOC.md` is local-only (gitignored, per `chore: gitignore internal design and
  operational docs`) — these edits exist only in this working copy, matching how every
  other design-doc-only change in this project's history has been handled.

## Alternatives Considered

- **Build the real staging environment now, before further feature work.** Rejected by
  the founder — there's no pilot yet, no real usage evidence, and ADR-008 already made
  the cost-conscious call to defer a second droplet; provisioning one now would add
  ongoing infra cost and operational surface (a second Postgres instance to secure,
  patch, and back up) ahead of any evidence it's needed yet.
- **Leave `DESIGN-DOC.md` describing the dual-environment target unchanged, since it's
  "still the eventual plan anyway."** Rejected — the doc as written read as though
  staging were an unstarted-but-imminent Phase 21 prerequisite, actively contradicting
  ADR-008's own already-accepted decision. Leaving that contradiction in place would
  mislead the next person (or agent) reading the roadmap into thinking Phase 21 is
  blocked on infrastructure that was deliberately not being built yet.
- **Switch production itself to DO Managed PostgreSQL while at it, since a future
  staging environment would supposedly also justify it.** Out of scope — ADR-008's
  self-hosted-Postgres decision stands unchanged; this ADR only addresses environment
  count and sequencing, not the database-hosting choice within each environment.
