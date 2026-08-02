# ADR-053: Phase 18 payment plans, controlled corrections, and reconciliation

**Status:** Accepted and implemented locally
**Date:** 2026-08-01

## Context

Phase 18 already supplied manual catalog/fulfillment and explicitly offline financial records. The remaining pilot-operational gaps were formal fee installments, a safe correction path for online and offline money, and a durable view of financial/fulfillment inconsistencies. Existing fee payments, source transactions, provider references, and ledger entries are historical evidence and must not be edited or deleted to make an operational screen look correct.

A test also exposed that the offline-verification acknowledgement payload used `Instant` directly with a raw Jackson `ObjectMapper`, making the service unit test depend on a Java-time module that the test intentionally did not install.

## Decision

### Fee payment plans

- A plan belongs to one existing fee assignment and snapshots only the assignment's current outstanding balance and currency.
- One active plan is allowed per assignment.
- A plan contains 2–24 positive installments whose sum must exactly equal the current balance and whose dates are chronological.
- Existing fee payments remain the payment source of truth. New payments allocate FIFO to the earliest unpaid installment through a separate allocation table.
- Voiding a payment preserves the payment/allocation records; progress calculations ignore allocations whose payment is voided.
- Adjustments cannot be added or voided while a plan is active. Managers must cancel and recreate the schedule against the new balance.
- Cancelling a plan never voids or changes a payment.

### Financial corrections

- Every correction requires a preview containing the exact source, maximum/remaining amount, warnings, and a SHA-256 confirmation hash.
- Execution is manager-only and idempotent. A PostgreSQL transaction-scoped advisory lock serializes corrections for the same organization/type/source record, followed by a second idempotency lookup, so concurrent confirmations cannot collectively exceed the remaining correctable amount.
- Confirmed Stripe contributions, sponsorships, and orders can be refunded partially or fully inside the existing 14-day window. The real Stripe refund API receives an idempotency key and amount.
- A partial refund appends correction/ledger history and leaves the source confirmed; a full refund marks the source refunded.
- Verified offline records may only be reversed in full. Reversal marks the offline source lifecycle as reversed and appends opposite-direction ledger entries linked to the correction.
- No correction edits or deletes original source, payment, fulfillment, audit, or ledger rows.

### Reconciliation

- Reconciliation is an explicit manager action that writes a permanent run and fixed issue snapshot.
- The first deterministic checks cover pending offline verification, missing Stripe payment references, confirmed sources without ledger entries, confirmed orders without fulfillment, fulfillment exceptions, overdue installments, and active-plan/current-balance mismatches.
- Reconciliation does not silently repair records. Each issue supplies an existing application route for human review.
- The latest issue count is surfaced in the Action Center.

### Serialization hotfix

The offline acknowledgement outbox payload stores `receivedAt` as an ISO-8601 string. The email handler parses that string back to `Instant` for display. This keeps the wire payload explicit and lets a plain `ObjectMapper` serialize the unit-test payload without requiring test-only mapper configuration.

## Consequences

- Phase 18 is complete locally, but live provider reconciliation, disputes/chargebacks, taxes, automated daily runs, and provider webhook activation remain later integration/launch work.
- Historical financial evidence stays append-only.
- Fee-plan progress always follows actual non-voided fee payments rather than a second payment ledger.
- Help Center articles ship with all three workflows and the cross-phase coverage inventory is updated.
