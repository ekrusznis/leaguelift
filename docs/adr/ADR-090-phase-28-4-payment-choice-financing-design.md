# ADR-090: Phase 28.4 Payment Choice and Household Financing Design Contract

**Status:** Accepted
**Date:** 2026-08-09

## Context

Rally26 already has real Stripe-hosted card checkout, immutable financial records, family-credit rules, offline/manual payment recording, corrections/reversals, and reconciliation workflows. Families will eventually need additional legitimate payment choices, but adding provider buttons before defining the accounting, confirmation, refund, privacy, and lender boundaries would create inconsistent financial state and could misrepresent provider availability.

Phase 28.4 is therefore design-only. It defines the contract Phase 31 must satisfy. It adds no provider SDK, credential, connection, database table, checkout button, payment-method setting, or production call.

## Decision

### 1. Payment method is an explicit dimension

Phase 31 must model payment choice independently from the source financial record. A fee payment, order, contribution, or sponsorship keeps its current domain ownership. Payment choice does not collapse those records into one mutable payment table.

A future provider-neutral descriptor should distinguish at least:

- the user-facing method key;
- the provider family;
- whether confirmation is provider-authoritative or staff-verified manual activity;
- platform implementation/readiness;
- organization-specific availability/setup state;
- eligible country/currency/source-record types;
- supported authorization/capture/refund/void/dispute/reconciliation capabilities;
- whether checkout is provider-hosted, SDK-tokenized, or manual/external.

Do not use a single `CONNECTED` or `AVAILABLE` flag to represent all of those dimensions.

### 2. Verified provider paths only

- **Stripe/card** remains the currently implemented hosted card path and existing source of truth for its transactions.
- **Venmo** may be implemented only through an approved PayPal Checkout/partner contract that officially exposes Venmo as a funding source. Current PayPal documentation exposes Venmo through PayPal Checkout for eligible US merchant/buyer flows; Rally26 must not collect a consumer Venmo login or fabricate a direct Venmo rail.
- **Cash App Pay** may be implemented only through Square's official Web Payments SDK/Payments API or another verified merchant contract. Current Square documentation tokenizes Cash App Pay in the Web Payments SDK and submits the token to the Payments API; Rally26 must not collect Cash App credentials.
- **Zelle** remains an external/manual method unless Rally26 obtains a supported financial-institution/partner integration that can authenticate the transfer. Current Zelle small-business guidance routes businesses through participating banks or credit unions and does not provide Rally26 a general merchant confirmation API.
- **Future ACH** may be added only through a verified provider contract and must identify the actual provider rather than presenting a generic bank-transfer claim.
- **Pay-over-time/lender** support such as Affirm is an external lender/payment-provider relationship. Current Affirm merchant APIs support checkout/transaction state plus void/refund operations; Rally26 does not become the lender and must not make underwriting decisions.

Provider availability must be re-verified against current official documentation and commercial eligibility at the start of Phase 31 implementation. This ADR records a design direction, not a promise that any named provider will remain commercially available to Rally26.

### 3. Internal credits and discounts precede external financing

The authoritative order is:

```text
Household amount owed
- eligible Rally26 family credit
- eligible Rally26 discounts / adjustments
= remaining Rally26 balance

remaining Rally26 balance
-> maximum amount eligible for external financing
```

The lender never receives the pre-credit gross balance when Rally26 credit is eligible and actually being applied to that transaction.

Phase 31 must distinguish a **provisional quote/reservation** from final credit consumption. Starting or abandoning an external checkout must not irreversibly consume family credit. If concurrent checkout can double-spend available credit, Phase 31 must introduce a bounded reservation/expiry mechanism rather than mutating existing immutable history or pretending an unconfirmed provider payment succeeded.

Once a lender authorization/transaction creates an external obligation, Rally26 credit cannot later reduce that lender balance. A later cancellation, reduction, or refund must use the lender/provider-supported void/refund/adjustment path and then append the corresponding Rally26 correction/reconciliation evidence.

### 4. Client intent never marks a balance paid

A click, redirect return, tokenization event, pasted reference, screenshot, or user-entered confirmation value is not payment confirmation.

A balance changes to paid only after one of these authoritative events:

1. verified provider confirmation/capture/webhook/readback according to that provider's contract; or
2. authorized owner/administrator verification of a real manual/offline payment using the existing controlled financial workflow.

