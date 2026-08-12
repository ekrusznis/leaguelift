# Payments: Money Transmission, 1099-K, and Ledger Model Scope

DESIGN-DOC.md §14.6 items #9 (accounting/ledger model review), #10 (1099-K/tax reporting), and #11 (money transmission/fund holding). These three are facets of the same underlying architecture, so covered together.

**This document describes the technical architecture and the generally-understood industry pattern it follows. It is not legal or tax advice, and does not replace the actual accountant/counsel review these compliance items call for** — money-transmitter law in particular varies by state, and 1099-K rules have changed federally in recent years. Where this doc says "likely" or "standard practice," that is a statement about how Stripe Connect platforms are commonly structured, not a legal conclusion Rally26 can rely on without professional confirmation.

## The architecture, precisely

Per ADR-005 ("separate charges and transfers"): a customer's card charge lands in **Rally26's own Stripe balance** first (Rally26 is the merchant of record). Organizations connect a **Stripe Connect Express account** (`StripeConnectClient.createExpressAccount`, `AccountCreateParams.Type.EXPRESS`) — Stripe hosts the onboarding UI and handles identity verification/KYC for that account, not Rally26. Once an organization's earned share is known (gross minus platform fee, minus production cost for orders), Rally26 explicitly creates a Stripe `Transfer` to move funds from its own Stripe balance to the organization's connected Express account (`PayoutAccountService.triggerTransfer` — manual-trigger-only, an OWNER/ADMINISTRATOR must click it; no automatic scheduler exists). At every step, the money itself stays inside Stripe's own ledger/balance system — it is never held in a Rally26-controlled bank account, and never becomes a Rally26 asset outside of Stripe's own regulated rails.

Real numbers as configured today (ADR-017, all explicitly starting/pilot values, not final pricing):
- **Platform fee**: 5% flat (`PlatformFeeProperties.feeBasisPoints = 500`), snapshotted onto every `RALLY26_PLATFORM_FEE` ledger entry so a future rate change never rewrites history.
- **Holding period**: 7 days (`PayoutProperties.holdingPeriodDays`), configurable, gating when an `ORGANIZATION_EARNING` credit becomes eligible for transfer.
- **Negative-balance handling**: deducted from the organization's next eligible payout — a below-cost order, a refund, or a dispute all produce a `DEBIT` entry that reduces what's transferred next, never a separate reversal charge (`LedgerService`, ADR-017).
- **Ledger**: append-only (`ledger_entry`, V15) — corrections are always new reversing rows, never edits. `MoneyArithmeticTest` structurally asserts no method exists that could mutate an existing row's amount/direction/type.

## Money transmission

Several architectural facts point toward this design following the standard, lower-risk "Stripe Connect platform" pattern rather than Rally26 itself acting as a money transmitter:

1. **Funds never leave Stripe's system before reaching the organization.** Rally26's "Stripe balance" is a ledger entry inside Stripe's own regulated infrastructure, not a Rally26 bank account. The 7-day holding period is a delay in *when Rally26 calls the Transfer API*, not custody of funds outside Stripe.
2. **Express accounts put identity verification/KYC on Stripe, not Rally26** — part of the same compliance framework that lets Stripe (which holds money-transmitter licenses/authorizations across US states) extend coverage to transactions flowing through Connect, provided the platform doesn't take on the characteristics of an independent money transmitter itself.
3. **Family credits are deliberately structured to avoid the "stored value" trigger** — the Terms of Service already state credits "are not cash, are not withdrawable, are not presented as a stored-value or bank balance, and are not transferable between unrelated families" (a real, meaningful distinction: a stored-value wallet product that lets users hold and freely spend/cash-out a balance is a classic money-transmitter trigger; a non-withdrawable fee credit is not).
4. **Rally26 never disburses cash or ACH directly to a household or individual** — the only money movement out of Rally26's Stripe balance is a `Transfer` to an organization's own connected Express account, structurally identical to how any Stripe Connect marketplace platform operates.

