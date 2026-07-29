# ADR-005: Stripe Connect Charge Model

## Status
Accepted

## Context

DESIGN-DOC.md §16 explicitly gates live multi-party payment routing behind this ADR:
*"Do not activate live multi-party payment routing until an ADR selects and documents
the Stripe Connect charge model."* It blocked Phase 3 (contribution/payment recording)
and Phase 5 (live payments) from being scoped concretely. The founder asked to resolve
it as part of Phase 2, alongside that phase's other remainder work (manual payment
recording, collections, CSV export), specifically so it stops blocking those later
phases — not to build live charge routing now.

Money needs to eventually reach an organization's bank account (a payout) while
LeagueLift takes a platform fee, and — per DESIGN-DOC.md §16/§14.3 — LeagueLift needs
to fund Printify production costs before an org's apparel-sale share is finalized, and
hold family credits "pending" before they become "available." That timing requirement
is the deciding factor between Stripe Connect's three charge models.

## Decision

**Separate charges and transfers.** LeagueLift charges the customer directly (funds
land in LeagueLift's own Stripe balance first), then explicitly creates a `Transfer`
to the connected organization's account once its share is known — after production
costs are deducted, after a credit's availability delay has passed, etc. LeagueLift is
the merchant of record and bears refund/dispute liability by default (resolves
§19.3's former item 3 — organizations are not the merchant of record).

Rejected alternatives:
- **Destination charges** — one API call, funds auto-route to the org minus a platform
  fee. Rejected: no clean way to hold back an unknown production cost or delay a
  payout until a credit becomes available; the transfer timing is baked into the
  charge itself.
- **Direct charges** — the charge is created on the org's own connected account; the
  org becomes primarily liable for disputes, and Stripe's statement descriptor shows
  the org's name, not LeagueLift's. Rejected: contradicts the one-consistent-checkout-
  experience goal and pushes dispute liability onto volunteer-run organizations that
  are in no position to handle it.

**This phase (Phase 2) implements onboarding only** — Stripe Connect Express account
creation and hosted onboarding-link flow (`payout` module), so an organization can
connect a payout destination. It does **not** implement live charge/transfer
execution; that stays Phase 5's job, gated behind its own launch checklist
(DESIGN-DOC.md §14.4: webhook signature verification, idempotency, an immutable
ledger, refund/dispute workflows, reconciliation, production secrets, legal/accounting
review, tested backup restoration, an incident-response runbook, one rehearsed
controlled live transaction+refund).

Payout-account fields mirror Stripe's own account object (`stripe_account_id`,
`details_submitted`, `charges_enabled`, `payouts_enabled`) rather than a separate
status enum — Stripe stays the source of truth, LeagueLift keeps a synchronized
record (DESIGN-DOC.md §16).

## Consequences

- Phase 3 (contribution recording) and Phase 5 (live payments) can now be scoped
  concretely against a decided charge model instead of guessing.
- Every future live charge needs an explicit, separate `Transfer` call at the point
  an org's share is actually known — more application code than destination charges
  would have needed, in exchange for correct timing control.
- LeagueLift bears refund/dispute liability as merchant of record; this needs to be
  priced into the platform fee and covered in the pilot's legal/terms review before
  Live Payments Launch.
- §19.3's open questions list shrinks by two items (Stripe Connect charge model;
  merchant of record) but several closely related questions remain genuinely open
  and are NOT resolved by this ADR: exact refund policy, exact credit
  percentages/availability delays, negative-balance handling if a refund happens
  after a transfer already went out, exact platform fee percentage, and org payout
  timing/schedule. These must be resolved before Phase 5 begins, not assumed from
  this decision.
- No webhook consumption exists yet (`webhook_event` table is unbuilt, per
  DESIGN-DOC.md §17) — Phase 5 must add Stripe webhook handling for account status
  changes, not just this phase's on-return synchronous refresh call, before going live.

## Alternatives Considered

Covered inline above (destination charges, direct charges) — both rejected for the
reasons stated, not just "not chosen."