Manual Zelle or similar external transfers therefore remain pending outside Rally26 until staff verifies and records them.

### 5. Future provider transaction seam

Phase 31 may add a provider-neutral attempt/reference layer, but it must preserve these invariants:

- globally unique internal identifier plus organization/source scoping;
- provider + method explicitly recorded;
- immutable request amount/currency snapshot;
- provider environment isolated between sandbox/test/live;
- provider order/payment/transaction/reference IDs never collide across provider families;
- idempotency keys scoped to the logical operation;
- raw credentials/tokens never stored in ordinary Settings, audit metadata, or logs;
- provider state transitions append or reconcile; they do not rewrite the original financial source history;
- retries reuse provider idempotency semantics when supported;
- webhook events are verified, deduplicated, and correlated to the internal source;
- reconciliation can identify missing, duplicate, stale, or contradictory provider state.

### 6. Refund, void, dispute, and reconciliation boundary

Provider-backed payments must implement their actual provider lifecycle before activation. At minimum Phase 31 must define and test:

- authorization/capture behavior where applicable;
- abandoned/declined checkout behavior;
- full and partial refund support where the provider allows it;
- void/cancellation behavior;
- settlement/fee records required for reconciliation;
- disputes/chargebacks when the provider supports them;
- webhook/readback recovery after missed callbacks;
- immutable Rally26 correction/ledger entries for every provider-side reversal.

A provider that cannot satisfy Rally26's reconciliation and correction requirements remains unavailable even if its checkout UI can technically render.

### 7. Organization settings boundary

Future organization Settings may expose only methods that are implemented and verified for Rally26. The organization may then enable/disable an eligible method and supply only the non-secret configuration the method genuinely requires.

Examples:

- manual/external Zelle instructions may contain an owner-verified business destination/instruction, never a consumer credential;
- PayPal/Square/Affirm secrets belong in controlled provider/runtime or encrypted connection storage, not ordinary organization settings;
- a method cannot show `AVAILABLE` merely because a logo or frontend component exists;
- Platform Admin support access does not grant unrestricted credential viewing or mutation.

### 8. Financing privacy and product boundary

Rally26 does not:

- underwrite the household;
- decide lender eligibility;
- store credit score, income, adverse-action reason, or lender login credentials by default;
- represent family credit as cash, collateral, or a wallet balance;
- promise approval, rate, term, or lender availability;
- service or collect the lender's receivable.

Prefer provider-hosted eligibility/checkout. Persist only the minimum provider transaction identifiers/status required for fulfillment, reconciliation, refunds, support, and legal/accounting evidence.

## Phase 31 activation gates

A payment method cannot be enabled for pilot/production until all applicable gates pass:

1. official provider/business contract and commercial eligibility verified;
2. provider sandbox/test flow verified with real documented request/response fixtures;
3. credentials isolated and rotated through the approved secret/connection model;
4. provider-authoritative confirmation path implemented;
5. webhook/signature/readback/idempotency behavior tested;
6. refund/void/correction behavior tested;
7. reconciliation and missing-event recovery tested;
8. cross-organization and environment isolation tested;
9. privacy/data-minimization review completed;
10. accounting/tax/settlement treatment reviewed;
11. lender-specific disclosures, support responsibility, amount limits, cancellation, and delinquency boundaries reviewed when financing is involved;
12. Help Center and user-facing availability language accurately describe the method.

## Consequences

- Phase 28 adds no payment provider code or schema for this work.
- Phase 31 can implement one provider at a time without changing the financial meaning of existing fee/order/contribution/sponsorship records.
- Family credit/discount is resolved before financing and cannot retroactively pay a lender obligation.
- Manual external methods remain legitimate but explicitly staff-verified, rather than being misrepresented as API-connected.
- Provider implementation status, organization setup state, and user-facing availability remain separate concepts.

## Current official-provider verification used for this design

Verified on 2026-08-09 against current official documentation:

- PayPal Developer: Pay with Venmo / PayPal Checkout documentation.
- Square Developer: Web Payments SDK Cash App Pay and Payments API flow.
- Zelle small-business guidance: business availability is through participating financial institutions.
- Affirm Developer: merchant checkout/Transactions API, including read, void, and refund capabilities.

These references must be rechecked at Phase 31 implementation time because provider contracts and capabilities can change.
