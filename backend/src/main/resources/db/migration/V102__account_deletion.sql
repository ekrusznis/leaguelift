-- Self-service account deletion (App Store Guideline 5.1.1(v) — an app that supports
-- account creation must also support in-app account deletion). A user requests
-- deletion, gets a 7-day cancelable grace period, then a daily scanner finalizes it —
-- see V101's sibling ownership-transfer work for the "an Owner needs an escape hatch
-- first" half of this feature.
--
-- Finalization never hard-deletes the app_user row (confirmed this session: ~70 tables
-- reference app_user.id and only 4 use "on delete cascade") — it anonymizes in place,
-- the same pattern V64's account-merge feature already established for a retired
-- source identity, just with its own distinct terminal status rather than reusing
-- SUSPENDED (there is no surviving target user for a deletion).
alter table app_user
    drop constraint app_user_status_check,
    add constraint app_user_status_check check (status in ('ACTIVE', 'SUSPENDED', 'PENDING_EMAIL_VERIFICATION', 'DELETED'));

create table account_deletion_request (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null references app_user (id),
    status         text not null default 'PENDING' check (status in ('PENDING', 'CANCELED', 'COMPLETED')),
    requested_at   timestamptz not null default now(),
    scheduled_for  timestamptz not null,
    canceled_at    timestamptz,
    completed_at   timestamptz,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- Only one pending deletion request per user at a time.
create unique index account_deletion_request_one_pending_per_user
    on account_deletion_request (user_id)
    where status = 'PENDING';

create index account_deletion_request_scheduled_idx
    on account_deletion_request (scheduled_for)
    where status = 'PENDING';
