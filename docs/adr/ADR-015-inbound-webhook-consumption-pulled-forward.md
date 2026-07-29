# ADR-015: Inbound Webhook Consumption Pulled Forward for Campaign Contributions

## Status
Accepted

## Context

DESIGN-DOC.md section 17 designs a generic `webhook_event` table (provider,
external event id, payload, signature-verified flag, processing status, retry
metadata) but had marked it "not built yet" and scoped to Phase 5, alongside live
charge/transfer execution and the outbox-consumer worker. ADR-005's consequences
section reinforced this: "Phase 5 must add Stripe webhook handling ... before
going live."

Building Phase 3's campaign-contribution checkout (ADR-005's separate-charges-
and-transfers model applied concretely for the first time) forced a concrete
choice for how a Checkout Session's completion gets recorded. The payout
module's existing precedent (Stripe Connect onboarding, Phase 2) uses a
synchronous "refresh on browser return" call — the org admin clicks a button
after finishing Stripe's hosted flow, and the backend fetches current status
from Stripe on that request. Applying the same pattern to a public supporter's
contribution has a real correctness gap: a supporter who completes payment and
closes the tab before the browser redirects back would leave Stripe holding
confirmed money that LeagueLift never records — there is no admin "refresh"
action a supporter would ever click.

## Decision

Build a real, minimal Stripe webhook receiver now (`POST /webhooks/stripe`, the
`webhook` module, and the `webhook_event` table from section 17's design target),
scoped narrowly:

- Only `checkout.session.completed` is consumed, only to confirm campaign
  contributions (`ContributionService.confirmFromWebhook`). Any other event type
  is recorded and marked `IGNORED`, not processed.
- Processing is synchronous, inline in the request handler — no outbox-consumer
  worker was built for this. A genuine processing failure returns HTTP 500 so
  Stripe's own automatic retry schedule covers it; there was no need to build a
  separate retry worker for one lightweight, idempotent event type.
- Idempotency is a `(provider, externalEventId)` unique constraint checked before
  processing, exactly matching section 17's original design-target shape — a
  duplicate delivery is detected and skipped before any business logic runs.
- Stripe Connect account-status webhooks (the thing ADR-005's consequences
  section was actually about) remain untouched and still Phase 5's job. This
  ADR only pulls forward the `checkout.session.completed` -> contribution-
  confirmation path.

## Consequences

- The `webhook_event` table now exists for real, so Phase 5 does not need to
  design it from scratch — it can add Connect-account event types to the same
  table rather than inventing a second mechanism.
- LeagueLift now has one small piece of genuinely asynchronous, provider-
  initiated request handling in production code ahead of the outbox-consumer
  worker (section 17) — reviewers should not assume "no webhook consumption
  exists yet" is still true; it is true only for Connect-account events.
- No live charge/transfer execution, ledger, reconciliation, or refund handling
  was added — this ADR is scoped strictly to confirming a contribution's payment
  status, not to any of Phase 5's remaining launch-gate items (DESIGN-DOC.md
  section 14.4).
- Credit rules/credit events (`credit_rule`, `credit_event`, `credit_application`)
  remain unbuilt — section 19.3's open questions #6/#16/#17 (exact credit
  percentages, cross-season credits, credit expiry) are still unresolved, and
  this ADR does not attempt to answer them.

## Alternatives Considered

- **Sync refresh-on-return only** (mirroring the payout module exactly): rejected
  for the reason in Context — a supporter has no reason to ever trigger a
  "refresh" action, unlike an org admin managing their own payout account, so a
  closed tab would silently and permanently lose the confirmation.
- **Full Phase 5 webhook infrastructure now** (outbox-consumer worker, dead-
  lettering, admin reprocessing UI, Connect-account event types): rejected as
  more than this one event type needs; section 17's own worker requirements
  (claim safely, retry with backoff, dead-letter, admin visibility) matter once
  there are multiple asynchronous consumers competing for outbox rows, which
  isn't the case yet.
