# ADR-051: Phase 18 slices 1-2 — manual catalog and fulfillment operations

**Status:** Accepted and implemented locally

**Date:** 2026-08-01

## Context

The Phase 4 commerce proof assumed every product and fulfillment originated in Printify. That is too narrow for pilot organizations that already work with local printers, fulfill merchandise themselves, or need LeagueLift staff to resolve an order exception manually. Fabricating Printify identifiers for those records would make provider health, reconciliation, and later migration unreliable. Editing an original paid order or ledger entry to represent an operational correction would also violate LeagueLift's append-only financial boundary.

## Decision

Phase 18 begins with two bounded slices.

### 18.1 Manual catalog and vendor management

- Add organization-owned `manual_vendor` records with active/archive lifecycle and optional adult business contact details.
- Give products and variants an explicit `catalog_source`: `PRINTIFY` or `MANUAL`.
- Existing records migrate to `PRINTIFY`.
- Printify products retain real blueprint/image/provider/variant identifiers and provider-returned cost snapshots.
- Manual products leave every Printify identifier null and may reference one active manual vendor.
- Manual variants store optional SKU, size, and color plus administrator-entered currency, vendor cost, and sale price.
- Price may not be below recorded cost. Order items continue snapshotting both price and cost at checkout, so later catalog edits never rewrite transaction history.
- A checkout may not mix Printify and manual items. One manual order may not span multiple manual vendors. This preserves one deterministic fulfillment source per order without inventing an order-splitting model.

### 18.2 Manual fulfillment, tracking, exceptions, and reprints

- Every fulfillment has an explicit `source` and a controlled operational status.
- Paid manual-product orders create a `MANUAL` fulfillment in `READY`; they do not call Printify.
- Paid Printify orders retain the existing draft-order behavior and remain confirmed even if provider submission fails.
- Managers may record vendor references, carrier/tracking data, internal notes, and a required reason for `NEEDS_ATTENTION`.
- Status transitions are allow-listed. Terminal delivered/canceled records cannot be silently reopened.
- Status changes append `fulfillment_history`; the original order, payment confirmation, order-item cost snapshots, and ledger entries are not modified.
- Reprints/replacements use a dedicated durable record with its own lifecycle. Only one open reprint is allowed per fulfillment. A reprint never overwrites or duplicates the original financial transaction.
- Fulfillment failures and attention states appear in the existing Action Center and deep-link to the Stores workspace.

## Authorization and privacy

Active organization members may read the catalog records already available to their organization. Order lists and fulfillment operations expose supporter contact details, vendor references, tracking data, internal notes, history, and reprints, so those reads and every product, vendor, fulfillment, and reprint mutation require the existing organization manager boundary (`OWNER` or `ADMINISTRATOR`). Manual vendor contacts are operational business contacts, not public storefront content. No youth data is added.

## Consequences

- Pilot organizations can operate stores without pretending a local vendor is Printify.
- Provider-backed and manual records remain distinguishable for later reconciliation and integration work.
- Mixed-source carts are rejected rather than creating a speculative multi-fulfillment/order-splitting system.
- This slice does not add offline orders, contributions, sponsorship purchases, payment plans, financial void/reversal workflows, or reconciliation dashboards; those remain later Phase 18 slices.
- This slice does not send a Printify order to production or claim shipment-webhook synchronization. Credentialed provider activation and verification remain Phase 20.
