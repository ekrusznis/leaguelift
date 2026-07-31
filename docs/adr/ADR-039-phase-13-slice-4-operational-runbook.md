# ADR-039: Phase 13 Slice 4 — Operational Runbook Documentation

## Status
Accepted

## Context

ADR-035 scoped Phase 13 Slice 4 to writing the actual incident-response and
backup/restore procedures section 18.3 named as a "design target" — labeled
explicitly as *written* procedure, not yet *rehearsed* against real
infrastructure, since section 19.1 confirms no staging/prod environment or
managed database provider exists yet to rehearse against. Before this slice,
section 18.3 held four short, generic paragraphs (5xx/migration-failure/
security-incident/backups) with no reference to a single real endpoint,
table, or config key this codebase actually has.

## Decision

**Rewrote section 18.3 into seven procedures, each naming real, currently-
existing code** rather than generic incident-response advice that could apply
to any codebase:

1. **API returning 5xx** — expanded with the real correlation mechanism
   (`ErrorResponse.requestId`, `GlobalExceptionHandler.handleUnexpected`'s
   server-side-only full-exception logging) and a note that
   provider-integration failures (Stripe/Printify/Resend/Twilio/Spaces, each
   a separate `*Client`/`*Provider` seam) shouldn't cascade — if one does,
   that's a bug to fix, not just an incident to close.
2. **Migration fails on deploy** — expanded with the concrete Flyway
   mechanics already established by convention in this codebase (forward-only,
   checksum-enforced, `FlywayConfig.outOfOrder`).
3. **Suspected security incident** — expanded from "rotate the affected
   secret" into a per-secret rotation reference covering every real secret
   this codebase has (`JWT_SECRET`, `STRIPE_SECRET_KEY`/
   `STRIPE_WEBHOOK_SECRET`, `PRINTIFY_API_TOKEN`, `SPACES_ACCESS_KEY`/
   `SPACES_SECRET_KEY`, `RESEND_API_KEY`, `TWILIO_AUTH_TOKEN`), a real
   `audit_event` query pattern, and a cross-reference into the new
   financial-incident procedure for money-involving cases.
4. **Financial/ledger incident** (new) — didn't exist before this slice; the
   old text explicitly deferred this ("financial-incident procedures aren't
   applicable pre-Phase-5, but expand this section before then") and Phase 5
   has been live since 2026-07-29. Documents that `ledger_entry`'s
   append-only structure (enforced by `MoneyArithmeticTest`, ADR-037) means a
   wrong-looking entry was wrong when written, never altered after; how to
   correlate an entry back to its source row and audit trail; that a
   correction is always a new reversing entry via `LedgerService.
   recordRefund`, never a manual `UPDATE`; and that `PayoutAccountService.
   triggerTransfer`'s manual-trigger-only design means an unexpected payout
   is always traceable to a real authenticated action via the
   `payout.transfer_triggered` audit action.
5. **Outbox backlog / notification delivery failure** (new) — documents the
   real, already-built recovery path: `GET /api/v1/admin/outbox-events/
   {failed,dead-letter}` to see what's stuck, `POST .../{id}/reprocess` to
   re-queue after the cause is fixed. Honestly notes that section 18.2's
   "outbox backlog" metric is still a design target with no real Micrometer
   instrumentation — the runbook gives the direct SQL query
   (`select count(*) from outbox_event where status = 'PENDING'`) as the
   actual way to check today, rather than pointing at a metric that doesn't
   exist yet.
6. **Stripe/provider webhook failures** (new) — documents that every webhook
   confirmation path (`Order`/`Contribution`/`SponsorshipService.
   confirmFromWebhook`) is idempotent and already covered by real tests
   (section 18.1 scenario 7, verified in ADR-037), so Stripe's automatic
   retries are always safe and manual replay should be reserved for
   specifically testing the idempotency guard.
7. **Backups and restore** — turned the single "design target, not yet
   configured" sentence into an actual four-step target procedure once
   DigitalOcean Managed PostgreSQL (section 5's chosen provider) is
   provisioned: automated daily backups plus WAL-based PITR sized to cover
   the real 14-day refund window and configurable payout holding period; a
   mandatory quarterly restore-into-scratch-instance rehearsal with concrete
   verification steps (Flyway `validate`, spot-checking known rows), framed
   as a hard Live Payments launch-gate prerequisite, not optional; CSV export
   as an independent second layer, reusing the existing `CsvUtil` rather than
   inventing a new export path; and object-storage versioning for uploaded
   assets.

Every procedure is explicitly labeled in the section's lead paragraph as
**written but not yet rehearsed**, distinguishing this from a procedure that
has actually been executed against a real system — consistent with the
"never fabricate a live-infrastructure result" discipline ADR-035 established
for this entire phase.

## Consequences

- Section 18.3 is now something an on-call engineer could actually follow —
  every referenced endpoint, table, and config key is real and exists in the
  codebase today — rather than generic advice requiring the reader to first
  figure out what this specific application's equivalents are.
- The financial-incident and outbox/webhook-failure procedures close real
  documentation gaps that predate this slice (financial procedures were
  explicitly deferred pre-Phase-5 and never revisited; outbox/webhook
  failure handling had no runbook entry at all despite the underlying
  infrastructure being real since Phase 8).
- The backup/restore procedure remains unrehearsed and honestly labeled as
  such — this ADR does not claim a rehearsal happened, and the Live Payments
  launch gate (§14.4) still requires a real one before go-live.

## Alternatives Considered

- **Actually provisioning a throwaway managed Postgres instance this
  session to rehearse a real restore**: rejected — out of scope for a
  documentation slice, and ADR-035 already rejected simulating
  infrastructure-dependent results; provisioning real cloud infrastructure
  is a cost/access decision for the founder, not something to do
  unilaterally mid-slice.
- **Leaving the financial-incident and outbox-failure gaps undocumented
  since they were "always going to be revisited later"**: rejected — both
  gaps were explicitly named in the prior text (the financial-incident TODO)
  or silently absent despite real infrastructure existing (outbox/webhook
  failure handling); Phase 13 is exactly the stabilization pass meant to
  close gaps like these, not defer them again.
