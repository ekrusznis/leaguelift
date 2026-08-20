-- Phase 45/46 (DESIGN-DOC.md §14.1T/§14.1U): a real FREE subscription tier,
-- self-serve with no Stripe Checkout required, plus the schema support for
-- real in-app upgrade/downgrade (today every plan change goes through the
-- Stripe-hosted Billing Portal only).
--
-- requires_checkout is the stable, non-price identifier for "does this plan
-- need Stripe Checkout" — Phase 45 explicitly forbids gating on price
-- literals, and amount_minor = 0 alone can't distinguish "free bypass plan"
-- from a future genuinely-free-but-still-Stripe-tracked promotion.

alter table subscription_plan
    add column requires_checkout boolean not null default true;

-- Must land before the new checkout_shape_check constraint below — CONTACT_RALLY26
-- is contact_only and needs requires_checkout = false to satisfy that shape.
update subscription_plan set requires_checkout = false where code = 'CONTACT_RALLY26';

alter table subscription_plan drop constraint subscription_plan_amount_check;
alter table subscription_plan
    add constraint subscription_plan_amount_check check (amount_minor is null or amount_minor >= 0);

alter table subscription_plan drop constraint subscription_plan_checkout_shape_check;
alter table subscription_plan
    add constraint subscription_plan_checkout_shape_check check (
        (contact_only = true and requires_checkout = false and amount_minor is null and currency is null and billing_interval is null)
        or
        (contact_only = false and requires_checkout = true and amount_minor is not null and amount_minor > 0 and currency is not null and billing_interval is not null)
        or
        (contact_only = false and requires_checkout = false and amount_minor = 0 and currency is not null and billing_interval is null)
    );

insert into subscription_plan (code, name, description, amount_minor, currency, billing_interval, contact_only, requires_checkout, active)
values
    ('FREE', 'Free', 'For a single team trying Rally26 for the first time.', 0, 'USD', null, false, false, true)
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    amount_minor = excluded.amount_minor,
    currency = excluded.currency,
    billing_interval = excluded.billing_interval,
    contact_only = excluded.contact_only,
    requires_checkout = excluded.requires_checkout,
    active = excluded.active,
    updated_at = now();

-- Idempotency-key suffix for the new Stripe subscription update/cancel calls,
-- mirroring the existing checkout_generation convention.
alter table organization_subscription
    add column plan_change_generation integer not null default 0,
    add constraint organization_subscription_plan_change_generation_check check (plan_change_generation >= 0);

-- Marks "this subscription is scheduled to cancel at period end because the
-- owner chose to downgrade to this plan" — distinct from a genuine full
-- cancellation initiated through the Stripe Billing Portal. handleSubscriptionChanged
-- must react differently to the two cases: a plan-change-driven cancellation
-- completes as a downgrade (org stays ACTIVE on the new plan), while a real
-- cancellation suspends the organization as it does today.
alter table organization_subscription
    add column downgrade_to_plan_code text references subscription_plan (code),
    add column current_period_end timestamptz;
