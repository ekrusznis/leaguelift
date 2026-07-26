# AI Agent Guardrails

This file is a quick-reference summary for coding agents. The authoritative version is
`DESIGN-DOC.md` section 27 ("AI Agent Operating Instructions") — read that in full
before starting non-trivial work. If this file and `DESIGN-DOC.md` ever disagree,
`DESIGN-DOC.md` wins.

## Before coding

1. Read `DESIGN-DOC.md` in full.
2. Read `README.md` and `docs/openapi.yaml`.
3. Read existing ADRs in `docs/adr/`.
4. Inspect current code before proposing changes.
5. Identify the active milestone (see `docs/launch-checklist.md` and section 10/35 of
   `DESIGN-DOC.md`).
6. Write a concise implementation plan before large changes.
7. Do not start unrelated future modules.

## While coding

- One vertical slice at a time. A "slice" includes: migration, domain behavior,
  repository, API, OpenAPI update, authorization, React UI, error states, tests, audit
  events, and documentation (`DESIGN-DOC.md` section 35).
- Keep the backend a modular monolith — no microservices, no Kubernetes.
- Every organization-owned query enforces membership/role checks in the backend.
- Every schema change is a new Flyway migration; never edit an applied one.
- Money is always `bigint` minor units + ISO-4217 currency — never floating point.
- Never edit historical ledger or credit events (future phases) — reversals only.
- Never process the same webhook event twice (future phases).
- Never expose secrets, stack traces, SQL, or internal exception names to clients.
- Never create child login accounts, represent credits as cash, or claim donations are
  tax-deductible without explicit verification.
- Keep external providers behind adapter interfaces.
- Use feature flags for incomplete production-facing features.
- Preserve loading / empty / error / unauthorized / success states in the UI.
- Avoid unrelated refactors and avoid adding dependencies that don't clearly reduce
  risk or complexity.
- Record material architecture decisions as new ADRs.

## Before completing a task

Run backend tests, frontend type-check + tests, and integration tests where
applicable; verify migrations, organization isolation, that no secrets were committed,
that OpenAPI matches behavior, error states, and mobile behavior; update documentation;
and summarize changes, tests, and remaining limitations in the handoff.

## Hard prohibitions

Do not: replace Kotlin/PostgreSQL/React, introduce microservices or Kubernetes, add
child login accounts, represent credits as cash, claim tax-deductibility without
verification, create fake production transactions, process live payments without
launch approval, store raw card details or secrets in source control, trust webhooks
without signature validation, delete financial history, or bypass role checks for
convenience.
