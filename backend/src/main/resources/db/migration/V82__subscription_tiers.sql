-- Real subscription tiers (DESIGN-DOC.md section 19.3 item 12), replacing the
-- single-placeholder-tier pricing page: STARTER ($49/mo, up to 3 teams, no SMS
-- reminders/SportsEngine/TeamSnap/QuickBooks), the existing FOUNDING_CLUB plan
-- renamed for display to "Club" (code unchanged — existing
-- organization_subscription.plan_code = 'FOUNDING_CLUB' rows keep working
-- unmodified), and CONTACT_RALLY26 renamed for display to "League".
-- PlanEntitlementService is the single place these three codes are interpreted;
-- this migration only seeds/renames catalog rows.

insert into subscription_plan (code, name, description, amount_minor, currency, billing_interval, contact_only, active)
values
    ('STARTER', 'Starter', 'For a single team or small club just getting started.', 4900, 'USD', 'MONTHLY', false, true);

update subscription_plan set name = 'Club', description = 'For multi-team clubs — SMS reminders, SportsEngine/TeamSnap, and QuickBooks export included.'
where code = 'FOUNDING_CLUB';

update subscription_plan set name = 'League', description = 'For leagues and large multi-org organizations — talk with Rally26 about a custom rollout.'
where code = 'CONTACT_RALLY26';