**What still needs real confirmation, not assumed by this document:**
- Whether Rally26's actual use of Stripe Connect (not a hypothetical "typical" platform) qualifies for whatever exemption or coverage Stripe's current Connected Account/Platform Agreement provides — this depends on Stripe's current terms, which Rally26 accepted when creating its Stripe account, and should be read directly rather than assumed.
- Whether any state Rally26 operates in has money-transmitter licensing thresholds or exemptions that apply differently to this specific fact pattern (fundraising/donation intermediation is sometimes treated differently from ordinary commerce under state law).

## 1099-K reporting

Because organizations are real Stripe Connect **Express** accounts (not Custom accounts, where the platform sometimes takes on more direct tax-reporting responsibility), the standard Stripe Connect practice is that **Stripe itself issues 1099-K forms directly to the connected Express account** once that account's aggregated payment volume crosses the applicable federal (and sometimes state) reporting threshold — not the platform. This is a genuine, meaningful difference between Custom and Express/Standard account types, and is consistent with Rally26's deliberate choice of Express accounts for exactly this kind of reduced compliance burden.

**A real operational risk worth flagging, separate from the legal question:** 1099-K issuance requires a valid taxpayer identification number (EIN or SSN) on file for the connected account. Many youth sports organizations are informal parent-run booster clubs that may not have a formal EIN. If an organization completes Express onboarding without a valid TIN, either Stripe's own compliance checks may restrict that account's ability to receive live payouts, or 1099-K issuance may fail/be inaccurate at year-end — this is worth a real product/onboarding check (does Rally26's onboarding flow surface this requirement clearly to organizations before they expect to receive money?), independent of the underlying legal determination.

**What still needs real confirmation:** whether Rally26 has any residual 1099-K or other tax-reporting obligation beyond what Stripe provides directly to connected accounts (e.g., for the platform fee revenue itself, which is Rally26's own income, not something 1099-K-relevant to organizations).

## Ledger/accounting model — reference for accountant review

Structured summary of what's built, for an accountant to review efficiently without reading the codebase directly:

- **Entry types**: `CONTRIBUTION`/`GROSS_SALE` (gross revenue, credit), `PRODUCTION_COST` (Printify cost snapshot, debit, orders only), `RALLY26_PLATFORM_FEE` (debit, Rally26's revenue), `ORGANIZATION_EARNING` (credit — what the org is owed, net of fee and cost), `TRANSFER` (debit, when money actually moves to the org), `REFUND`/`CHARGEBACK`/`CHARGEBACK_FEE` (reversals — see `docs/DISPUTE-CHARGEBACK-RUNBOOK.md` for the dispute-specific ones), `OFFLINE_SETTLEMENT` (money the org received outside Rally26 — cash/check/Venmo — recorded for tracking but never eligible for a Rally26 transfer), `MANUAL_ADJUSTMENT` (staff-corrected reversals).
- **A below-cost order** (production cost + platform fee exceeding gross) produces a negative organization earning, recorded honestly as a debit rather than hidden — this is picked up immediately in the org's next payout calculation, no holding period.
- **Every entry is organization-scoped, typed, and traceable back to its source record** (`source_type`/`source_id`) — a `select` by source or by organization gives a complete, append-only history.
- **Platform fee rate is snapshotted per entry**, not looked up live — a future rate change is provably non-retroactive.

## Summary of what remains open

| Item | Status |
|---|---|
| Money transmission | Architecture documented 2026-08-12; standard Stripe Connect pattern reasoning laid out; real state-by-state legal confirmation still not done |
| 1099-K | Architecture documented 2026-08-12; Express-account TIN operational risk flagged; real tax-professional confirmation still not done |
| Ledger/accounting model | Structured reference written 2026-08-12 for accountant review; actual accountant review still not done |
