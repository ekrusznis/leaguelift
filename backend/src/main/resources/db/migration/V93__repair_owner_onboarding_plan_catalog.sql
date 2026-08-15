-- P0 pilot-readiness repair: owner onboarding must always expose the three
-- currently-supported catalog choices. V82 inserted STARTER but only renamed the
-- two V53 seed rows; an environment missing either historical seed could therefore
-- return an incomplete /owner-onboarding/plans response.
--
-- Upsert by stable plan code and deliberately leave stripe_product_id / stripe_price_id
-- untouched on conflict so already-provisioned Stripe mappings are preserved.

insert into subscription_plan (
    code,
    name,
    description,
    amount_minor,
    currency,
    billing_interval,
    contact_only,
    active
)
values
    (
        'STARTER',
        'Starter',
        'For a single team or small club just getting started.',
        4900,
        'USD',
        'MONTHLY',
        false,
        true
    ),
    (
        'FOUNDING_CLUB',
        'Club',
        'For multi-team clubs — SMS reminders, SportsEngine/TeamSnap, and QuickBooks export included.',
        14900,
        'USD',
        'MONTHLY',
        false,
        true
    ),
    (
        'CONTACT_RALLY26',
        'League',
        'For leagues and large multi-org organizations — talk with Rally26 about a custom rollout.',
        null,
        null,
        null,
        true,
        true
    )
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    amount_minor = excluded.amount_minor,
    currency = excluded.currency,
    billing_interval = excluded.billing_interval,
    contact_only = excluded.contact_only,
    active = excluded.active,
    updated_at = now();
