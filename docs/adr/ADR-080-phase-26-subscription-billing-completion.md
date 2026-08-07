# ADR-080 — Phase 26 Organization Subscription Billing Completion

**Status:** Accepted / implementation complete pending provider-backed live verification  
**Date:** 2026-08-07  
**Migration:** V61

## Context

Phase 24.6 (ADR-072, V53) intentionally pulled forward the core Phase 26 Stripe Billing boundary: backend-owned plans, one Rally26 subscription record per organization, idempotent Stripe Product/Price/Customer/Checkout creation, Billing Portal session creation, and signed subscription-webhook authority for organization activation/suspension.

Phase 26 therefore does not create a second billing implementation. It completes the operating surfaces and recovery visibility around that existing core.

The founder explicitly deferred provider-backed live Stripe verification for this pass to the IntelliJ AI agent running in the normal repository environment with the existing Stripe test credentials. This commit must instead be fully testable without external provider calls.

## Decision

### 1. Stripe remains authoritative

Browser Checkout or Billing Portal returns never activate, suspend, cancel, or recover an organization by themselves. `customer.subscription.*` webhooks remain authoritative for durable subscription status and organization access.

`invoice.payment_failed` records payment failure and `PAST_DUE` without inventing a lockout/grace-period rule. `customer.subscription.updated` also carries Stripe's `cancel_at_period_end` flag so a still-active subscription can be shown as scheduled to cancel without prematurely suspending access. `invoice.paid` and `invoice.payment_succeeded` now record a durable `last_payment_success_at` timestamp, but do not independently change subscription status; the subscription webhook remains authoritative.

### 2. Billing Portal owns owner billing mutations

Rally26 does not add a parallel cancellation/payment-method implementation. The owner Billing page opens Stripe Billing Portal for payment-method changes, invoices, and cancellation. This keeps sensitive billing changes in Stripe while Rally26 consumes the resulting signed webhook state.

### 3. Owner billing page

A new `/app/organizations/:organizationId/billing` route shows:

- backend plan name and price,
- durable subscription status,
- a derived recovery state,
- last payment failure/success timestamps,
- a clear Stripe Billing Portal recovery/manage action.

The route is surfaced through the existing `organization.billing.manage` capability.

### 4. Platform Admin visibility is read-only

A new `/api/v1/platform/admin/subscriptions` endpoint and `/app/platform/subscriptions` page provide sanitized, paginated support visibility across all organizations, including organizations that have not started subscription checkout.

The view exposes organization/plan/status, derived recovery state, payment timestamps, and booleans indicating whether Stripe customer/subscription identifiers are linked. It does not expose secret credentials or raw Stripe objects and adds no Platform Admin mutation path.

### 5. Recovery states are derived, not a second lifecycle

`BillingRecoveryState` is a pure projection over the existing durable subscription status:

- `CURRENT`: `TRIALING`, `ACTIVE`
- `PAYMENT_ACTION_REQUIRED`: `PAST_DUE`
- `ENDED`: `CANCELED`
- `CHECKOUT_REQUIRED`: `CHECKOUT_PENDING`, `INCOMPLETE`

No second status column is persisted.

## Testing

This slice adds:

- domain unit tests for Stripe status/access/recovery-state mapping,
- a real-Postgres `OrganizationSubscriptionRepositoryIntegrationTest` covering V61 payment timestamps and Platform Admin queries,
- a signed-webhook transport unit test proving `invoice.paid` dispatches to organization billing and records a processed webhook event.

Provider-backed live verification is deliberately deferred to the founder's normal IntelliJ environment. The live pass should use the existing Stripe test keys and verify Checkout, Billing Portal, `invoice.payment_failed`, `invoice.paid`, `customer.subscription.updated`, and `customer.subscription.deleted` delivery end to end.

## Consequences

- Phase 26 is code-complete without duplicating the V53 Stripe core.
- Cancellation/payment recovery UX is available through Stripe Billing Portal.
- Platform support can find billing/onboarding state without customer impersonation or provider-secret exposure.
- A successful invoice is observable even before a later subscription status event arrives, while access remains webhook-authoritative.
