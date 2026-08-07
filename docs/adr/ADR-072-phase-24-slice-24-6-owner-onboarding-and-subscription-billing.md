# ADR-072: Phase 24.6 Owner Onboarding and Pulled-Forward Subscription Billing

## Status

Accepted — 2026-08-07

## Context

Phase 24.6 requires Rally26 to replace the single owner-registration form with a durable, resumable Account -> Organization -> Plan -> Review/Checkout workflow. The design originally left the actual Stripe Billing activation lifecycle to Phase 26, but the founder explicitly pulled the live provider-backed Stripe creation path into this implementation slice and requested testing with the project's existing Stripe test credentials.

The existing code already has a shared `StripeClient`, signed Stripe webhook receiver, webhook-event deduplication, and test/live configuration through `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`. It did not have a subscription plan catalog, Stripe Customer/Product/Price ownership for the platform subscription, durable onboarding progress, or an organization state that was safe to expose before subscription activation.

## Decision

### Durable onboarding

A normal owner self-registration creates exactly one `owner_onboarding` row. Invitation-based registrations do not create owner onboarding; they keep the existing invitation activation path.

The account step remains `/auth/register`. After verification/sign-in, the remaining wizard uses distinct protected routes:

- `/app/onboarding/organization`
- `/app/onboarding/plan`
- `/app/onboarding/review`

Backend state, not local browser state, determines the resume point. `owner_user_id` and `organization_id` are unique in `owner_onboarding`, so refreshes, repeat sign-ins, and retries cannot create duplicate onboarding records or duplicate draft organizations.

### Draft organizations are not normal tenant workspaces

`OrganizationStatus.DRAFT` is added. The organization step creates one DRAFT organization and grants its owner membership, but DRAFT organizations are excluded from normal organization listings and blocked from ordinary membership/capability-based organization access. The onboarding service is the controlled write path while the organization remains DRAFT. Platform Admin support access remains exempt from this tenant boundary.

The Phase 24.5 timezone model is reused: address-derived timezone is only a suggestion and the owner explicitly confirms an IANA timezone before advancing.

### Backend-owned plan catalog

`subscription_plan` is the authoritative application catalog. The first self-service plan is:

- `FOUNDING_CLUB` — USD 149.00/month

`CONTACT_RALLY26` is also represented as a contact-only card and cannot enter self-service Checkout. The frontend renders backend-driven plan cards rather than hardcoding Stripe Price IDs or using a dropdown as the primary comparison UI.

### Real Stripe subscription creation

The existing configured `StripeClient` is reused. On first Checkout for a self-service plan, Rally26 creates/reuses:

1. a Stripe Product for the Rally26 plan,
2. a recurring monthly Stripe Price,
3. one Stripe Customer for the organization,
4. a Stripe Checkout Session in `subscription` mode.

Stripe object IDs are persisted only server-side. Creation calls use deterministic Stripe idempotency keys, and the backend also serializes creation with database row locks. An existing open Checkout Session is reused when possible instead of creating another session.

The implementation works with whichever Stripe account the configured secret key belongs to. For verification, use the project's existing **test-mode** secret key. Swapping a database that already contains test-mode Stripe object IDs directly to a live-mode key is **not** a safe cutover; Product/Price/Customer IDs are account/mode-specific and require a controlled live-mode migration/recreation runbook before launch.

### Webhooks are authoritative

The Checkout success URL is informational only. It never activates the organization.

The existing signed `/api/v1/webhooks/stripe` receiver is extended with:

- `checkout.session.completed` for subscription identifier linking,
- `customer.subscription.created`,
- `customer.subscription.updated`,
- `customer.subscription.deleted`,
- `invoice.payment_failed`.

`customer.subscription.*` is authoritative for subscription state. A Checkout-completed event only links provider IDs and preserves the current local status, because Stripe does not guarantee cross-event delivery order; a late Checkout event therefore cannot regress an already ACTIVE subscription to CHECKOUT_PENDING.

Local states are `CHECKOUT_PENDING`, `TRIALING`, `ACTIVE`, `PAST_DUE`, `CANCELED`, and `INCOMPLETE`. `TRIALING`/`ACTIVE` promote the organization to ACTIVE and complete onboarding. Definitive `CANCELED`/`INCOMPLETE` transitions suspend an already-active organization. `PAST_DUE` is recorded but does not immediately suspend access because the founder has not selected a grace-period/lockout policy; the owner can use the Stripe Billing Portal to recover payment.

A late `invoice.payment_failed` cannot overwrite a terminal `CANCELED`/`INCOMPLETE` local state.

### Billing management

The organization owner can create a Stripe Billing Portal session once a Customer exists. The portal is the supported path for payment-method management and recovery of an existing subscription; the application refuses to create a second Checkout subscription while the current local subscription is ACTIVE, TRIALING, or PAST_DUE.

## Consequences

- Owner onboarding now survives refresh and later sign-in without duplicate users, organizations, or local subscriptions.
- A newly created organization is not available as a normal tenant workspace before Stripe confirms the subscription.
- Stripe Product/Price/Customer/Checkout creation is real provider-backed behavior, not a future adapter seam.
- Browser redirects cannot forge organization activation.
- Existing Stripe contribution/order/sponsorship webhook behavior remains intact; subscription routing is identified by `organizationSubscriptionId` metadata first.
- Phase 26 is now partially pulled forward. Remaining Phase 26 work still includes any final grace/suspension policy, richer Platform Admin billing visibility, live-mode cutover/rehearsal, and any later plan/tier expansion justified by sales evidence.
- Phase 24.7 Multimedia Help remains the next unfinished Phase 24 slice.

## Alternatives Considered

### Activate on Checkout return URL

Rejected. The browser can abandon, replay, or forge a return URL, and Stripe's authoritative state may arrive later.

### Create the organization as ACTIVE before payment

Rejected. An owner membership on an ACTIVE organization would allow normal tenant use without a confirmed subscription.

### Store Stripe Price IDs in frontend configuration

Rejected. Pricing and provider identifiers are server-owned and must be changeable without a frontend deployment.

### Immediately suspend on the first failed renewal

Rejected for now. The product design has not selected a grace-period policy. Recording PAST_DUE while keeping billing recovery available is the narrowest non-invented behavior.
