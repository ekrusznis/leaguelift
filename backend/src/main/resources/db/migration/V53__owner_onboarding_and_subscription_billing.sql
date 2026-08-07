-- Phase 24.6 + founder-requested pull-forward of Phase 26 core:
-- resumable owner onboarding and real Stripe subscription billing.
-- Browser return URLs are never authoritative for organization activation; signed Stripe
-- subscription webhooks are. The seeded Founding Club price is $149.00/month.

alter table organization drop constraint organization_status_check;
alter table organization
    add constraint organization_status_check check (status in ('DRAFT', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'));

create table subscription_plan (
    code               text primary key,
    name               text not null,
    description        text not null,
    amount_minor       bigint,
    currency           text,
    billing_interval   text,
    contact_only       boolean not null default false,
    active             boolean not null default true,
    stripe_product_id  text,
    stripe_price_id    text,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint subscription_plan_amount_check check (amount_minor is null or amount_minor > 0),
    constraint subscription_plan_currency_check check (currency is null or currency ~ '^[A-Z]{3}$'),
    constraint subscription_plan_interval_check check (billing_interval is null or billing_interval in ('MONTHLY')),
    constraint subscription_plan_stripe_product_key unique (stripe_product_id),
    constraint subscription_plan_stripe_price_key unique (stripe_price_id),
    constraint subscription_plan_checkout_shape_check check (
        (contact_only = true and amount_minor is null and currency is null and billing_interval is null)
        or
        (contact_only = false and amount_minor is not null and currency is not null and billing_interval is not null)
    )
);

insert into subscription_plan (code, name, description, amount_minor, currency, billing_interval, contact_only, active)
values
    ('FOUNDING_CLUB', 'Founding Club', 'Rally26 organization subscription.', 14900, 'USD', 'MONTHLY', false, true),
    ('CONTACT_RALLY26', 'Contact Rally26', 'Talk with Rally26 about a custom rollout.', null, null, null, true, true);

create table owner_onboarding (
    id                         uuid primary key default gen_random_uuid(),
    owner_user_id              uuid not null references app_user (id),
    organization_id            uuid references organization (id),
    current_step               text not null default 'ORGANIZATION',
    selected_plan_code         text references subscription_plan (code),
    selected_billing_frequency text,
    terms_accepted_at          timestamptz not null,
    adult_confirmed_at         timestamptz not null,
    checkout_session_id        text,
    checkout_started_at        timestamptz,
    completed_at               timestamptz,
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    constraint owner_onboarding_owner_key unique (owner_user_id),
    constraint owner_onboarding_organization_key unique (organization_id),
    constraint owner_onboarding_checkout_session_key unique (checkout_session_id),
    constraint owner_onboarding_step_check check (current_step in ('ORGANIZATION', 'PLAN', 'REVIEW', 'CHECKOUT', 'COMPLETE')),
    constraint owner_onboarding_billing_frequency_check check (
        selected_billing_frequency is null or selected_billing_frequency in ('MONTHLY')
    )
);

create table organization_subscription (
    id                         uuid primary key default gen_random_uuid(),
    organization_id            uuid not null references organization (id),
    plan_code                  text not null references subscription_plan (code),
    status                     text not null default 'CHECKOUT_PENDING',
    stripe_customer_id         text,
    stripe_subscription_id     text,
    stripe_checkout_session_id text,
    checkout_generation        integer not null default 0,
    last_payment_failure_at    timestamptz,
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    constraint organization_subscription_organization_key unique (organization_id),
    constraint organization_subscription_customer_key unique (stripe_customer_id),
    constraint organization_subscription_subscription_key unique (stripe_subscription_id),
    constraint organization_subscription_checkout_key unique (stripe_checkout_session_id),
    constraint organization_subscription_status_check check (
        status in ('CHECKOUT_PENDING', 'TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'INCOMPLETE')
    ),
    constraint organization_subscription_generation_check check (checkout_generation >= 0)
);

create index owner_onboarding_step_idx on owner_onboarding (current_step);
create index organization_subscription_status_idx on organization_subscription (status);
